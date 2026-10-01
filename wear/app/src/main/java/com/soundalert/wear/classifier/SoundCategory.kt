package com.soundalert.wear.classifier

/**
 * Categorías que SoundAlert puede reconocer con YAMNet. Solo existen las que
 * tienen una clase YAMNet específica (ver YAMNET_CATEGORY_LABELS); todo lo demás
 * es [UNKNOWN] y nunca genera eventos.
 *
 * Respecto al backend: SIREN…WATER_RUNNING coinciden en nombre. Faltan en el
 * reloj NAME_CALLED, SCHOOL_BELL y KETTLE_WHISTLE (sin clase fiable en YAMNet),
 * VEHICLE_APPROACHING ("Car passing by" no indica que un vehículo se acerque) y
 * MICROWAVE_BEEP ("Microwave oven" es el aparato funcionando, no su pitido).
 * Son nuevas en el reloj: GENERAL_ALARM, CAR_ALARM, TIRE_SKID, REVERSING_VEHICLE,
 * TRAIN_HORN, GLASS_BREAK y SCREAM.
 *
 * [danger]: peligro en el catálogo; el estabilizador usa una histéresis más
 * larga para estas categorías (una sirena que pasa tiene huecos).
 *
 * [alertable] = false: categoría de SOLO REGISTRO. Se detecta y genera un evento
 * (log), pero nunca alerta ni vibra: el RuleEngine rechaza cualquier regla para
 * ella. Son las clases genéricas BELL y WARNING_SIGNAL, demasiado ambiguas para
 * avisar (campanas de iglesia, pitidos de aparatos…).
 *
 * Voz, música, aplausos y pasos NO son categorías: siguen siendo UNKNOWN.
 */
enum class SoundCategory(val danger: Boolean = false, val alertable: Boolean = true) {
    // Emergencias
    SIREN(danger = true),
    FIRE_ALARM(danger = true),
    SMOKE_ALARM(danger = true),
    GENERAL_ALARM,

    // Tránsito
    /** Bocina de cualquier vehículo: YAMNet no distingue coche, moto o camión. */
    CAR_HORN,
    CAR_ALARM,
    TIRE_SKID,
    REVERSING_VEHICLE,
    TRAIN_HORN,
    BICYCLE_BELL,

    // Personas y entorno
    GLASS_BREAK,
    SCREAM,
    BABY_CRYING,
    DOG_BARK,
    /** Solo registro: campanas genéricas ("Bell", "Church bell"…). */
    BELL(alertable = false),
    /** Solo registro: pitidos y zumbadores genéricos ("Beep, bleep", "Buzzer"). */
    WARNING_SIGNAL(alertable = false),

    // Hogar
    DOORBELL,
    DOOR_KNOCK,
    PHONE_RING,
    ALARM_CLOCK,
    WATER_RUNNING,

    UNKNOWN,
    ;

    val known: Boolean get() = this != UNKNOWN

    companion object {
        /** Las categorías que el pipeline puede detectar (todas menos [UNKNOWN]). */
        val KNOWN: List<SoundCategory> = entries.filter { it.known }

        /**
         * Alarmas con clase específica. En la ontología de YAMNet, "Alarm" se activa
         * junto a ellas, así que GENERAL_ALARM se descarta cuando alguna está presente.
         */
        val SPECIFIC_ALARMS: Set<SoundCategory> =
            setOf(SIREN, FIRE_ALARM, SMOKE_ALARM, CAR_ALARM, ALARM_CLOCK, PHONE_RING, REVERSING_VEHICLE)

        /** Categorías que generan alertas cuando tienen regla (todas las conocidas menos las de solo registro). */
        val ALERTABLE: List<SoundCategory> = KNOWN.filter { it.alertable }

        /**
         * Categoría genérica → categorías específicas que la descartan en la misma
         * ventana. Las clases padre de YAMNet ("Alarm", "Bell", "Beep, bleep") se
         * activan junto a sus hijas: si la específica está presente, se usa solo ella.
         */
        val SUPPRESSED_BY: Map<SoundCategory, Set<SoundCategory>> = mapOf(
            GENERAL_ALARM to SPECIFIC_ALARMS,
            BELL to setOf(BICYCLE_BELL, DOORBELL),
            WARNING_SIGNAL to SPECIFIC_ALARMS + GENERAL_ALARM,
        )
    }
}
