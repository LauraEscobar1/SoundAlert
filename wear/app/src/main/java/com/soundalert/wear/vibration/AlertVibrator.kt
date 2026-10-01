package com.soundalert.wear.vibration

import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority

/** Patrón de vibración: [timings] en formato Android [espera, vibra, pausa, vibra…]. */
data class VibrationPattern(val name: String, val pulses: Int, val pulseMs: Long, val timings: LongArray) {
    override fun equals(other: Any?) =
        other is VibrationPattern && name == other.name && pulses == other.pulses &&
            pulseMs == other.pulseMs && timings.contentEquals(other.timings)

    override fun hashCode() = 31 * name.hashCode() + timings.contentHashCode()

    companion object {
        fun forPriority(priority: Priority, config: AlertConfig = AlertConfig()): VibrationPattern = when (priority) {
            Priority.DANGER -> build("3_LONG", 3, config.longPulseMs, config.pulseGapMs)
            Priority.ATTENTION -> build("2_MEDIUM", 2, config.mediumPulseMs, config.pulseGapMs)
            Priority.INFORMATION -> build("1_SHORT", 1, config.shortPulseMs, config.pulseGapMs)
        }

        private fun build(name: String, pulses: Int, pulseMs: Long, gapMs: Long): VibrationPattern {
            val timings = buildList {
                add(0L)
                repeat(pulses) { i ->
                    add(pulseMs)
                    if (i < pulses - 1) add(gapMs)
                }
            }.toLongArray()
            return VibrationPattern(name, pulses, pulseMs, timings)
        }
    }
}

/**
 * Hace vibrar el reloj según la prioridad. Aislado del pipeline y del
 * AlertManager para poder sustituirlo por un fake en los tests.
 */
interface AlertVibrator {
    fun vibrate(priority: Priority, pattern: VibrationPattern)

    /** Detiene una vibración en curso (p. ej. al confirmar una alerta). */
    fun cancel()
}
