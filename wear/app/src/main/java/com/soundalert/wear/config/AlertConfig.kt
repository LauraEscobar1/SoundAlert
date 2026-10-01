package com.soundalert.wear.config

/**
 * Comportamiento de las alertas. Valores iniciales configurables.
 * La duración de los pulsos sigue el mockup (largas / medias / corta); el
 * número de pulsos (3/2/1) coincide con `vibrationCount` del backend.
 */
data class AlertConfig(
    /** ATTENTION se cierra sola (EXPIRED) tras este tiempo. */
    val attentionTimeoutMs: Long = 10_000,
    /** INFORMATION se cierra sola (EXPIRED) tras este tiempo. */
    val informationTimeoutMs: Long = 5_000,
    /**
     * Tiempo mínimo entre dos alertas de la misma categoría. Evita vibrar otra vez
     * cuando un sonido se corta y vuelve enseguida. Igual que ALERT_COOLDOWN_SECONDS del backend.
     */
    val cooldownMs: Long = 10_000,
    /**
     * Mientras una alerta DANGER siga ACTIVE (sin confirmar), su vibración se repite
     * cada este tiempo, aunque el sonido ya haya terminado. 0 = no repetir.
     * 15 s: el patrón dura 2,3 s (3 × 600 ms + 2 × 250 ms), así quedan ~13 s sin
     * vibrar entre repeticiones, y nunca es más frecuente que el [cooldownMs] (10 s)
     * con el que una alerta nueva de la misma categoría podría volver a vibrar.
     */
    val dangerRepeatIntervalMs: Long = 15_000,
    /** Alertas que se conservan en el historial en memoria. */
    val historySize: Int = 50,
    val longPulseMs: Long = 600,
    val mediumPulseMs: Long = 350,
    val shortPulseMs: Long = 150,
    val pulseGapMs: Long = 250,
) {
    init {
        require(dangerRepeatIntervalMs >= 0) { "dangerRepeatIntervalMs no puede ser negativo" }
    }
}
