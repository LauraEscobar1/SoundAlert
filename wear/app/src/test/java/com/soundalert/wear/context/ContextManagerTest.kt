package com.soundalert.wear.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextManagerTest {

    @Test
    fun `el contexto inicial es valido`() {
        val manager = ContextManager()
        assertEquals(SoundAlertContext.OTRO, manager.current)
        assertEquals(manager.current, manager.context.value)
    }

    @Test
    fun `se puede cambiar el contexto`() {
        val manager = ContextManager()
        manager.set(SoundAlertContext.CALLE)
        assertEquals(SoundAlertContext.CALLE, manager.current)
        manager.set(SoundAlertContext.CASA)
        assertEquals(SoundAlertContext.CASA, manager.context.value)
    }

    @Test
    fun `contextos de la v1`() {
        assertEquals(
            listOf("CALLE", "CASA", "TRABAJO", "TRANSPORTE", "OTRO"),
            SoundAlertContext.entries.map { it.name },
        )
    }
}
