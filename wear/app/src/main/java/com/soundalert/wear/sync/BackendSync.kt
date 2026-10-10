package com.soundalert.wear.sync

import android.util.Log
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.api.ApiResult
import com.soundalert.wear.api.DetectionUpload
import com.soundalert.wear.api.SoundAlertApi
import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Datos con los que el reloj se registra la primera vez (POST /devices). */
data class DeviceRegistration(val name: String, val context: SoundAlertContext, val minConfidence: Float)

enum class SyncState { IDLE, SYNCING, WAITING_RETRY, DISABLED }

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val deviceId: String? = null,
    val pending: Int = 0,
    /** Detecciones locales que aún no están en el backend (el historial las muestra igualmente). */
    val pendingDetectionIds: Set<String> = emptySet(),
    val lastSyncAtMs: Long? = null,
    val lastError: String? = null,
)

/**
 * Reloj → Railway → Supabase. SECUNDARIO a la detección local:
 *
 * - YAMNet, reglas, alertas y vibración no dependen de esto ni lo esperan. Solo se
 *   OBSERVA lo que ya pasó (DetectionHistory, AlertManager, ContextManager) y se
 *   encola; el envío ocurre después, en otro hilo.
 * - La cola se guarda en disco ([SyncStore]): sin internet, o si Android mata la
 *   app, nada se pierde y se envía cuando vuelve la conexión.
 * - El reloj se registra una sola vez (POST /devices) y reutiliza el id guardado
 *   ([DeviceStore]); lo comprueba con GET /devices/:id una vez por proceso.
 * - El backend solo REGISTRA: la decisión de alertar ya la tomó el reloj. Por eso se
 *   registra con una confianza mínima igual al umbral más bajo del reloj, para que
 *   el backend no descarte por LOW_CONFIDENCE algo que el reloj ya confirmó.
 *
 * Errores: red caída, 5xx, 401/403/408/429 → se reintenta con espera creciente.
 * Otros 4xx → la operación no es válida y se descarta (sin bloquear la cola).
 * 404 del dispositivo → se registra de nuevo y se reintenta.
 */
