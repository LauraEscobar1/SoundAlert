package com.soundalert.wear.rules

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.BICYCLE_BELL
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.DOOR_KNOCK
import com.soundalert.wear.classifier.SoundCategory.FIRE_ALARM
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.SMOKE_ALARM
import com.soundalert.wear.classifier.SoundCategory.UNKNOWN
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun priority(category: SoundCategory, context: SoundAlertContext) = engine.match(category, context)?.priority

    @Test
    fun `reglas de CALLE`() {
        assertEquals(Priority.DANGER, priority(SIREN, CALLE))
        assertEquals(Priority.ATTENTION, priority(CAR_HORN, CALLE))
        assertEquals(Priority.ATTENTION, priority(BICYCLE_BELL, CALLE))
    }

    @Test
    fun `reglas de CASA`() {
        assertEquals(Priority.INFORMATION, priority(DOORBELL, CASA))
        assertEquals(Priority.INFORMATION, priority(DOOR_KNOCK, CASA))
        assertEquals(Priority.DANGER, priority(FIRE_ALARM, CASA))
        assertEquals(Priority.DANGER, priority(SMOKE_ALARM, CASA))
    }

    @Test
    fun `la regla devuelve exactamente la categoria, el contexto y la prioridad`() {
        assertEquals(SoundRule(CALLE, SIREN, Priority.DANGER), engine.match(SIREN, CALLE))
        assertEquals(SoundRule(CASA, DOORBELL, Priority.INFORMATION), engine.match(DOORBELL, CASA))
    }

    @Test
    fun `UNKNOWN nunca tiene regla en ningun contexto`() {
        SoundAlertContext.entries.forEach { assertNull(it.name, engine.match(UNKNOWN, it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no se puede configurar una regla para UNKNOWN`() {
        RuleEngine(mapOf(CALLE to mapOf(UNKNOWN to Priority.INFORMATION)))
    }

    @Test
    fun `un sonido no relevante en el contexto no tiene regla (bocina en casa)`() {
        assertNull(engine.match(CAR_HORN, CASA))
        assertNull(engine.match(DOORBELL, CALLE))
    }

    @Test
    fun `los sonidos de peligro son DANGER en todos los contextos`() {
        for (context in SoundAlertContext.entries) {
            for (category in listOf(SIREN, FIRE_ALARM, SMOKE_ALARM)) {
                assertEquals("$category en $context", Priority.DANGER, priority(category, context))
            }
        }
    }

    @Test
    fun `OTRO vigila todas las categorias alertables`() {
        val covered = engine.rulesFor(SoundAlertContext.OTRO).map { it.category }.toSet()
        assertEquals(SoundCategory.ALERTABLE.toSet(), covered)
    }

    @Test
    fun `las categorias de solo registro no tienen regla en ningun contexto`() {
        for (context in SoundAlertContext.entries) {
            assertNull(engine.match(SoundCategory.BELL, context))
            assertNull(engine.match(SoundCategory.WARNING_SIGNAL, context))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no se puede configurar una regla para una categoria de solo registro`() {
        RuleEngine(mapOf(CASA to mapOf(SoundCategory.BELL to Priority.INFORMATION)))
    }

    @Test
    fun `timbre, golpe y campana nunca son peligro`() {
        for (context in SoundAlertContext.entries) {
            for (category in listOf(DOORBELL, DOOR_KNOCK, SoundCategory.BELL, SoundCategory.BICYCLE_BELL)) {
                assertTrue("$category en $context", priority(category, context) != Priority.DANGER)
            }
        }
    }

    /** Matriz completa contexto × categoría. Cualquier cambio de reglas debe reflejarse aquí a propósito. */
    @Test
    fun `matriz completa de reglas`() {
        val D = Priority.DANGER; val A = Priority.ATTENTION; val I = Priority.INFORMATION
        val c = SoundCategory.entries
        fun row(vararg p: Pair<SoundCategory, Priority>) = p.toMap()
        val always = row(SIREN to D, FIRE_ALARM to D, SMOKE_ALARM to D, c.first { it.name == "GENERAL_ALARM" } to A)
        val expected = mapOf(
            CALLE to always + row(
                CAR_HORN to A, BICYCLE_BELL to A, SoundCategory.TIRE_SKID to A, SoundCategory.REVERSING_VEHICLE to A,
                SoundCategory.TRAIN_HORN to A, SoundCategory.CAR_ALARM to A, SoundCategory.GLASS_BREAK to A,
                SoundCategory.SCREAM to A, SoundCategory.DOG_BARK to I,
            ),
            CASA to always + row(
                SoundCategory.GLASS_BREAK to A, SoundCategory.SCREAM to A, SoundCategory.BABY_CRYING to A,
                DOORBELL to I, DOOR_KNOCK to I, SoundCategory.PHONE_RING to I, SoundCategory.ALARM_CLOCK to I,
                SoundCategory.DOG_BARK to I, SoundCategory.WATER_RUNNING to I,
            ),
            SoundAlertContext.TRABAJO to always + row(
                SoundCategory.GLASS_BREAK to A, SoundCategory.SCREAM to A, SoundCategory.REVERSING_VEHICLE to A,
                SoundCategory.PHONE_RING to I, DOOR_KNOCK to I, DOORBELL to I,
            ),
            SoundAlertContext.TRANSPORTE to always + row(
                CAR_HORN to A, SoundCategory.TIRE_SKID to A, SoundCategory.TRAIN_HORN to A,
                SoundCategory.GLASS_BREAK to A, SoundCategory.SCREAM to A, SoundCategory.PHONE_RING to I,
            ),
            SoundAlertContext.OTRO to always + row(
                CAR_HORN to A, SoundCategory.CAR_ALARM to A, SoundCategory.TIRE_SKID to A, SoundCategory.REVERSING_VEHICLE to A,
                SoundCategory.TRAIN_HORN to A, BICYCLE_BELL to A, SoundCategory.GLASS_BREAK to A, SoundCategory.SCREAM to A,
                SoundCategory.BABY_CRYING to A, DOORBELL to I, DOOR_KNOCK to I, SoundCategory.PHONE_RING to I,
                SoundCategory.ALARM_CLOCK to I, SoundCategory.DOG_BARK to I, SoundCategory.WATER_RUNNING to I,
            ),
        )
        for (context in SoundAlertContext.entries) {
            val actual = engine.rulesFor(context).associate { it.category to it.priority }
            assertEquals("Reglas de $context", expected.getValue(context), actual)
        }
    }

    // ---------- Catálogo ampliado ----------

    @Test
    fun `solo sirena, incendio y humo son DANGER - en ningun contexto hay otro DANGER`() {
        val dangers = SoundAlertContext.entries.flatMap { engine.rulesFor(it) }
            .filter { it.priority == Priority.DANGER }.map { it.category }.toSet()
        assertEquals(setOf(SIREN, FIRE_ALARM, SMOKE_ALARM), dangers)
    }

    @Test
    fun `alarma general es ATTENTION en todos los contextos`() {
        SoundAlertContext.entries.forEach { assertEquals(it.name, Priority.ATTENTION, priority(SoundCategory.GENERAL_ALARM, it)) }
    }

    @Test
    fun `vidrio roto y grito son ATTENTION en todos los contextos`() {
        for (context in SoundAlertContext.entries) {
            assertEquals("GLASS_BREAK $context", Priority.ATTENTION, priority(SoundCategory.GLASS_BREAK, context))
            assertEquals("SCREAM $context", Priority.ATTENTION, priority(SoundCategory.SCREAM, context))
        }
    }

    @Test
    fun `transito - solo donde tiene sentido`() {
        // Bocina: calle, transporte, otro. No en casa ni en el trabajo.
        assertEquals(Priority.ATTENTION, priority(CAR_HORN, SoundAlertContext.TRANSPORTE))
        assertNull(engine.match(CAR_HORN, SoundAlertContext.TRABAJO))
        // Derrape: calle y transporte.
        assertEquals(Priority.ATTENTION, priority(SoundCategory.TIRE_SKID, CALLE))
        assertEquals(Priority.ATTENTION, priority(SoundCategory.TIRE_SKID, SoundAlertContext.TRANSPORTE))
        assertNull(engine.match(SoundCategory.TIRE_SKID, CASA))
        // Marcha atrás: calle y trabajo.
        assertEquals(Priority.ATTENTION, priority(SoundCategory.REVERSING_VEHICLE, CALLE))
        assertEquals(Priority.ATTENTION, priority(SoundCategory.REVERSING_VEHICLE, SoundAlertContext.TRABAJO))
        assertNull(engine.match(SoundCategory.REVERSING_VEHICLE, CASA))
        // Bocina de tren: calle y transporte.
        assertEquals(Priority.ATTENTION, priority(SoundCategory.TRAIN_HORN, SoundAlertContext.TRANSPORTE))
        assertNull(engine.match(SoundCategory.TRAIN_HORN, CASA))
        // Alarma de coche: calle, no casa (suele ser un coche ajeno).
        assertEquals(Priority.ATTENTION, priority(SoundCategory.CAR_ALARM, CALLE))
        assertNull(engine.match(SoundCategory.CAR_ALARM, CASA))
    }

    @Test
    fun `hogar - informacion en casa`() {
        for (category in listOf(DOORBELL, DOOR_KNOCK, SoundCategory.PHONE_RING, SoundCategory.ALARM_CLOCK, SoundCategory.WATER_RUNNING)) {
            assertEquals(category.name, Priority.INFORMATION, priority(category, CASA))
        }
        assertEquals(Priority.ATTENTION, priority(SoundCategory.BABY_CRYING, CASA))
    }

    @Test
    fun `cada regla es de una categoria que el LabelMapper puede producir`() {
        val mappable = com.soundalert.wear.classifier.YAMNET_CATEGORY_LABELS.keys
        SoundAlertContext.entries.flatMap { engine.rulesFor(it) }.forEach {
            assertTrue("${it.category} tiene regla pero ninguna clase YAMNet", it.category in mappable)
        }
    }

    @Test
    fun `consultar reglas no las modifica`() {
        val before = SoundAlertContext.entries.associateWith { engine.rulesFor(it) }
        repeat(100) { engine.match(CAR_HORN, CALLE); engine.match(UNKNOWN, CASA) }
        assertTrue(SoundAlertContext.entries.all { engine.rulesFor(it) == before[it] })
    }
}
