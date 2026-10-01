package com.soundalert.wear.audio

/**
 * PCM16 → float32 en [-1, 1], el formato de entrada de YAMNet.
 * No se normaliza el volumen: el espectrograma log-mel se calcula dentro
 * del modelo y la energía real del sonido es información útil.
 */
object AudioPreprocessor {
    private const val SCALE = 1f / 32768f

    fun toFloat(src: ShortArray, dest: FloatArray) {
        require(dest.size == src.size) { "Tamaños distintos: ${src.size} vs ${dest.size}" }
        for (i in src.indices) dest[i] = src[i] * SCALE
    }
}
