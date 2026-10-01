package com.soundalert.wear.vibration

import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternTest {

    @Test
    fun `DANGER son 3 pulsos largos`() {
        val p = VibrationPattern.forPriority(Priority.DANGER)
        assertEquals("3_LONG", p.name)
        assertEquals(3, p.pulses)
        assertArrayEquals(longArrayOf(0, 600, 250, 600, 250, 600), p.timings)
    }

    @Test
    fun `ATTENTION son 2 pulsos medios`() {
        val p = VibrationPattern.forPriority(Priority.ATTENTION)
        assertEquals("2_MEDIUM", p.name)
        assertArrayEquals(longArrayOf(0, 350, 250, 350), p.timings)
    }

    @Test
    fun `INFORMATION es 1 pulso corto`() {
        val p = VibrationPattern.forPriority(Priority.INFORMATION)
        assertEquals("1_SHORT", p.name)
        assertArrayEquals(longArrayOf(0, 150), p.timings)
    }

    @Test
    fun `largo mayor que medio mayor que corto`() {
        val (d, a, i) = Priority.entries.map { VibrationPattern.forPriority(it).pulseMs }
        assertTrue(d > a && a > i)
    }

    @Test
    fun `las duraciones son configurables`() {
        val p = VibrationPattern.forPriority(Priority.ATTENTION, AlertConfig(mediumPulseMs = 400, pulseGapMs = 100))
        assertArrayEquals(longArrayOf(0, 400, 100, 400), p.timings)
    }
}
