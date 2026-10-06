package com.soundalert.wear.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.soundalert.wear.MainActivity
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.audio.MicAudioSource
import com.soundalert.wear.classifier.YamnetClassifier
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.pipeline.AudioPipeline
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.rules.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Escucha continua: foreground service de tipo `microphone`.
 *
 * Debe iniciarse con la app visible (MainActivity): Android solo concede el
 * micrófono a un foreground service iniciado así ("while-in-use"). Sigue
 * grabando con la pantalla apagada o la app cerrada.
 *
 * START_NOT_STICKY a propósito: si el sistema mata el proceso, un reinicio
 * automático en segundo plano no tendría acceso al micrófono (grabaría silencio).
 * Reanudar tras un reinicio o una muerte del proceso queda para la siguiente fase.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pipelineJob: Job? = null
    private var captureThread: ExecutorCoroutineDispatcher? = null
    private var inferenceThread: ExecutorCoroutineDispatcher? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACK) {
            // Confirmación desde la notificación: no toca el micrófono ni YAMNet.
            intent.getStringExtra(EXTRA_ALERT_ID)?.let { SoundAlertRuntime.alertManager(this).acknowledge(it) }
            if (pipelineJob?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Pausa solicitada por el usuario")
            stopSelf()
            return START_NOT_STICKY
        }
        if (pipelineJob?.isActive == true) return START_NOT_STICKY

        try {
            startForeground(NOTIFICATION_ID, buildNotification(danger = null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            fail("Android no permite iniciar la escucha desde segundo plano. Abre la app y pulsa Activar.", e)
            return START_NOT_STICKY
        } catch (e: SecurityException) {
            fail("Sin permiso de micrófono para el servicio: ${e.message}", e)
            return START_NOT_STICKY
        }

        PipelineStatus.reset(Phase.STARTING)
        startPipeline()
        showActiveDangerInNotification()
        return START_NOT_STICKY
    }

    private fun startPipeline() {
        val capture = Executors.newSingleThreadExecutor { Thread(it, "sa-capture") }.asCoroutineDispatcher()
            .also { captureThread = it }
        val inference = Executors.newSingleThreadExecutor { Thread(it, "sa-inference") }.asCoroutineDispatcher()
            .also { inferenceThread = it }

        pipelineJob = scope.launch {
            val classifier = try {
                YamnetClassifier(applicationContext)
            } catch (e: Exception) {
                fail("No se pudo cargar YAMNet: ${e.message}", e)
                return@launch
            }
            try {
                val config = PipelineConfig()
                val source = MicAudioSource(applicationContext, config.sampleRate) { silenced ->
                    PipelineStatus.update { it.copy(phase = if (silenced) Phase.SILENCED else Phase.LISTENING) }
                }
                PipelineStatus.update { it.copy(phase = Phase.LISTENING) }
                val contextual = SoundAlertRuntime.contextualClassifier(applicationContext)
                Log.i(TAG, "Escucha continua iniciada con ${classifier.info} y $config")
                Log.i(CONTEXT_TAG, "contexto actual=${SoundAlertRuntime.contextManager.current}")
                AudioPipeline(
                    source = source,
                    classifier = classifier,
                    // Misma configuración de estabilización para el mapeo y el pipeline.
                    mapper = YamnetClassifier.loadLabelMapper(applicationContext, config.stabilizer),
                    config = config,
                    captureDispatcher = capture,
                    inferenceDispatcher = inference,
                    // Evento estable → contexto activo (congelado) → regla → alerta → vibración.
                    onEvent = contextual::onEvent,
                ).run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("La escucha se detuvo por un error: ${e.message}", e)
            } finally {
                classifier.close()
            }
        }
    }

    private fun fail(message: String, error: Throwable) {
        Log.e(TAG, message, error)
        PipelineStatus.reset(Phase.ERROR, message)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        captureThread?.close()
        inferenceThread?.close()
        if (PipelineStatus.snapshot.value.phase != Phase.ERROR) PipelineStatus.reset(Phase.IDLE)
        Log.i(TAG, "Servicio de escucha detenido")
        super.onDestroy()
    }

    /** Mientras haya un DANGER activo, la notificación lo muestra con una acción "Confirmar". */
    private fun showActiveDangerInNotification() {
        val alerts = SoundAlertRuntime.alertManager(applicationContext).alerts
        scope.launch {
            alerts
                .map { list -> list.firstOrNull { it.status == AlertStatus.ACTIVE && it.priority == Priority.DANGER } }
                .distinctUntilChanged()
                .collect { danger ->
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(danger))
                }
        }
    }

    private fun buildNotification(danger: Alert?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_listening)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (danger != null) {
            val ack = PendingIntent.getService(
                this,
                2,
                Intent(this, ListeningService::class.java).setAction(ACTION_ACK).putExtra(EXTRA_ALERT_ID, danger.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentTitle(getString(R.string.notification_danger_title, danger.category.name))
                .setContentText(getString(R.string.notification_danger_text))
                .addAction(0, getString(R.string.action_acknowledge), ack)
        } else {
            builder.setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
        }
        return builder.addAction(0, getString(R.string.action_pause), stop).build()
    }

    companion object {
        private const val TAG = "SA/Service"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.soundalert.wear.STOP"
        private const val ACTION_ACK = "com.soundalert.wear.ACK"
        private const val EXTRA_ALERT_ID = "alertId"
        private const val CONTEXT_TAG = "SA/Context"

        fun start(context: Context) = context.startForegroundService(Intent(context, ListeningService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, ListeningService::class.java))
    }
}
