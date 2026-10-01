package com.soundalert.wear.audio

import com.soundalert.wear.config.GateConfig
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Decide si merece la pena ejecutar la IA sobre el bloque recién capturado.
 *
 * - LOUD: el bloque supera max(minLoudDbfs, ruidoDeFondo + margen).
 * - HANGOVER: hubo un bloque fuerte hace menos de hangoverMs.
 * - PERIODIC: hace más de forcedInferenceIntervalMs que no se infiere.
 * - QUIET: no se infiere.
 *
 * El tiempo se mide en milisegundos de audio (no de reloj), así es determinista.
 */
class EnergyGate(private val config: GateConfig, private val blockMs: Long) {

    enum class Reason { LOUD, HANGOVER, PERIODIC, QUIET }

    data class Decision(val dbfs: Float, val noiseFloorDbfs: Float, val reason: Reason) {
        val infer: Boolean get() = reason != Reason.QUIET
    }

    var noiseFloorDbfs: Float = Float.NaN
        private set

    private var lastLoudMs = Long.MIN_VALUE / 2
    private var lastInferenceMs = Long.MIN_VALUE / 2

    fun evaluate(block: ShortArray, count: Int, audioTimeMs: Long): Decision {
        val dbfs = rmsDbfs(block, count)
        if (noiseFloorDbfs.isNaN()) noiseFloorDbfs = dbfs

        val threshold = maxOf(config.minLoudDbfs, noiseFloorDbfs + config.marginOverFloorDb)
        val loud = dbfs > threshold

        // El fondo baja rápido (se adapta a sitios más silenciosos) y sube despacio
        // solo con bloques no fuertes, para que un sonido largo no se "aprenda" como fondo.
        if (dbfs < noiseFloorDbfs) {
            noiseFloorDbfs += (dbfs - noiseFloorDbfs) * 0.5f
        } else if (!loud) {
            val alpha = blockMs.toFloat() / config.floorRiseTimeConstantMs
            noiseFloorDbfs += (dbfs - noiseFloorDbfs) * alpha.coerceIn(0f, 1f)
        }

        val reason = when {
            loud -> Reason.LOUD
            audioTimeMs - lastLoudMs <= config.hangoverMs -> Reason.HANGOVER
            audioTimeMs - lastInferenceMs >= config.forcedInferenceIntervalMs -> Reason.PERIODIC
            else -> Reason.QUIET
        }
        if (loud) lastLoudMs = audioTimeMs
        if (reason != Reason.QUIET) lastInferenceMs = audioTimeMs
        return Decision(dbfs, noiseFloorDbfs, reason)
    }

    companion object {
        const val SILENCE_DBFS = -120f

        fun rmsDbfs(block: ShortArray, count: Int = block.size): Float {
            if (count == 0) return SILENCE_DBFS
            var sum = 0.0
            for (i in 0 until count) {
                val s = block[i] / 32768.0
                sum += s * s
            }
            val rms = sqrt(sum / count)
            return if (rms <= 1e-6) SILENCE_DBFS else (20 * log10(rms)).toFloat()
        }
    }
}
