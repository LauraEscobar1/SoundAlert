package com.soundalert.wear.session

import android.util.Log
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Agrupa lo que pasa en el reloj en sesiones (pantalla "Reloj de sesiones"). Solo
 * observa estado que ya existe: si se está escuchando (PipelineStatus), el contexto
 * activo (ContextManager), las detecciones (DetectionHistory) y las alertas
 * (AlertManager). No decide nada del sonido.
 *
 * - Una sesión empieza al reanudar la escucha o al cambiar de contexto mientras se
 *   escucha, y termina con el siguiente cambio de contexto o al pausar. Una sesión que
 *   se cierra vacía antes de [minEmptySessionMs] (pausar y reanudar enseguida) se descarta.
 * - Cada sonido detectado se anota en la sesión en la que ocurrió (por su hora), alerte
 *   o no. Si alertó, lleva el nivel de su alerta y se marca cuando la persona la confirma.
 * - Se guarda tras cada cambio. Si el proceso murió con una sesión abierta, al
 *   restaurarla se cierra en su último momento conocido (su último sonido o su inicio).
 *
 * Thread-safe: [update] puede llegar desde cualquier hilo.
 */
class SessionTracker(
    private val store: SessionStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val minEmptySessionMs: Long = DEFAULT_MIN_EMPTY_SESSION_MS,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val state = MutableStateFlow(restore())

    /** Sesiones de la más antigua a la más reciente; la última puede estar en curso. */
    val sessions: StateFlow<List<Session>> = state

    fun update(listening: Boolean, context: SoundAlertContext, detections: List<ContextualDetection>, alerts: List<Alert>) = synchronized(lock) {
        val now = clock()
        var sessions = state.value
        val open = sessions.lastOrNull()?.takeIf { it.ongoing }
        if (open != null && (!listening || open.context != context)) {
            val closed = open.copy(endMs = now)
            val discard = closed.events.isEmpty() && now - closed.startMs < minEmptySessionMs
            sessions = sessions.dropLast(1) + listOfNotNull(closed.takeUnless { discard })
            Log.i(TAG, if (discard) "DESCARTADA ${open.context} (vacía y corta)" else "FIN ${open.context} (${(now - open.startMs) / 60_000} min, ${open.events.size} sonidos)")
        }
        if (listening && sessions.lastOrNull()?.ongoing != true) {
            sessions = sessions + Session(newId(), context, startMs = now, endMs = null, events = emptyList())
            Log.i(TAG, "INICIO $context")
        }
        sessions = withEvents(sessions, detections, alerts, now).takeLast(maxSessions)
        if (sessions != state.value) {
            state.value = sessions
            store?.save(sessions)
        }
    }

    /** Anota los sonidos nuevos en su sesión y les añade o actualiza su alerta. */
    private fun withEvents(sessions: List<Session>, detections: List<ContextualDetection>, alerts: List<Alert>, now: Long): List<Session> {
        if (detections.isEmpty() && alerts.isEmpty()) return sessions
        val alertByDetection = alerts.associateBy { it.detectionId }
        val recorded = sessions.flatMap { it.events }.associateBy { it.id }
        val result = sessions.toMutableList()

        fun place(event: SessionEvent) {
            // La sesión más reciente que contiene la hora del sonido.
            val index = result.indexOfLast { event.atMs >= it.startMs && event.atMs <= it.endOr(now) }
            if (index >= 0) result[index] = result[index].let { it.copy(events = (it.events + event).sortedBy { e -> e.atMs }) }
        }

        fun replace(updated: SessionEvent) {
            val index = result.indexOfFirst { session -> session.events.any { it.id == updated.id } }
            result[index] = result[index].let { session -> session.copy(events = session.events.map { if (it.id == updated.id) updated else it }) }
        }

        // Detecciones (alerten o no) y, por si la detección ya salió del historial en
        // memoria, también las alertas sin detección conocida.
        val seen = HashSet<String>()
        val incoming = detections.map { d ->
            seen += d.id
            val alert = alertByDetection[d.id]
            SessionEvent(d.id, d.category, alert?.priority, d.detectedAtMs, alert?.status == AlertStatus.ACKNOWLEDGED, alert?.id)
        } + alerts.filter { it.detectionId !in seen }.map { a ->
            SessionEvent(a.detectionId, a.category, a.priority, a.createdAtMs, a.status == AlertStatus.ACKNOWLEDGED, a.id)
        }
        for (event in incoming) {
            val existing = recorded[event.id]
            when {
                existing == null -> place(event)
                // Una alerta que llega después de su detección, o su confirmación.
                existing.alertId == null && event.alertId != null -> replace(existing.copy(priority = event.priority, alertId = event.alertId, acknowledged = event.acknowledged))
                event.acknowledged && !existing.acknowledged -> replace(existing.copy(acknowledged = true))
            }
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
        const val DEFAULT_MIN_EMPTY_SESSION_MS = 60_000L
        private const val TAG = "SA/Session"
    }
}
