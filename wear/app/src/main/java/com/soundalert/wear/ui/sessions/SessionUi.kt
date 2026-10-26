package com.soundalert.wear.ui.sessions

import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Lógica de presentación de las pantallas H (reloj de sesiones) e I (detalle). Solo
 * lee las sesiones del SessionTracker.
 */

/** La esfera cubre 12 horas: se muestran las sesiones que tocan las últimas 12. */
const val DIAL_WINDOW_MS = 12 * 60 * 60 * 1_000L

fun visibleSessions(sessions: List<Session>, nowMs: Long): List<Session> =
    sessions.filter { it.endOr(nowMs) > nowMs - DIAL_WINDOW_MS }

/** Ángulo en la esfera de 12 h, en grados desde las 12 en el sentido de las agujas. */
fun dialAngle(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): Float {
    val time = Instant.ofEpochMilli(atMs).atZone(zone)
    val minutes = (time.hour % 12) * 60 + time.minute + time.second / 60f
    return minutes / (12 * 60) * 360f
}

/** Grados que ocupa una sesión en la esfera: al menos [MIN_SWEEP] para que se vea, como mucho la vuelta entera. */
fun dialSweep(startMs: Long, endMs: Long): Float =
    ((endMs - startMs).toFloat() / DIAL_WINDOW_MS * 360f).coerceIn(MIN_SWEEP, 360f)

const val MIN_SWEEP = 2.5f

/** Posición (0 → 1) de [atMs] entre el inicio y el fin de la sesión, para el arco del detalle. */
fun sessionFraction(atMs: Long, startMs: Long, endMs: Long): Float =
    if (endMs <= startMs) 0.5f else ((atMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)

/** "40 min", "3 h 40", "2 h". */
fun durationText(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> String.format(Locale.US, "%d h %02d", minutes / 60, minutes % 60)
    }
}

/** "08:30" o "8:30 AM" según el formato del reloj. */
fun clockTime(atMs: Long, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(atMs).atZone(zone).format(if (is24Hour) HOUR_24 else HOUR_12)

/** "08:30 – 12:10 · 3 h 40"; la sesión en curso termina "ahora". */
fun rangeText(session: Session, nowMs: Long, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val end = if (session.ongoing) "ahora" else clockTime(session.endOr(nowMs), is24Hour, zone)
    return "${clockTime(session.startMs, is24Hour, zone)} – $end · ${durationText(session.endOr(nowMs) - session.startMs)}"
}

/**
 * Conteo por nivel, del más urgente al menos ("2 avisos"), y al final los sonidos que no
 * alertaron (nivel null, "3 sin alerta"). Lo que no ocurrió no aparece.
 */
fun levelCounts(events: List<SessionEvent>): List<Pair<Priority?, String>> {
    val levels = Priority.entries.mapNotNull { priority ->
        val count = events.count { it.priority == priority }
        if (count == 0) null else priority to "$count ${levelNoun(priority, count)}"
    }
    val silent = events.count { it.priority == null }
    return levels + listOfNotNull(if (silent > 0) null to "$silent sin alerta" else null)
}

private fun levelNoun(priority: Priority, count: Int): String = when (priority) {
    Priority.DANGER -> if (count == 1) "peligro" else "peligros"
    Priority.ATTENTION -> if (count == 1) "atención" else "atenciones"
    Priority.INFORMATION -> if (count == 1) "aviso" else "avisos"
}

/** Qué pasó con el sonido: peligro solo se cierra al confirmarlo; atención y aviso, también solos. */
fun eventOutcome(event: SessionEvent): String = when {
    event.priority == null -> if (event.category.alertable) "Sin alerta en este contexto" else "Solo registro"
    event.acknowledged -> "Confirmada por ti"
    event.priority == Priority.DANGER -> "Sin confirmar"
    else -> "Se cerró sola"
}

private val HOUR_24 = DateTimeFormatter.ofPattern("HH:mm")
private val HOUR_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
