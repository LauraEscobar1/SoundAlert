package com.soundalert.wear

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.soundalert.wear.alert.AlertManager
import com.soundalert.wear.api.SoundAlertApi
import com.soundalert.wear.api.UrlConnectionTransport
import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.ContextStore
import com.soundalert.wear.context.SharedPreferencesContextStore
import com.soundalert.wear.context.ContextualClassifier
import com.soundalert.wear.context.DetectionHistory
import com.soundalert.wear.history.HistoryRepository
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.sync.BackendSync
import com.soundalert.wear.sync.DeviceRegistration
import com.soundalert.wear.sync.FileSyncStore
import com.soundalert.wear.sync.SharedPreferencesDeviceStore
import com.soundalert.wear.vibration.AndroidAlertVibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Instancias únicas del proceso: el contexto y las alertas viven mientras viva
 * la app, no solo mientras escucha el servicio. Así una alerta DANGER sigue
 * activa (y se puede confirmar) aunque se pause la escucha.
 */
object SoundAlertRuntime {
    @Volatile private var contextStore: ContextStore? = null

    /** Lo llama SoundAlertApp al arrancar el proceso. */
    fun init(context: Context) {
        if (contextStore == null) contextStore = SharedPreferencesContextStore(context)
    }

    /**
     * Contexto activo: UNA sola instancia por proceso, compartida por la pantalla y el
     * pipeline. Se restaura desde el almacenamiento (ver ContextManager).
     */
    val contextManager: ContextManager by lazy {
        ContextManager(store = checkNotNull(contextStore) { "SoundAlertRuntime.init() no se ha llamado (SoundAlertApp)" })
    }

    /** Todas las detecciones con su contexto (alerten o no). */
    val detectionHistory = DetectionHistory()

    /** Reglas fijas del reloj: las usa el pipeline y las muestra la pantalla de sonidos. */
    val ruleEngine = RuleEngine()

    /** Tiempos de las alertas: los aplica el AlertManager y los dibuja la pantalla de alerta. */
    val alertConfig = AlertConfig()

    @Volatile private var alertManager: AlertManager? = null
    @Volatile private var contextualClassifier: ContextualClassifier? = null

    fun alertManager(context: Context): AlertManager =
        alertManager ?: synchronized(this) {
            alertManager ?: AlertManager(
                vibrator = AndroidAlertVibrator(context.applicationContext),
                config = alertConfig,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ).also { alertManager = it }
        }

    /** Entrada de los eventos del pipeline: categoría + contexto activo → regla → alerta. */
    fun contextualClassifier(context: Context): ContextualClassifier =
        contextualClassifier ?: synchronized(this) {
            contextualClassifier ?: ContextualClassifier(
                contextProvider = contextManager,
                rules = ruleEngine,
                history = detectionHistory,
                alerts = alertManager(context),
            ).also { contextualClassifier = it }
        }

    // ---------- Backend (Railway → Supabase): secundario a la detección local ----------

    @Volatile private var backendSync: BackendSync? = null
    @Volatile private var historyRepository: HistoryRepository? = null
    @Volatile private var networkCallbackRegistered = false

    private val api: SoundAlertApi by lazy { SoundAlertApi(BuildConfig.API_BASE_URL, UrlConnectionTransport()) }

    /** Envía al backend lo que ya pasó en el reloj (dispositivo, contexto, detecciones, confirmaciones). */
    fun backendSync(context: Context): BackendSync =
        backendSync ?: synchronized(this) {
            backendSync ?: run {
                val app = context.applicationContext
                val stabilizer = PipelineConfig().stabilizer
                BackendSync(
                    api = api,
                    devices = SharedPreferencesDeviceStore(app),
                    store = FileSyncStore(File(app.filesDir, "sync-queue.json")),
                    // Mismo modelo que YamnetClassifier.info.
                    classifier = ClassifierInfo(name = "yamnet-litert", version = "classification-tflite-1"),
                    registration = {
                        DeviceRegistration(
                            name = "SoundAlert · ${Build.MODEL}".take(80),
                            context = contextManager.current,
                            // El umbral más bajo del reloj: el backend no descarta lo que el reloj ya confirmó.
                            minConfidence = SoundCategory.KNOWN.minOf { stabilizer.onThresholdFor(it) },
                        )
                    },
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                ).also { backendSync = it }
            }
        }

    fun historyRepository(context: Context): HistoryRepository =
        historyRepository ?: synchronized(this) {
            historyRepository ?: HistoryRepository(api, SharedPreferencesDeviceStore(context.applicationContext)).also { historyRepository = it }
        }

    /**
     * Empieza a sincronizar (idempotente). Lo llama MainActivity: la escucha siempre se
     * inicia desde ella, así que la sincronización ya está activa cuando hay detecciones.
     * Al volver la conexión se reintenta enseguida, sin esperar al siguiente reintento.
     */
    fun startBackendSync(context: Context) {
        val app = context.applicationContext
        backendSync(app).start(detectionHistory.detections, alertManager(app).alerts, contextManager.context)
        synchronized(this) {
            if (networkCallbackRegistered) return
            networkCallbackRegistered = true
        }
        app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = backendSync(app).requestSync()
            },
        )
    }
}
