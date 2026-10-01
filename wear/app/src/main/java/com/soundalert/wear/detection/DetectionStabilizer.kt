package com.soundalert.wear.detection

import com.soundalert.wear.classifier.CategoryScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.config.StabilizerConfig

/** Evento estable: lo único que sale del pipeline de IA. */
sealed interface DetectionEvent {
    val category: SoundCategory
    val atMs: Long

    /** Un sonido empieza: categoría + confianza. */
    data class Started(
        override val category: SoundCategory,
        val confidence: Float,
        val label: String,
        val fastPath: Boolean,
        override val atMs: Long,
    ) : DetectionEvent

    /** El sonido dejó de oírse (histéresis cumplida). */
    data class Ended(
        override val category: SoundCategory,
        val peakConfidence: Float,
        val durationMs: Long,
        override val atMs: Long,
    ) : DetectionEvent
}

/**
 * Convierte puntuaciones ventana a ventana en eventos estables:
 *
 * - Inicio: ≥ [StabilizerConfig.confirmRequired] de las últimas
 *   [StabilizerConfig.confirmWindows] ventanas superan el umbral de la categoría,
 *   o (solo peligro) una ventana supera [StabilizerConfig.fastPathThreshold].
 * - Mientras dura: no se emite nada más (un sonido largo = un evento).
 * - Fin: N ventanas seguidas por debajo de [StabilizerConfig.offThreshold].
 *
 * No es thread-safe: lo usa solo el hilo de inferencia.
 */
class DetectionStabilizer(private val config: StabilizerConfig, private val hopMs: Long) {

    private class Track {
        val history = ArrayDeque<CategoryScore?>()
        var active = false
        var startedAtMs = 0L
        var peak = 0f
        var windowsBelow = 0
    }

    private val tracks = SoundCategory.entries.associateWith { Track() }

    val activeCategories: Set<SoundCategory>
        get() = tracks.filterValues { it.active }.keys

    /**
     * Procesa una ventana. [scores] puede omitir categorías (cuentan como 0);
     * una ventana no inferida por la puerta de energía se pasa como lista vacía.
     */
    fun update(scores: List<CategoryScore>, atMs: Long): List<DetectionEvent> {
        val byCategory = scores.associateBy { it.category }
        val events = mutableListOf<DetectionEvent>()

        for ((category, track) in tracks) {
            val current = byCategory[category]
            val score = current?.score ?: 0f
            track.history.addLast(current)
            if (track.history.size > config.confirmWindows) track.history.removeFirst()

            if (!track.active) {
                val threshold = config.onThresholdFor(category)
                val positives = track.history.filterNotNull().filter { it.score >= threshold }
                val fast = category.danger && score >= config.fastPathThreshold
                if (fast || positives.size >= config.confirmRequired) {
                    val best = (positives + listOfNotNull(current)).maxBy { it.score }
                    track.active = true
                    track.startedAtMs = atMs
                    track.peak = best.score
                    track.windowsBelow = 0
                    events += DetectionEvent.Started(category, best.score, best.label, fast && positives.size < config.confirmRequired, atMs)
                }
            } else {
                track.peak = maxOf(track.peak, score)
                track.windowsBelow = if (score < config.offThreshold) track.windowsBelow + 1 else 0
                val release = if (category.danger) config.dangerReleaseWindows else config.releaseWindows
                if (track.windowsBelow >= release) {
                    // El sonido terminó cuando empezó la racha de ventanas bajas.
                    val endMs = atMs - (release - 1) * hopMs
                    events += DetectionEvent.Ended(category, track.peak, endMs - track.startedAtMs, atMs)
                    track.active = false
                    track.history.clear()
                }
            }
        }
        return events
    }
}
