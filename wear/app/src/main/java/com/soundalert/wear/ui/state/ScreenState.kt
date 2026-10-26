package com.soundalert.wear.ui.state

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.YAMNET_CATEGORY_LABELS
import com.soundalert.wear.config.DEFAULT_OFF_THRESHOLD
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Lógica de presentación: decide QUÉ muestra cada pantalla a partir del estado real
 * (PipelineStatus, AlertManager, RuleEngine). No decide nada del sonido: eso ya lo
 * hicieron el clasificador, el estabilizador y las reglas.
 */

/** Pantalla B. [confirmed]: el estabilizador ya confirmó el sonido; si no, la IA lo está analizando. */
data class DetectionDisplay(val category: SoundCategory, val confidence: Float, val confirmed: Boolean)

/**
 * Qué está oyendo la IA, a partir del estado real del pipeline:
 * - Confirmado: el estabilizador emitió `Started` hace menos de [confirmedHoldMs].
 * - Analizando: una de las 3 clases principales de YAMNet pertenece a una categoría
 *   conocida y supera [minScore] (el umbral de fin del estabilizador), aún sin confirmar.
 *   Si la confianza no llega al umbral de la categoría, no habrá alerta.
 * Un candidato se mantiene [candidateHoldMs] tras desaparecer para que no parpadee.
 * UNKNOWN (voz, música, silencio…) nunca se muestra.
 */
class DetectionTracker(
    private val labelCategories: Map<String, SoundCategory> = LABEL_CATEGORIES,
    private val minScore: Float = DEFAULT_OFF_THRESHOLD,
    private val confirmedHoldMs: Long = 3_000,
    private val candidateHoldMs: Long = 1_500,
) {
    private var lastCandidate: DetectionDisplay? = null
    private var lastCandidateAtMs = 0L

    fun update(snapshot: PipelineStatus.Snapshot, nowMs: Long): DetectionDisplay? {
        if (snapshot.phase != PipelineStatus.Phase.LISTENING) {
            lastCandidate = null
            return null
        }
        val event = snapshot.lastEvent
        if (event is DetectionEvent.Started && nowMs - snapshot.lastEventWallClockMs in 0..confirmedHoldMs) {
            return DetectionDisplay(event.category, event.confidence, confirmed = true)
        }
        val candidate = snapshot.topLabels.firstNotNullOfOrNull { top ->
            labelCategories[top.label]?.takeIf { top.score >= minScore }?.let { DetectionDisplay(it, top.score, confirmed = false) }
        }
        if (candidate != null) {
            lastCandidate = candidate
            lastCandidateAtMs = nowMs
            return candidate
        }
        return lastCandidate?.takeIf { nowMs - lastCandidateAtMs <= candidateHoldMs }
    }

    companion object {
        /** Etiqueta de YAMNet → categoría, del mismo mapeo que usa el LabelMapper. */
        val LABEL_CATEGORIES: Map<String, SoundCategory> =
            YAMNET_CATEGORY_LABELS.flatMap { (category, labels) -> labels.map { it to category } }.toMap()
    }
}

/** Pantalla C: la alerta ACTIVE de mayor prioridad; a igualdad, la más reciente. */
fun alertOnScreen(alerts: List<Alert>): Alert? =
    alerts.filter { it.status == AlertStatus.ACTIVE }
        .minWithOrNull(compareBy<Alert> { it.priority.ordinal }.thenByDescending { it.createdAtMs })

/** Celda del panal G. [enabled] false: el usuario la apagó en este contexto (no alerta). */
data class SoundCell(val category: SoundCategory, val priority: Priority, val enabled: Boolean)

/**
 * Pantalla G: los sonidos REALES que se pueden vigilar en [context] (reglas de la tabla
 * del RuleEngine), encendidos o apagados por el usuario. Peligro primero, luego atención
 * y aviso. Las categorías de solo registro y UNKNOWN no tienen regla y no aparecen.
 */
fun soundCells(context: SoundAlertContext, rules: RuleEngine): List<SoundCell> =
    rules.availableRules(context)
        .sortedWith(compareBy({ it.priority.ordinal }, { it.category.ordinal }))
        .map { SoundCell(it.category, it.priority, rules.isEnabled(context, it.category)) }

/** Celdas por página del panal (filas de 2 – 3 – 2). */
const val CELLS_PER_PAGE = 7

/** Orden del resumen de la pantalla A: lo más reconocible de cada contexto primero. */
private val SUMMARY_ORDER = listOf(
    SoundCategory.DOORBELL,
    SoundCategory.CAR_HORN,
    SoundCategory.FIRE_ALARM,
    SoundCategory.BABY_CRYING,
    SoundCategory.BICYCLE_BELL,
    SoundCategory.SIREN,
)

/** Pantalla A: hasta [max] sonidos que se vigilan en [context] ("Timbre · Alarma · Bebé"). */
fun watchedSummary(context: SoundAlertContext, rules: RuleEngine, name: (SoundCategory) -> String, max: Int = 3): List<String> {
    val watched = rules.rulesFor(context).map { it.category }.toSet()
    val ordered = SUMMARY_ORDER.filter { it in watched } + SoundCategory.ALERTABLE.filter { it in watched && it !in SUMMARY_ORDER }
    return ordered.map(name).distinct().take(max)
}

/** Hora si es de hoy ("10:12", o "4:15 PM" con el reloj en 12 h); "03/10" si es de otro día. */
fun historyTime(atMs: Long, nowMs: Long, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(atMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return at.format(if (at.toLocalDate() != today) DAY else if (is24Hour) HOUR_24 else HOUR_12)
}

fun isToday(atMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

/** "ahora" durante el primer minuto; después "hace N min". */
fun relativeTime(atMs: Long, nowMs: Long): String {
    val minutes = (nowMs - atMs) / 60_000
    return if (minutes < 1) "ahora" else "hace $minutes min"
}

/** Aviso: "ahora" el primer segundo, "hace 2 s" durante el primer minuto y después "hace N min". */
fun relativeTimeSeconds(atMs: Long, nowMs: Long): String {
    val seconds = (nowMs - atMs) / 1_000
    return when {
        seconds < 1 -> "ahora"
        seconds < 60 -> "hace $seconds s"
        else -> relativeTime(atMs, nowMs)
    }
}

private val HOUR_24 = DateTimeFormatter.ofPattern("HH:mm")
private val HOUR_12 = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
private val DAY = DateTimeFormatter.ofPattern("dd/MM")
