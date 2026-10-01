package com.soundalert.wear.alert

import android.util.Log
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.vibration.AlertVibrator
import com.soundalert.wear.vibration.VibrationPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * DetectionEvent → contexto actual → regla → prioridad → Alert → vibración.
 *
 * - UNKNOWN y sonidos sin regla en el contexto actual se ignoran. Las categorías
 *   de solo registro (BELL, WARNING_SIGNAL) se anotan en el log y nada más.
 * - Un sonido sostenido ya llega como un único `Started` (DetectionStabilizer).
 *   Además, no se crea otra alerta de una categoría que ya tiene una ACTIVE, ni
 *   antes de [AlertConfig.cooldownMs] desde la última alerta de esa categoría.
 * - GENERAL_ALARM no alerta si una alarma específica (SIREN, FIRE_ALARM…) ya lo hizo
 *   (activa o dentro del cooldown): la clase padre "Alarm" se activa junto a ellas.
 * - DANGER solo pasa a ACKNOWLEDGED con [acknowledge]; nunca expira. Mientras siga
 *   ACTIVE, repite la vibración de LA MISMA alerta cada [AlertConfig.dangerRepeatIntervalMs]
 *   (no crea alertas nuevas). Confirmarla detiene la repetición al momento.
 *   ATTENTION e INFORMATION expiran solas tras su timeout.
 * - Confirmar una alerta no afecta al micrófono ni a YAMNet.
 *
 * - Vibración: una vibración en curso de mayor prioridad no se interrumpe por otra
 *   de menor prioridad (Android sustituye la vibración actual por la nueva). La
 *   alerta se crea igual; solo se omite su vibración. Confirmar una alerta solo
 *   corta la vibración si es la suya.
 * - El historial se recorta a [AlertConfig.historySize] alertas CERRADAS: una alerta
 *   ACTIVE nunca sale del historial (si no, un DANGER sin confirmar se perdería).
 *
 * Thread-safe: recibe eventos del hilo de inferencia, confirmaciones de la UI
 * y expiraciones de sus temporizadores. Todo el estado (incluida la decisión de
 * vibrar) se modifica bajo un único lock.
 */