class BackendSync(
    private val api: SoundAlertApi,
    private val devices: DeviceStore,
    private val store: SyncStore,
    private val classifier: ClassifierInfo,
    private val registration: () -> DeviceRegistration,
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val queue = ArrayDeque<SyncOperation>()
    private val remoteAlertIds = LinkedHashMap<String, String>()
    private val seenDetections = LinkedHashSet<String>()
    private val acknowledgedAlerts = LinkedHashSet<String>()
    private val started = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var retryJob: Job? = null
    private var failures = 0
    @Volatile private var state = SyncState.IDLE

    /** Ya comprobado con GET /devices/:id en este proceso. */
    @Volatile private var deviceVerified = false

    /**
     * Interruptor general. Los tests instrumentados lo apagan para no escribir datos
     * de prueba en Supabase. Apagado no se encola nada.
     */
    @Volatile var enabled: Boolean = true
        set(value) {
            field = value
            publish()
            if (value) requestSync()
        }

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    init {
        val saved = store.load()
        synchronized(lock) {
            queue.addAll(saved.queue)
            remoteAlertIds.putAll(saved.remoteAlertIds)
            saved.queue.filterIsInstance<SyncOperation.UploadDetection>().forEach { seenDetections += it.detectionId }
        }
        publish()
    }

    /**
     * Empieza a observar el estado local y a enviar la cola. Idempotente: llamarlo
     * otra vez (p. ej. al recrearse la Activity) no duplica nada.
     */
    fun start(
        detections: Flow<List<ContextualDetection>>,
        alerts: Flow<List<Alert>>,
        context: StateFlow<SoundAlertContext>,
    ) {
        if (!started.compareAndSet(false, true)) return
        scope.launch { detections.collect(::onDetections) }
        scope.launch { alerts.collect(::onAlerts) }
        // Incluye el contexto actual al arrancar: el backend queda igual que el reloj.
        scope.launch { context.collect { enqueue(SyncOperation.SetContext(it)) } }
        scope.launch { for (signal in wake) drain() }
        requestSync()
    }

    /** Intenta enviar ya (p. ej. al volver la conexión), sin esperar al reintento. */
    fun requestSync() {
        wake.trySend(Unit)
    }

    /** Detecciones nuevas (la lista llega más reciente primero; se encolan en orden). */
    internal fun onDetections(list: List<ContextualDetection>) {
        list.asReversed().forEach { detection ->
            val isNew = synchronized(lock) { seenDetections.add(detection.id).also { trim(seenDetections) } }
            if (isNew) {
                enqueue(
                    SyncOperation.UploadDetection(
                        detectionId = detection.id,
                        category = detection.category,
                        confidence = detection.confidence,
                        context = detection.context,
                        detectedAtMs = detection.detectedAtMs,
                    ),
                )
            }
        }
    }

    /** Solo la confirmación explícita (ACKNOWLEDGED) se envía; EXPIRED no existe en el backend. */
    internal fun onAlerts(list: List<Alert>) {
        list.asReversed().filter { it.status == AlertStatus.ACKNOWLEDGED }.forEach { alert ->
            val isNew = synchronized(lock) { acknowledgedAlerts.add(alert.id).also { trim(acknowledgedAlerts) } }
            if (isNew) enqueue(SyncOperation.AcknowledgeAlert(alert.detectionId))
        }
    }

    internal fun enqueue(operation: SyncOperation) {
        if (!enabled) return
        synchronized(lock) {
            if (operation is SyncOperation.SetContext) queue.removeAll { it is SyncOperation.SetContext }
            queue.addLast(operation)
            while (queue.size > MAX_QUEUE) {
                val oldest = queue.firstOrNull { it is SyncOperation.UploadDetection } ?: queue.first()
                queue.remove(oldest)
                Log.w(TAG, "Cola llena ($MAX_QUEUE): se descarta la operación más antigua $oldest")
            }
            persist()
        }
        publish()
        requestSync()
    }

    /** Envía la cola en orden hasta vaciarla o hasta el primer error recuperable. */
    internal suspend fun drain() {
        retryJob?.cancel()
        while (enabled) {
            val operation = synchronized(lock) { queue.firstOrNull() } ?: break
            publish(SyncState.SYNCING)
            val deviceId = when (val device = ensureDevice()) {
                is Step.Ready -> device.deviceId
                is Step.Retry -> return scheduleRetry(device.reason)
            }
            when (val result = execute(deviceId, operation)) {
                Outcome.Done -> complete(operation, error = null)
                is Outcome.Drop -> {
                    Log.w(TAG, "Operación descartada (${result.reason}): $operation")
                    complete(operation, error = result.reason)
                }
                Outcome.DeviceMissing -> {
                    Log.w(TAG, "El backend ya no tiene el dispositivo $deviceId: se registra de nuevo")
                    devices.clear()
                    deviceVerified = false
                }
                is Outcome.Retry -> return scheduleRetry(result.reason)
            }
        }
        failures = 0
        publish(SyncState.IDLE)
    }

    private sealed interface Step {
        data class Ready(val deviceId: String) : Step
        data class Retry(val reason: String) : Step
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data object DeviceMissing : Outcome
        data class Drop(val reason: String) : Outcome
        data class Retry(val reason: String) : Outcome
    }

    private fun ensureDevice(): Step {
        val stored = devices.load()
        if (stored != null) {
            if (deviceVerified) return Step.Ready(stored)
            return when (val result = api.getDevice(stored)) {
                is ApiResult.Ok -> {
                    deviceVerified = true
                    Step.Ready(stored)
                }
                is ApiResult.HttpError -> if (result.code == 404 || result.code == 400) {
                    Log.w(TAG, "Dispositivo guardado $stored no encontrado (${result.code}): se registra de nuevo")
                    devices.clear()
                    register()
                } else {
                    Step.Retry("GET /devices: ${result.code} ${result.message}")
                }
                is ApiResult.NetworkError -> Step.Retry(result.message)
                is ApiResult.InvalidResponse -> Step.Retry(result.message)
            }
        }
        return register()
    }

    private fun register(): Step {
        val data = registration()
        return when (val result = api.registerDevice(data.name, data.context, data.minConfidence)) {
            is ApiResult.Ok -> {
                devices.save(result.value.id)
                deviceVerified = true
                Log.i(TAG, "Reloj registrado en el backend: id=${result.value.id}")
                Step.Ready(result.value.id)
            }
            is ApiResult.HttpError -> Step.Retry("POST /devices: ${result.code} ${result.message}")
            is ApiResult.NetworkError -> Step.Retry(result.message)
            is ApiResult.InvalidResponse -> Step.Retry(result.message)
        }
    }

    private fun execute(deviceId: String, operation: SyncOperation): Outcome = when (operation) {
        is SyncOperation.SetContext -> api.setContext(deviceId, operation.context).toOutcome { Outcome.Done }
        is SyncOperation.UploadDetection -> {
            val upload = DetectionUpload(operation.category, operation.confidence, operation.context, classifier)
            api.uploadDetection(deviceId, upload).toOutcome { uploaded ->
                uploaded.alertId?.let { alertId ->
                    synchronized(lock) {
                        remoteAlertIds[operation.detectionId] = alertId
                        while (remoteAlertIds.size > MAX_ALERT_IDS) remoteAlertIds.remove(remoteAlertIds.keys.first())
                    }
                }
                Log.i(TAG, "Detección ${operation.category} (${operation.context}) registrada: ${uploaded.outcome}")
                Outcome.Done
            }
        }
        is SyncOperation.AcknowledgeAlert -> {
            val alertId = synchronized(lock) { remoteAlertIds[operation.detectionId] }
            if (alertId == null) {
                // El backend no creó alerta para esa detección (p. ej. su propio cooldown).
                Outcome.Drop("sin alerta en el backend para la detección ${operation.detectionId}")
            } else {
                val result = api.acknowledgeAlert(deviceId, alertId)
                if (result is ApiResult.HttpError && result.code == 404) {
                    Outcome.Drop("la alerta $alertId ya no existe")
                } else {
                    result.toOutcome { Outcome.Done }
                }
            }
        }
    }

    private inline fun <T> ApiResult<T>.toOutcome(onOk: (T) -> Outcome): Outcome = when (this) {
        is ApiResult.Ok -> onOk(value)
        is ApiResult.HttpError -> when {
            code == 404 -> Outcome.DeviceMissing
            code in RETRYABLE_CODES || code >= 500 -> Outcome.Retry("$code $message")
            else -> Outcome.Drop("$code $message")
        }
        is ApiResult.NetworkError -> Outcome.Retry(message)
        is ApiResult.InvalidResponse -> Outcome.Drop(message)
    }

    private fun complete(operation: SyncOperation, error: String?) {
        synchronized(lock) {
            queue.remove(operation)
            persist()
        }
        failures = 0
        _status.update { it.copy(lastSyncAtMs = clock(), lastError = error) }
        publish()
    }

    private fun scheduleRetry(reason: String) {
        val waitMs = retryDelaysMs[minOf(failures, retryDelaysMs.lastIndex)]
        failures++
        Log.w(TAG, "Sincronización pendiente (${status.value.pending}): $reason. Reintento en ${waitMs / 1000} s")
        _status.update { it.copy(lastError = reason) }
        publish(SyncState.WAITING_RETRY)
        retryJob = scope.launch {
            delay(waitMs)
            requestSync()
        }
    }

    private fun persist() = store.save(SyncSnapshot(queue.toList(), remoteAlertIds.toMap()))

    /** Publica el estado; [newState] sustituye al anterior (DISABLED manda si está apagado). */
    private fun publish(newState: SyncState? = null) {
        newState?.let { state = it }
        val (pending, pendingIds) = synchronized(lock) {
            queue.size to queue.filterIsInstance<SyncOperation.UploadDetection>().map { it.detectionId }.toSet()
        }
        _status.update {
            it.copy(
                state = if (enabled) state else SyncState.DISABLED,
                deviceId = devices.load(),
                pending = pending,
                pendingDetectionIds = pendingIds,
            )
        }
    }

    private fun trim(set: LinkedHashSet<String>) {
        while (set.size > MAX_SEEN) set.remove(set.first())
    }

    companion object {
        private const val TAG = "SA/Sync"
        const val MAX_QUEUE = 500
        private const val MAX_ALERT_IDS = 300
        private const val MAX_SEEN = 1_000
        private val RETRYABLE_CODES = setOf(401, 403, 408, 429)
        val DEFAULT_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)
    }
}
