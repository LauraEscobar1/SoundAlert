package com.soundalert.wear.classifier

/**
 * Contrato del modelo de IA. El resto del pipeline no sabe qué modelo hay
 * detrás: hoy YAMNet (LiteRT); mañana podría ser otro con su propio [LabelMapper].
 */
interface SoundClassifier : AutoCloseable {
    val info: ClassifierInfo

    /** Muestras float [-1, 1] que espera el modelo por inferencia. */
    val inputSamples: Int

    /** Devuelve una puntuación 0-1 por clase nativa del modelo (índice = clase). */
    fun classify(waveform: FloatArray): FloatArray
}

data class ClassifierInfo(val name: String, val version: String)
