package com.soundalert.wear.vibration

import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority

/**
 * Patrón de vibración: [timings] en formato Android [espera, vibra, pausa, vibra…] y
 * [amplitudes] con la misma longitud (0 en esperas y pausas; 1..255 o
 * [DEFAULT_AMPLITUDE] en los pulsos).
 */
data class VibrationPattern(
    val name: String,
    val pulses: Int,
    val pulseMs: Long,
    val timings: LongArray,
    val amplitudes: IntArray,
) {
    init {
        require(timings.size == amplitudes.size) { "timings y amplitudes deben tener la misma longitud" }
    }

    /** Amplitud de los pulsos (todos iguales dentro de un patrón). */
    val pulseAmplitude: Int get() = amplitudes[1]

    /** true si los pulsos usan la amplitud por defecto del vibrador (sin control explícito). */
    val usesDefaultAmplitude: Boolean get() = pulseAmplitude == DEFAULT_AMPLITUDE

    override fun equals(other: Any?) =
        other is VibrationPattern && name == other.name && pulses == other.pulses &&
            pulseMs == other.pulseMs && timings.contentEquals(other.timings) &&
            amplitudes.contentEquals(other.amplitudes)

    override fun hashCode() = (31 * name.hashCode() + timings.contentHashCode()) * 31 + amplitudes.contentHashCode()

    override fun toString() =
        "$name timings=${timings.contentToString()} amplitudes=${amplitudes.contentToString()}"

    companion object {
        /** Igual que `VibrationEffect.DEFAULT_AMPLITUDE`; copiado para que esta clase no dependa de Android. */
        const val DEFAULT_AMPLITUDE = -1

        fun forPriority(priority: Priority, config: AlertConfig = AlertConfig()): VibrationPattern = when (priority) {
            // Peligro: intacto (amplitud por defecto, la que ya se siente bien en el reloj).
            Priority.DANGER -> build("3_LONG", 3, config.longPulseMs, config.pulseGapMs, DEFAULT_AMPLITUDE)
            Priority.ATTENTION -> build("2_MEDIUM", 2, config.mediumPulseMs, config.pulseGapMs, config.attentionAmplitude)
            Priority.INFORMATION -> build("1_SHORT", 1, config.shortPulseMs, config.pulseGapMs, config.informationAmplitude)
        }

        private fun build(name: String, pulses: Int, pulseMs: Long, gapMs: Long, amplitude: Int): VibrationPattern {
            val timings = mutableListOf(0L)
            val amplitudes = mutableListOf(0)
            repeat(pulses) { i ->
                timings += pulseMs
                amplitudes += amplitude
                if (i < pulses - 1) {
                    timings += gapMs
                    amplitudes += 0
                }
            }
            return VibrationPattern(name, pulses, pulseMs, timings.toLongArray(), amplitudes.toIntArray())
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
