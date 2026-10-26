package com.soundalert.wear.ui.sessions

import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Lógica de presentación de las pantallas H (pila de sesiones) e I (detalle). Solo
 * lee las sesiones del SessionTracker.
 */

/** Sesiones de hoy (las que tocan el día local de [nowMs]), de la más antigua a la más reciente. */
fun todaySessions(sessions: List<Session>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Session> {
    val startOfDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return sessions.filter { it.endOr(nowMs) >= startOfDay && it.startMs <= nowMs }.sortedBy { it.startMs }
}

/** Tarjeta de la pila de sesiones: una sesión o el resumen del día ("Tu día"). */
sealed interface StackCard {
    data class SessionCard(val session: Session) : StackCard
    data class DayCard(val day: DaySummary) : StackCard
}

/** La pila: de la sesión más reciente (la actual) a la más antigua de hoy y, al final, "Tu día". */
fun stackCards(sessions: List<Session>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<StackCard> {
    val today = todaySessions(sessions, nowMs, zone)
    return today.reversed().map { StackCard.SessionCard(it) } + StackCard.DayCard(daySummary(today, nowMs))
}

/** Resumen de "Tu día": sesiones, tiempo escuchando, alertas por nivel y la sesión más tranquila. */
data class DaySummary(
    val sessions: Int,
    val listeningMs: Long,
    val danger: Int,
    val attention: Int,
    val information: Int,
    val calmest: Session?,
) {
    val alerts: Int get() = danger + attention + information
}

fun daySummary(today: List<Session>, nowMs: Long): DaySummary {
    val events = today.flatMap { it.events }
    return DaySummary(
        sessions = today.size,
        listeningMs = today.sumOf { it.endOr(nowMs) - it.startMs },
        danger = events.count { it.priority == Priority.DANGER },
        attention = events.count { it.priority == Priority.ATTENTION },
        information = events.count { it.priority == Priority.INFORMATION },
        // La de menos alertas por hora; a igualdad, la más larga.
        calmest = today.minWithOrNull(compareBy<Session> { alertsPerHour(it, nowMs) }.thenByDescending { it.endOr(nowMs) - it.startMs }),
    )
}

private fun alertsPerHour(session: Session, nowMs: Long): Double {
    val hours = maxOf(session.endOr(nowMs) - session.startMs, 60_000L) / 3_600_000.0
    return session.events.count { it.priority != null } / hours
}

/**
 * Resumen de una sesión: "3 sonidos · 1 peligro confirmado" (el nivel más alto que hubo; el
 * peligro dice si se confirmó), "2 sonidos · sin alertas" o "Sin alertas en esta sesión".
 */
fun sessionSummary(session: Session): String {
    val events = session.events
    if (events.isEmpty()) return "Sin alertas en esta sesión"
    val sounds = "${events.size} ${if (events.size == 1) "sonido" else "sonidos"}"
    val top = Priority.entries.firstOrNull { level -> events.any { it.priority == level } } ?: return "$sounds · sin alertas"
    val ofTop = events.filter { it.priority == top }
    val suffix = when {
        top != Priority.DANGER -> ""
        ofTop.all { it.acknowledged } -> if (ofTop.size == 1) " confirmado" else " confirmados"
        else -> " sin confirmar"
    }
    return "$sounds · ${ofTop.size} ${levelNoun(top, ofTop.size)}$suffix"
}

/** "1 sesión", "4 sesiones". */
fun sessionsText(count: Int): String = if (count == 1) "1 sesión" else "$count sesiones"

/** "2 PELIGRO · 3 ATENCIÓN · 2 AVISO", o "SIN ALERTAS HOY". */
fun levelsLine(day: DaySummary): String =
    if (day.alerts == 0) "SIN ALERTAS HOY" else "${day.danger} PELIGRO · ${day.attention} ATENCIÓN · ${day.information} AVISO"

/**
 * A qué tarjeta va la pila al soltar un arrastre. [offset]: cuánto se desplazó, en
 * tarjetas (positivo hacia adelante). Pasado el [threshold], avanza o retrocede una.
 */
fun settleIndex(current: Int, offset: Float, count: Int, threshold: Float = SETTLE_THRESHOLD): Int {
    val target = when {
        offset >= threshold -> current + 1
        offset <= -threshold -> current - 1
        else -> current
    }
    return target.coerceIn(0, (count - 1).coerceAtLeast(0))
}

const val SETTLE_THRESHOLD = 0.3f

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

internal fun levelNoun(priority: Priority, count: Int): String = when (priority) {
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
