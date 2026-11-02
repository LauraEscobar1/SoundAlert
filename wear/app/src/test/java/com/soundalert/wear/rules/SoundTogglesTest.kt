package com.soundalert.wear.rules

import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOG_BARK
import com.soundalert.wear.classifier.SoundCategory.FIRE_ALARM
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.SMOKE_ALARM
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTogglesTest {

    private class MemoryStore(val saved: MutableMap<String, Set<String>> = mutableMapOf()) : SoundToggleStore {
        override fun load(): Map<String, Set<String>> = saved.toMap()
        override fun save(context: String, disabled: Set<String>) {
            saved[context] = disabled
        }
    }

    @Test
    fun `un sonido apagado no tiene regla en ese contexto y encenderlo devuelve su prioridad`() {
        val toggles = SoundToggles()
        val engine = RuleEngine(toggles = toggles)
        toggles.setEnabled(CALLE, DOG_BARK, enabled = false)
        assertNull(engine.match(DOG_BARK, CALLE))
        // Solo en ese contexto.
        assertEquals(Priority.INFORMATION, engine.match(DOG_BARK, CASA)?.priority)

        toggles.setEnabled(CALLE, DOG_BARK, enabled = true)
        assertEquals(Priority.INFORMATION, engine.match(DOG_BARK, CALLE)?.priority)
    }

    @Test
    fun `los sonidos de peligro no se pueden apagar`() {
        val store = MemoryStore()
        val toggles = SoundToggles(store)
        val engine = RuleEngine(toggles = toggles)
        for (category in listOf(SIREN, FIRE_ALARM, SMOKE_ALARM)) {
            assertTrue(toggles.isLocked(category))
            assertFalse(toggles.setEnabled(CALLE, category, enabled = false))
            assertTrue(toggles.isEnabled(CALLE, category))
            assertEquals(Priority.DANGER, engine.match(category, CALLE)?.priority)
            assertTrue(engine.isEnabled(CALLE, category))
        }
        // No se guarda nada y encenderlos (ya lo están) no falla.
        assertTrue(store.saved.isEmpty())
        assertTrue(toggles.setEnabled(CALLE, SIREN, enabled = true))
        assertFalse(toggles.isLocked(DOG_BARK))
    }

    @Test
    fun `un peligro guardado como apagado se restaura activo`() {
        val toggles = SoundToggles(MemoryStore(mutableMapOf("CALLE" to setOf("SIREN", "DOG_BARK"))))
        assertEquals(mapOf(CALLE to setOf(DOG_BARK)), toggles.disabled.value)
        assertEquals(Priority.DANGER, RuleEngine(toggles = toggles).match(SIREN, CALLE)?.priority)
    }

    @Test
    fun `una regla de peligro se aplica aunque la tabla de apagados diga lo contrario`() {
        // Regla DANGER para una categoría que no es de peligro: el motor tampoco la deja sin regla.
        val toggles = SoundToggles()
        val engine = RuleEngine(mapOf(CALLE to mapOf(DOG_BARK to Priority.DANGER)), toggles)
        toggles.setEnabled(CALLE, DOG_BARK, enabled = false)
        assertEquals(Priority.DANGER, engine.match(DOG_BARK, CALLE)?.priority)
        assertEquals(listOf(DOG_BARK), engine.rulesFor(CALLE).map { it.category })
    }

    @Test
    fun `rulesFor deja fuera los apagados y availableRules los conserva`() {
        val toggles = SoundToggles()
        val engine = RuleEngine(toggles = toggles)
        toggles.setEnabled(CALLE, CAR_HORN, enabled = false)
        assertFalse(engine.rulesFor(CALLE).any { it.category == CAR_HORN })
        assertTrue(engine.availableRules(CALLE).any { it.category == CAR_HORN })
        assertEquals(engine.availableRules(CALLE).size - 1, engine.rulesFor(CALLE).size)
        assertFalse(engine.isEnabled(CALLE, CAR_HORN))
    }

    @Test
    fun `se guarda por contexto y se restaura al arrancar`() {
        val store = MemoryStore()
        SoundToggles(store).apply {
            setEnabled(CALLE, DOG_BARK, enabled = false)
            setEnabled(CALLE, CAR_HORN, enabled = false)
            setEnabled(CASA, DOG_BARK, enabled = false)
            setEnabled(CASA, DOG_BARK, enabled = true)
        }
        assertEquals(mapOf("CALLE" to setOf("DOG_BARK", "CAR_HORN"), "CASA" to emptySet<String>()), store.saved)

        val restored = SoundToggles(store)
        assertFalse(restored.isEnabled(CALLE, DOG_BARK))
        assertFalse(restored.isEnabled(CALLE, CAR_HORN))
        assertTrue(restored.isEnabled(CASA, DOG_BARK))
    }

    @Test
    fun `lo guardado que ya no existe se ignora`() {
        val restored = SoundToggles(MemoryStore(mutableMapOf("CALLE" to setOf("DOG_BARK", "NO_EXISTE"), "MARTE" to setOf("SIREN"))))
        assertEquals(mapOf(CALLE to setOf(DOG_BARK)), restored.disabled.value)
    }
}