class AlertManager(
    private val contextManager: ContextManager,
    private val rules: RuleEngine,
    private val vibrator: AlertVibrator,
    private val config: AlertConfig,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val state = MutableStateFlow<List<Alert>>(emptyList())
    private val expiryJobs = HashMap<String, Job>()
    private val repeatJobs = HashMap<String, Job>()
    private val lastAlertAt = HashMap<SoundCategory, Long>()

    /** Vibración lanzada más recientemente (para no interrumpirla con otra de menor prioridad). */
    private class Vibration(val alertId: String, val priority: Priority, val startedAtMs: Long, val durationMs: Long) {
        fun runningAt(nowMs: Long) = nowMs < startedAtMs + durationMs
    }
    private var lastVibration: Vibration? = null

    /** Historial en memoria, más reciente primero (incluye las cerradas). */
    val alerts: StateFlow<List<Alert>> = state

    val activeAlerts: List<Alert> get() = state.value.filter { it.status == AlertStatus.ACTIVE }

    fun onDetection(event: DetectionEvent) {
        when (event) {
            is DetectionEvent.Started -> onStarted(event)
            is DetectionEvent.Ended -> {
                val active = activeAlerts.firstOrNull { it.category == event.category }
                if (active?.priority == Priority.DANGER) {
                    Log.i(TAG, "${event.category} dejó de oírse; la alerta DANGER id=${active.id} sigue ACTIVE hasta que se confirme")
                }
            }
        }
    }

    private fun onStarted(event: DetectionEvent.Started) {
        if (!event.category.known) {
            Log.w(TAG, "Evento UNKNOWN descartado (no debería llegar aquí)")
            return
        }
        if (!event.category.alertable) {
            Log.i(
                TAG,
                String.format(Locale.US, "REGISTRO %s confidence=%.2f label=\"%s\" (solo registro: sin alerta ni vibración)", event.category, event.confidence, event.label),
            )
            return
        }
        val context = contextManager.current
        val rule = rules.match(event.category, context)
        if (rule == null) {
            Log.i(RULE_TAG, "${event.category} + $context -> sin regla (no relevante, se ignora)")
            return
        }
        Log.i(RULE_TAG, "${event.category} + $context -> ${rule.priority}")

        synchronized(lock) {
            val now = clock()
            if (event.category == SoundCategory.GENERAL_ALARM) {
                // "Alarm" se activa junto a las alarmas específicas: si una de ellas ya
                // alertó (activa o hace menos del cooldown), no se avisa dos veces.
                val specific = state.value.firstOrNull {
                    it.category in SoundCategory.SPECIFIC_ALARMS &&
                        (it.status == AlertStatus.ACTIVE || now - it.createdAtMs < config.cooldownMs)
                }
                if (specific != null) {
                    Log.i(TAG, "GENERAL_ALARM descartada: ya hay alerta de ${specific.category} (id=${specific.id})")
                    return
                }
            }
            val active = activeAlerts.firstOrNull { it.category == event.category }
            if (active != null) {
                Log.i(TAG, "${rule.priority} ${event.category} ya está ACTIVE (id=${active.id}); no se crea otra")
                return
            }
            val last = lastAlertAt[event.category]
            if (last != null && now - last < config.cooldownMs) {
                Log.i(TAG, "COOLDOWN ${event.category}: última alerta hace ${now - last} ms (< ${config.cooldownMs} ms)")
                return
            }
            Alert(
                id = UUID.randomUUID().toString(),
                category = event.category,
                label = event.label,
                confidence = event.confidence,
                priority = rule.priority,
                context = context,
                createdAtMs = now,
            ).also {
                lastAlertAt[event.category] = now
                state.value = trimHistory(listOf(it) + state.value)
                timeoutFor(it.priority)?.let { timeout -> scheduleExpiry(it.id, timeout) }
                if (it.priority == Priority.DANGER && config.dangerRepeatIntervalMs > 0) scheduleDangerRepeat(it)
                Log.i(
                    TAG,
                    String.format(Locale.US, "START %s %s confidence=%.2f label=\"%s\" context=%s id=%s", it.priority, it.category, it.confidence, it.label, it.context, it.id),
                )
                vibrateLocked(it, now)
            }
        }
    }

    /** Confirmación explícita del usuario. Devuelve false si la alerta no estaba ACTIVE. */
    fun acknowledge(id: String): Boolean = synchronized(lock) {
        val alert = close(id, AlertStatus.ACKNOWLEDGED) ?: return false
        // Solo se corta la vibración en curso si es la de esta alerta.
        val running = lastVibration
        if (running != null && running.alertId == id && running.runningAt(clock())) {
            vibrator.cancel()
            lastVibration = null
        }
        Log.i(TAG, "ACK ${alert.priority} ${alert.category} id=${alert.id}")
        true
    }

    /** Vibra por [alert] salvo que haya en curso una vibración de mayor prioridad. Llamar con el lock tomado. */
    private fun vibrateLocked(alert: Alert, nowMs: Long) {
        val running = lastVibration?.takeIf { it.runningAt(nowMs) }
        if (running != null && running.priority.ordinal < alert.priority.ordinal) {
            Log.i(TAG, "Vibración de ${alert.priority} ${alert.category} omitida: hay una ${running.priority} en curso")
            return
        }
        val pattern = VibrationPattern.forPriority(alert.priority, config)
        vibrator.vibrate(alert.priority, pattern)
        lastVibration = Vibration(alert.id, alert.priority, nowMs, pattern.timings.sum())
    }

    /** Conserva todas las ACTIVE y, de las cerradas, las [AlertConfig.historySize] más recientes que quepan. */
    private fun trimHistory(alerts: List<Alert>): List<Alert> {
        var closedBudget = config.historySize - alerts.count { it.status == AlertStatus.ACTIVE }
        return alerts.filter { it.status == AlertStatus.ACTIVE || closedBudget-- > 0 }
    }

    private fun timeoutFor(priority: Priority): Long? = when (priority) {
        Priority.DANGER -> null
        Priority.ATTENTION -> config.attentionTimeoutMs
        Priority.INFORMATION -> config.informationTimeoutMs
    }

    private fun scheduleExpiry(id: String, timeoutMs: Long) {
        expiryJobs[id] = scope.launch {
            delay(timeoutMs)
            close(id, AlertStatus.EXPIRED)?.let { Log.i(TAG, "EXPIRED ${it.priority} ${it.category} id=${it.id} tras $timeoutMs ms") }
        }
    }

    /** Repite la vibración de una alerta DANGER mientras siga ACTIVE. */
    private fun scheduleDangerRepeat(alert: Alert) {
        repeatJobs[alert.id] = scope.launch {
            var repetition = 1
            while (true) {
                delay(config.dangerRepeatIntervalMs)
                // Comprobar y vibrar de forma atómica: una confirmación concurrente
                // ocurre antes (no vibra) o después (cancela esta vibración).
                val repeated = synchronized(lock) {
                    val current = state.value.firstOrNull { it.id == alert.id && it.status == AlertStatus.ACTIVE }
                    if (current != null) {
                        repetition++
                        Log.i(TAG, "REPEAT DANGER ${alert.category} id=${alert.id} vibración #$repetition (sin confirmar)")
                        vibrateLocked(current, clock())
                    }
                    current != null
                }
                if (!repeated) break
            }
        }
    }

    /** ACTIVE → [status]. Devuelve la alerta cerrada o null si no estaba activa. */
    private fun close(id: String, status: AlertStatus): Alert? = synchronized(lock) {
        val current = state.value.firstOrNull { it.id == id && it.status == AlertStatus.ACTIVE } ?: return null
        val closed = current.copy(status = status, closedAtMs = clock())
        state.value = state.value.map { if (it.id == id) closed else it }
        expiryJobs.remove(id)?.let { if (status != AlertStatus.EXPIRED) it.cancel() }
        repeatJobs.remove(id)?.cancel()
        closed
    }

    private companion object {
        const val TAG = "SA/Alert"
        const val RULE_TAG = "SA/Rule"
    }
}
