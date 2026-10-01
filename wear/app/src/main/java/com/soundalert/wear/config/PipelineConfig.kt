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
    /** Puntuación mínima para que una ventana cuente como positiva (categorías sin umbral propio). */
    val onThreshold: Float = 0.35f,
    /** Umbrales específicos por categoría (sustituyen a [onThreshold]). Ver [DEFAULT_ON_THRESHOLDS]. */
    val onThresholdByCategory: Map<SoundCategory, Float> = DEFAULT_ON_THRESHOLDS,
    /** Positivas necesarias dentro de las últimas [confirmWindows] ventanas. */
    val confirmRequired: Int = 2,
    val confirmWindows: Int = 3,
    /**
     * Vía rápida: una sola ventana por encima de este valor basta para iniciar el
     * evento, sin esperar al 2 de 3. Solo para las categorías listadas. Ver [DEFAULT_FAST_PATH_THRESHOLDS].
     */
    val fastPathThresholdByCategory: Map<SoundCategory, Float> = DEFAULT_FAST_PATH_THRESHOLDS,
    /** Por debajo de esto una ventana cuenta para cerrar el evento (histéresis). */
    val offThreshold: Float = DEFAULT_OFF_THRESHOLD,
    /** Ventanas seguidas por debajo de [offThreshold] para cerrar un evento (4 × 500 ms = 2 s). */
    val releaseWindows: Int = 4,
    /** Igual para peligro: una sirena que pasa tiene huecos (10 × 500 ms = 5 s). */
    val dangerReleaseWindows: Int = 10,
) {
    fun onThresholdFor(category: SoundCategory): Float = onThresholdByCategory[category] ?: onThreshold

    fun fastPathThresholdFor(category: SoundCategory): Float? = fastPathThresholdByCategory[category]

    init {
        require(confirmRequired in 1..confirmWindows) { "confirmRequired debe estar entre 1 y confirmWindows" }
        require(offThreshold < onThreshold) { "offThreshold debe ser menor que onThreshold" }
        require(SoundCategory.UNKNOWN !in onThresholdByCategory) { "UNKNOWN no tiene umbral: nunca genera eventos" }
        onThresholdByCategory.forEach { (category, threshold) ->
            require(threshold > offThreshold && threshold <= 1f) {
                "Umbral de $category ($threshold) debe estar entre offThreshold ($offThreshold) y 1"
            }
        }
        require(SoundCategory.UNKNOWN !in fastPathThresholdByCategory) { "UNKNOWN no tiene vía rápida" }
        fastPathThresholdByCategory.forEach { (category, threshold) ->
            require(threshold >= onThresholdFor(category) && threshold <= 1f) {
                "Vía rápida de $category ($threshold) debe estar entre su umbral (${onThresholdFor(category)}) y 1"
            }
        }
    }
}

/** Umbral de fin de evento (histéresis) del estabilizador. */
const val DEFAULT_OFF_THRESHOLD = 0.15f

/**
 * Umbrales de inicio por categoría: el único sitio que hay que tocar para calibrar.
 * YAMNet da puntuaciones en pasos de 1/256 (~0,004). Todas las categorías
 * conocidas aparecen aquí (un test lo exige).
 *
 * Estado de calibración:
 *  - [REAL]      con audio real por el micrófono del emulador.
 *  - [PENDIENTE] sin datos reales suficientes: valor conservador provisional.
 * Las categorías nuevas sin datos usan 0,50 (más exigente que el 0,35 general).
 */
val DEFAULT_ON_THRESHOLDS: Map<SoundCategory, Float> = mapOf(
    // [REAL] 0,33–0,80 con sirenas reales por altavoz; además tiene vía rápida.
    SoundCategory.SIREN to 0.35f,
    // [REAL] 0,80 (una ventana) con una alarma de incendio real. Su pitido es intermitente: lo cubre la vía rápida.
    SoundCategory.FIRE_ALARM to 0.35f,
    // [REAL] generó una alerta con un detector de humo real.
    SoundCategory.SMOKE_ALARM to 0.35f,
    // [PENDIENTE] Clase padre "Alarm": se activa junto a otras (0,33–0,41 con sirenas e incendio). Más exigente.
    SoundCategory.GENERAL_ALARM to 0.50f,
    // [REAL] 0,26–1,00 con bocinas reales por altavoz; con 0,35 se perdían las débiles.
    // En voz, música, tecleo, respiración y sirenas reales nunca superó 0,15.
    SoundCategory.CAR_HORN to 0.25f,
    // [PENDIENTE] Clases específicas sin datos reales.
    SoundCategory.CAR_ALARM to 0.50f,
    SoundCategory.TIRE_SKID to 0.50f,
    SoundCategory.REVERSING_VEHICLE to 0.50f,
    SoundCategory.TRAIN_HORN to 0.50f,
    // [PENDIENTE] Se mantiene el valor general hasta tener datos.
    SoundCategory.BICYCLE_BELL to 0.35f,
    // [PENDIENTE] Sonido corto: además tiene vía rápida exigente.
    SoundCategory.GLASS_BREAK to 0.50f,
    // [PENDIENTE] Puede aparecer en juegos, TV o deporte: exigente.
    SoundCategory.SCREAM to 0.50f,
    // [PENDIENTE] Se mantiene el valor general.
    SoundCategory.BABY_CRYING to 0.35f,
    SoundCategory.DOG_BARK to 0.35f,
    // [PENDIENTE] Solo registro (nunca alertan). Clases genéricas: conservador.
    SoundCategory.BELL to 0.50f,
    SoundCategory.WARNING_SIGNAL to 0.50f,
    SoundCategory.DOORBELL to 0.35f,
    // [REAL] golpes reales en la puerta detectados con 0,35.
    SoundCategory.DOOR_KNOCK to 0.35f,
    // [REAL] "Ringtone" 0,92 con un tono de llamada real.
    SoundCategory.PHONE_RING to 0.35f,
    // [PENDIENTE] Se mantiene el valor general.
    SoundCategory.ALARM_CLOCK to 0.35f,
    SoundCategory.WATER_RUNNING to 0.35f,
)

/**
 * Vía rápida (una ventana basta). El 2 de 3 está pensado para sonidos que duran;
 * un sonido de menos de ~1 s puede quedar fuerte en una sola ventana.
 *  - Peligro: 0,60, sin cambios respecto a la versión anterior.
 *  - GLASS_BREAK: sonido corto; 0,70 provisional y exigente para no confundirlo
 *    con golpes o platos. [PENDIENTE] de calibrar.
 * Las demás categorías necesitan 2 de 3.
 */
val DEFAULT_FAST_PATH_THRESHOLDS: Map<SoundCategory, Float> = mapOf(
    SoundCategory.SIREN to 0.60f,
    SoundCategory.FIRE_ALARM to 0.60f,
    SoundCategory.SMOKE_ALARM to 0.60f,
    SoundCategory.GLASS_BREAK to 0.70f,
)
