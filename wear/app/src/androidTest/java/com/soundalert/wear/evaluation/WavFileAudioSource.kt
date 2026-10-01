package com.soundalert.wear.evaluation

import com.soundalert.wear.audio.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fuente de audio a partir de un WAV PCM16 mono a 16 kHz, para pasar grabaciones
 * reales por el pipeline sin micrófono. Añade silencio antes (para que el buffer
 * esté lleno cuando empiece el sonido, como en la escucha continua) y después
 * (para que los eventos terminen).
 */
class WavFileAudioSource(
    wav: ByteArray,
    private val leadingSilenceMs: Int = 1_500,
    private val trailingSilenceMs: Int = 3_000,
) : AudioSource {

    override val sampleRate = 16_000
    private val samples: ShortArray

    init {
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        require(String(wav, 0, 4) == "RIFF" && String(wav, 8, 4) == "WAVE") { "No es un WAV" }
        var pos = 12
        var data: ShortArray? = null
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4)
            val size = buf.getInt(pos + 4)
            if (id == "fmt ") {
                require(buf.getShort(pos + 8).toInt() == 1) { "Solo PCM" }
                require(buf.getShort(pos + 10).toInt() == 1) { "Solo mono" }
                require(buf.getInt(pos + 12) == sampleRate) { "Solo 16 kHz" }
                require(buf.getShort(pos + 22).toInt() == 16) { "Solo 16 bits" }
            } else if (id == "data") {
                val n = minOf(size, wav.size - pos - 8) / 2
                data = ShortArray(n) { buf.getShort(pos + 8 + it * 2) }
            }
            pos += 8 + size + (size and 1)
        }
        samples = requireNotNull(data) { "Sin chunk data" }
    }

    override fun blocks(blockSamples: Int): Flow<ShortArray> = flow {
        val lead = sampleRate * leadingSilenceMs / 1_000
        val trail = sampleRate * trailingSilenceMs / 1_000
        val total = lead + samples.size + trail
        var offset = 0
        while (offset < total) {
            val block = ShortArray(blockSamples) { i ->
                val s = offset + i - lead
                if (s in samples.indices) samples[s] else 0
            }
            emit(block)
            offset += blockSamples
            // Cede el hilo a la inferencia: el micrófono real entrega un bloque cada 500 ms.
            yield()
        }
    }
}
