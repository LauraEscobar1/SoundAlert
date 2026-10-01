package com.soundalert.wear.classifier

/**
 * Categorías de SoundAlert que el modelo de la v1 puede reconocer.
 * Coinciden con `SoundCategory` del backend, salvo NAME_CALLED, SCHOOL_BELL
 * y KETTLE_WHISTLE, que YAMNet no distingue de forma fiable.
 *
 * [danger]: peligro por defecto en el catálogo del backend; habilita la
 * confirmación rápida (una sola ventana) en el estabilizador.
 *
 * [UNKNOWN]: la clase de YAMNet no tiene categoría de SoundAlert (Speech,
 * Music, Walk…). Igual que en el backend. Nunca genera eventos.
 */
enum class SoundCategory(val danger: Boolean = false) {
    SIREN(danger = true),
    FIRE_ALARM(danger = true),
    SMOKE_ALARM(danger = true),
    CAR_HORN,
    VEHICLE_APPROACHING,
    BICYCLE_BELL,
    DOORBELL,
    DOOR_KNOCK,
    PHONE_RING,
    BABY_CRYING,
    DOG_BARK,
    ALARM_CLOCK,
    MICROWAVE_BEEP,
    WATER_RUNNING,
    UNKNOWN,
    ;

    val known: Boolean get() = this != UNKNOWN

    companion object {
        /** Las categorías que el pipeline puede detectar (todas menos [UNKNOWN]). */
        val KNOWN: List<SoundCategory> = entries.filter { it.known }
    }
}
