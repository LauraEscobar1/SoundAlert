package com.soundalert.wear.config

import com.soundalert.wear.classifier.SoundCategory

/**
 * Parámetros del pipeline de escucha. Son valores INICIALES: se calibrarán
 * con clips reales (precisión/recall por categoría) antes de darlos por buenos.
 */
data class PipelineConfig(
    /** Frecuencia que espera YAMNet. */
    val sampleRate: Int = 16_000,
    /** Entrada fija de YAMNet: 0,975 s. */
    val windowSamples: Int = 15_600,
    /** Salto entre ventanas (500 ms → ~49 % de solape). También es el tamaño de cada lectura. */
    val hopSamples: Int = 8_000,
    /** Capacidad del buffer circular (1 s). */
    val ringSamples: Int = 16_000,
    val gate: GateConfig = GateConfig(),
    val stabilizer: StabilizerConfig = StabilizerConfig(),
) {
    val hopMs: Long get() = hopSamples * 1_000L / sampleRate

    init {
        require(windowSamples <= ringSamples) { "La ventana no cabe en el buffer" }
        require(hopSamples in 1..windowSamples) { "Salto inválido" }
    }
}

/** Puerta de energía: evita inferir cuando no hay nada que escuchar. */
data class GateConfig(
    /** Nivel absoluto mínimo para considerar que hay sonido. */
    val minLoudDbfs: Float = -55f,
    /** Cuánto debe superar el sonido al ruido de fondo. */
    val marginOverFloorDb: Float = 6f,
    /** Constante de tiempo con la que el ruido de fondo sube (baja rápido). */
    val floorRiseTimeConstantMs: Long = 30_000,
    /** Se sigue infiriendo este tiempo después del último bloque fuerte. */
    val hangoverMs: Long = 3_000,
    /** Inferencia de seguridad aunque todo parezca silencio (sonidos lejanos y constantes). */
    val forcedInferenceIntervalMs: Long = 5_000,
)

/** Convierte puntuaciones ventana a ventana en eventos estables. */
data class StabilizerConfig(
    /** Puntuación mínima para que una ventana cuente como positiva. */
    val onThreshold: Float = 0.35f,
    /** Umbrales específicos por categoría (sustituyen a [onThreshold]). */
    val onThresholdByCategory: Map<SoundCategory, Float> = emptyMap(),
    /** Positivas necesarias dentro de las últimas [confirmWindows] ventanas. */
    val confirmRequired: Int = 2,
    val confirmWindows: Int = 3,
    /** Una sola ventana por encima de esto basta para sonidos de peligro. */
    val fastPathThreshold: Float = 0.60f,
    /** Por debajo de esto una ventana cuenta para cerrar el evento (histéresis). */
    val offThreshold: Float = 0.15f,
    /** Ventanas seguidas por debajo de [offThreshold] para cerrar un evento (4 × 500 ms = 2 s). */
    val releaseWindows: Int = 4,
    /** Igual para peligro: una sirena que pasa tiene huecos (10 × 500 ms = 5 s). */
    val dangerReleaseWindows: Int = 10,
) {
    fun onThresholdFor(category: SoundCategory): Float = onThresholdByCategory[category] ?: onThreshold

    init {
        require(confirmRequired in 1..confirmWindows) { "confirmRequired debe estar entre 1 y confirmWindows" }
        require(offThreshold < onThreshold) { "offThreshold debe ser menor que onThreshold" }
    }
}
