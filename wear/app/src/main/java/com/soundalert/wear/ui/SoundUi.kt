package com.soundalert.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.soundalert.wear.R
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.ALARM_CLOCK
import com.soundalert.wear.classifier.SoundCategory.BABY_CRYING
import com.soundalert.wear.classifier.SoundCategory.BELL
import com.soundalert.wear.classifier.SoundCategory.BICYCLE_BELL
import com.soundalert.wear.classifier.SoundCategory.CAR_ALARM
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOG_BARK
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.DOOR_KNOCK
import com.soundalert.wear.classifier.SoundCategory.FIRE_ALARM
import com.soundalert.wear.classifier.SoundCategory.GENERAL_ALARM
import com.soundalert.wear.classifier.SoundCategory.GLASS_BREAK
import com.soundalert.wear.classifier.SoundCategory.PHONE_RING
import com.soundalert.wear.classifier.SoundCategory.REVERSING_VEHICLE
import com.soundalert.wear.classifier.SoundCategory.SCREAM
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.SMOKE_ALARM
import com.soundalert.wear.classifier.SoundCategory.TIRE_SKID
import com.soundalert.wear.classifier.SoundCategory.TRAIN_HORN
import com.soundalert.wear.classifier.SoundCategory.UNKNOWN
import com.soundalert.wear.classifier.SoundCategory.WARNING_SIGNAL
import com.soundalert.wear.classifier.SoundCategory.WATER_RUNNING
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.theme.SaColors

/**
 * Cómo se presenta cada categoría. Solo texto e iconos: la categoría, la prioridad y
 * si alerta los deciden el clasificador y las reglas.
 */
object SoundUi {

    /** Nombre del catálogo, igual que GET /catalog/sounds del backend ("Bocina de vehículo"). */
    fun name(category: SoundCategory): String = when (category) {
        SIREN -> "Sirena"
        FIRE_ALARM -> "Alarma de incendio"
        SMOKE_ALARM -> "Detector de humo"
        GENERAL_ALARM -> "Alarma"
        CAR_HORN -> "Bocina de vehículo"
        CAR_ALARM -> "Alarma de coche"
        TIRE_SKID -> "Frenazo"
        REVERSING_VEHICLE -> "Vehículo en marcha atrás"
        TRAIN_HORN -> "Bocina de tren"
        BICYCLE_BELL -> "Timbre de bicicleta"
        GLASS_BREAK -> "Vidrio roto"
        SCREAM -> "Grito"
        BABY_CRYING -> "Bebé llorando"
        DOG_BARK -> "Perro ladrando"
        BELL -> "Campana"
        WARNING_SIGNAL -> "Pitido"
        DOORBELL -> "Timbre de puerta"
        DOOR_KNOCK -> "Golpe en la puerta"
        PHONE_RING -> "Teléfono sonando"
        ALARM_CLOCK -> "Despertador"
        WATER_RUNNING -> "Agua corriendo"
        UNKNOWN -> "Sonido"
    }

    /** Nombre corto de las listas e historial, como en los mockups ("Bocina", "Timbre"). */
    fun shortName(category: SoundCategory): String = when (category) {
        SIREN -> "Sirena"
        FIRE_ALARM -> "Incendio"
        SMOKE_ALARM -> "Humo"
        GENERAL_ALARM -> "Alarma"
        CAR_HORN -> "Bocina"
        CAR_ALARM -> "Alarma de coche"
        TIRE_SKID -> "Frenazo"
        REVERSING_VEHICLE -> "Marcha atrás"
        TRAIN_HORN -> "Tren"
        BICYCLE_BELL -> "Bicicleta"
        GLASS_BREAK -> "Vidrio roto"
        SCREAM -> "Grito"
        BABY_CRYING -> "Bebé"
        DOG_BARK -> "Perro"
        BELL -> "Campana"
        WARNING_SIGNAL -> "Pitido"
        DOORBELL -> "Timbre"
        DOOR_KNOCK -> "Puerta"
        PHONE_RING -> "Teléfono"
        ALARM_CLOCK -> "Despertador"
        WATER_RUNNING -> "Agua"
        UNKNOWN -> "Sonido"
    }

    /** Resumen de la pantalla de reposo ("Timbre · Alarma · Bebé"): las alarmas se agrupan. */
    fun summaryName(category: SoundCategory): String = when (category) {
        FIRE_ALARM, SMOKE_ALARM, GENERAL_ALARM -> "Alarma"
        else -> shortName(category)
    }

    /** Titular de la pantalla de alerta. */
    fun alertTitle(category: SoundCategory): String = when (category) {
        SIREN -> "Sirena de emergencia"
        DOORBELL -> "Timbre"
        DOOR_KNOCK -> "Llaman a la puerta"
        else -> name(category)
    }

    /** "Posible sirena", "Posible bebé llorando". */
    fun possible(category: SoundCategory): String = "Posible " + name(category).replaceFirstChar { it.lowercase() }

    @DrawableRes
    fun icon(category: SoundCategory): Int = when (category) {
        SIREN -> R.drawable.ic_sound_siren
        FIRE_ALARM -> R.drawable.ic_sound_fire_alarm
        SMOKE_ALARM -> R.drawable.ic_sound_smoke_alarm
        GENERAL_ALARM -> R.drawable.ic_sound_general_alarm
        CAR_HORN -> R.drawable.ic_sound_car_horn
        CAR_ALARM -> R.drawable.ic_sound_car_alarm
        TIRE_SKID -> R.drawable.ic_sound_tire_skid
        REVERSING_VEHICLE -> R.drawable.ic_sound_reversing_vehicle
        TRAIN_HORN -> R.drawable.ic_sound_train_horn
        BICYCLE_BELL -> R.drawable.ic_sound_bicycle_bell
        GLASS_BREAK -> R.drawable.ic_sound_glass_break
        SCREAM -> R.drawable.ic_sound_scream
        BABY_CRYING -> R.drawable.ic_sound_baby_crying
        DOG_BARK -> R.drawable.ic_sound_dog_bark
        BELL -> R.drawable.ic_sound_bell
        WARNING_SIGNAL -> R.drawable.ic_sound_warning_signal
        DOORBELL -> R.drawable.ic_sound_doorbell
        DOOR_KNOCK -> R.drawable.ic_sound_door_knock
        PHONE_RING -> R.drawable.ic_sound_phone_ring
        ALARM_CLOCK -> R.drawable.ic_sound_alarm_clock
        WATER_RUNNING -> R.drawable.ic_sound_water_running
        UNKNOWN -> R.drawable.ic_sound_unknown
    }
}

@get:DrawableRes
val SoundAlertContext.icon: Int
    get() = when (this) {
        SoundAlertContext.CASA -> R.drawable.ic_context_home
        SoundAlertContext.CALLE -> R.drawable.ic_context_street
        else -> R.drawable.ic_context_other
    }

val Priority.color: Color
    get() = when (this) {
        Priority.DANGER -> SaColors.Danger
        Priority.ATTENTION -> SaColors.Attention
        Priority.INFORMATION -> SaColors.Information
    }

/** Etiqueta de la cabecera de alerta, como en los mockups. */
val Priority.label: String
    get() = when (this) {
        Priority.DANGER -> "PELIGRO"
        Priority.ATTENTION -> "ATENCIÓN"
        Priority.INFORMATION -> "AVISO"
    }

/** Barras encendidas: coinciden con el número de vibraciones (3 / 2 / 1). */
val Priority.bars: Int
    get() = when (this) {
        Priority.DANGER -> 3
        Priority.ATTENTION -> 2
        Priority.INFORMATION -> 1
    }
