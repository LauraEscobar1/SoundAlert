package com.soundalert.wear

import android.content.Context
import com.soundalert.wear.alert.AlertManager
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.vibration.AndroidAlertVibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Instancias únicas del proceso: el contexto y las alertas viven mientras viva
 * la app, no solo mientras escucha el servicio. Así una alerta DANGER sigue
 * activa (y se puede confirmar) aunque se pause la escucha.
 */
object SoundAlertRuntime {
    val contextManager = ContextManager()

    @Volatile private var alertManager: AlertManager? = null

    fun alertManager(context: Context): AlertManager =
        alertManager ?: synchronized(this) {
            alertManager ?: AlertManager(
                contextManager = contextManager,
                rules = RuleEngine(),
                vibrator = AndroidAlertVibrator(context.applicationContext),
                config = AlertConfig(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ).also { alertManager = it }
        }
}
