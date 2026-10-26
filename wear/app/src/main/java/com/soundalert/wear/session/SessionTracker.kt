package com.soundalert.wear.session

import android.util.Log
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.context.SoundAlertContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Agrupa lo que pasa en el reloj en sesiones (pantalla "Reloj de sesiones"). Solo
 * observa estado que ya existe: si se está escuchando (PipelineStatus), el contexto
 * activo (ContextManager) y las alertas (AlertManager). No decide nada del sonido.
 *
 * - Una sesión empieza al reanudar la escucha o al cambiar de contexto mientras se
 *   escucha, y termina con el siguiente cambio de contexto o al pausar.
 * - Cada alerta se anota en la sesión en la que ocurrió (por su hora) y se actualiza
 *   cuando la persona la confirma.
 * - Se guarda tras cada cambio. Si el proceso murió con una sesión abierta, al
 *   restaurarla se cierra en su último momento conocido (su última alerta o su inicio).
 *
 * Thread-safe: [update] puede llegar desde cualquier hilo.
 */
class SessionTracker(
    private val store: SessionStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val state = MutableStateFlow(restore())

    /** Sesiones de la más antigua a la más reciente; la última puede estar en curso. */
    val sessions: StateFlow<List<Session>> = state

    fun update(listening: Boolean, context: SoundAlertContext, alerts: List<Alert>) = synchronized(lock) {
        val now = clock()
        var sessions = state.value
        val open = sessions.lastOrNull()?.takeIf { it.ongoing }
        if (open != null && (!listening || open.context != context)) {
            sessions = sessions.dropLast(1) + open.copy(endMs = now)
            Log.i(TAG, "FIN ${open.context} (${(now - open.startMs) / 60_000} min, ${open.events.size} alertas)")
        }
        if (listening && sessions.lastOrNull()?.ongoing != true) {
            sessions = sessions + Session(newId(), context, startMs = now, endMs = null, events = emptyList())
            Log.i(TAG, "INICIO $context")
        }
        sessions = withAlerts(sessions, alerts, now).takeLast(maxSessions)
        if (sessions != state.value) {
            state.value = sessions
            store?.save(sessions)
        }
    }

    /** Anota las alertas nuevas en su sesión y marca las confirmadas. */
    private fun withAlerts(sessions: List<Session>, alerts: List<Alert>, now: Long): List<Session> {
        if (alerts.isEmpty()) return sessions
        val recorded = sessions.flatMap { it.events }.associateBy { it.alertId }
        val result = sessions.toMutableList()
        for (alert in alerts) {
            val acknowledged = alert.status == AlertStatus.ACKNOWLEDGED
            val existing = recorded[alert.id]
            if (existing != null) {
                if (acknowledged && !existing.acknowledged) {
                    val index = result.indexOfFirst { session -> session.events.any { it.alertId == alert.id } }
                    result[index] = result[index].let { session ->
                        session.copy(events = session.events.map { if (it.alertId == alert.id) it.copy(acknowledged = true) else it })
                    }
                }
                continue
            }
            // La sesión más reciente que contiene la hora de la alerta.
            val index = result.indexOfLast { alert.createdAtMs >= it.startMs && alert.createdAtMs <= it.endOr(now) }
            if (index < 0) continue
            val event = SessionEvent(alert.id, alert.category, alert.priority, alert.createdAtMs, acknowledged)
            result[index] = result[index].let { it.copy(events = (it.events + event).sortedBy { e -> e.atMs }) }
        }
        return result
    }

    private fun restore(): List<Session> {
        val saved = store?.load().orEmpty().sortedBy { it.startMs }
        val last = saved.lastOrNull()?.takeIf { it.ongoing } ?: return saved
        val closed = last.copy(endMs = last.events.maxOfOrNull { it.atMs } ?: last.startMs)
        Log.i(TAG, "Sesión de ${last.context} abierta al morir el proceso: se cierra en su último momento conocido")
        return saved.dropLast(1) + closed
    }

    companion object {
        const val DEFAULT_MAX_SESSIONS = 200
        private const val TAG = "SA/Session"
    }
}
