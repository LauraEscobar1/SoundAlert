package com.soundalert.wear.ui

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.vibration.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundUiTest {

    /** Nombres de GET /catalog/sounds (backend en Railway, 2026-10-07) para las categorías del reloj. */
    private val backendNames = mapOf(
        SoundCategory.SIREN to "Sirena",
        SoundCategory.FIRE_ALARM to "Alarma de incendio",
        SoundCategory.SMOKE_ALARM to "Detector de humo",
        SoundCategory.GENERAL_ALARM to "Alarma",
        SoundCategory.CAR_HORN to "Bocina de vehículo",
        SoundCategory.CAR_ALARM to "Alarma de coche",
        SoundCategory.TIRE_SKID to "Frenazo",
        SoundCategory.REVERSING_VEHICLE to "Vehículo en marcha atrás",
        SoundCategory.TRAIN_HORN to "Bocina de tren",
        SoundCategory.BICYCLE_BELL to "Timbre de bicicleta",
        SoundCategory.GLASS_BREAK to "Vidrio roto",
        SoundCategory.SCREAM to "Grito",
        SoundCategory.BABY_CRYING to "Bebé llorando",
        SoundCategory.DOG_BARK to "Perro ladrando",
        SoundCategory.BELL to "Campana",
        SoundCategory.WARNING_SIGNAL to "Pitido",
        SoundCategory.DOORBELL to "Timbre de puerta",
        SoundCategory.DOOR_KNOCK to "Golpe en la puerta",
        SoundCategory.PHONE_RING to "Teléfono sonando",
        SoundCategory.ALARM_CLOCK to "Despertador",
        SoundCategory.WATER_RUNNING to "Agua corriendo",
    )

    @Test
    fun `los nombres coinciden con el catalogo del backend`() {
        assertEquals(SoundCategory.KNOWN.toSet(), backendNames.keys)
        for ((category, name) in backendNames) assertEquals(category.name, name, SoundUi.name(category))
    }

    @Test
    fun `cada categoria tiene su propio icono`() {
        val icons = SoundCategory.entries.map { SoundUi.icon(it) }
        assertEquals(icons.size, icons.distinct().size)
        assertEquals(SoundAlertContext.ACTIVE.size, SoundAlertContext.ACTIVE.map { it.icon }.distinct().size)
    }

    @Test
    fun `las barras de prioridad coinciden con el numero de vibraciones`() {
        for (priority in Priority.entries) {
            assertEquals(priority.name, VibrationPattern.forPriority(priority).pulses, priority.bars)
        }
        assertEquals(listOf("PELIGRO", "ATENCIÓN", "AVISO"), Priority.entries.map { it.label })
    }

    @Test
    fun `textos de la pantalla B y de alerta como en los mockups`() {
        assertEquals("Posible sirena", SoundUi.possible(SoundCategory.SIREN))
        assertEquals("Sirena de emergencia", SoundUi.alertTitle(SoundCategory.SIREN))
        assertEquals("Alarma de incendio", SoundUi.alertTitle(SoundCategory.FIRE_ALARM))
        assertEquals("Bocina de vehículo", SoundUi.alertTitle(SoundCategory.CAR_HORN))
        assertEquals("Timbre", SoundUi.alertTitle(SoundCategory.DOORBELL))
    }
}
