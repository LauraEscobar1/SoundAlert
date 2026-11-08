package com.soundalert.wear.vibration

import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternTest {

    @Test
    fun `DANGER son 3 pulsos largos con la amplitud por defecto (sin cambios)`() {
        val p = VibrationPattern.forPriority(Priority.DANGER)
        assertEquals("3_LONG", p.name)
        assertEquals(3, p.pulses)
        assertArrayEquals(longArrayOf(0, 600, 250, 600, 250, 600), p.timings)
        assertArrayEquals(intArrayOf(0, -1, 0, -1, 0, -1), p.amplitudes)
        assertTrue(p.usesDefaultAmplitude)
    }

    @Test
    fun `ATTENTION son 2 pulsos de 400 ms con amplitud alta`() {
        val p = VibrationPattern.forPriority(Priority.ATTENTION)
        assertEquals("2_MEDIUM", p.name)
        assertEquals(2, p.pulses)
        assertArrayEquals(longArrayOf(0, 400, 250, 400), p.timings)
        assertArrayEquals(intArrayOf(0, 200, 0, 200), p.amplitudes)
        assertFalse(p.usesDefaultAmplitude)
    }

    @Test
    fun `INFORMATION es 1 pulso suave de 300 ms con amplitud media`() {
        val p = VibrationPattern.forPriority(Priority.INFORMATION)
        assertEquals("1_SHORT", p.name)
        assertEquals(1, p.pulses)
        assertArrayEquals(longArrayOf(0, 300), p.timings)
        assertArrayEquals(intArrayOf(0, 140), p.amplitudes)
        assertFalse(p.usesDefaultAmplitude)
    }

    @Test
    fun `INFORMATION nunca es un toque corto que no se sienta`() {
        assertTrue(VibrationPattern.forPriority(Priority.INFORMATION).pulseMs >= 300)
    }

    @Test
    fun `largo mayor que medio mayor que corto`() {
        val (d, a, i) = Priority.entries.map { VibrationPattern.forPriority(it).pulseMs }
        assertTrue(d > a && a > i)
    }

    @Test
    fun `ATTENTION vibra más fuerte que INFORMATION`() {
        val attention = VibrationPattern.forPriority(Priority.ATTENTION).pulseAmplitude
        val information = VibrationPattern.forPriority(Priority.INFORMATION).pulseAmplitude
        assertTrue(attention > information)
    }

    @Test
    fun `timings y amplitudes tienen la misma longitud y las pausas van a 0`() {
        Priority.entries.forEach { priority ->
            val p = VibrationPattern.forPriority(priority)
            assertEquals(priority.name, p.timings.size, p.amplitudes.size)
            p.amplitudes.forEachIndexed { i, amp ->
                if (i % 2 == 0) assertEquals("$priority índice $i", 0, amp) else assertTrue(amp != 0)
            }
        }
    }

    @Test
    fun `las duraciones y amplitudes son configurables`() {
        val p = VibrationPattern.forPriority(
            Priority.ATTENTION,
            AlertConfig(mediumPulseMs = 450, pulseGapMs = 100, attentionAmplitude = 220),
        )
        assertArrayEquals(longArrayOf(0, 450, 100, 450), p.timings)
        assertArrayEquals(intArrayOf(0, 220, 0, 220), p.amplitudes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una amplitud fuera de 1 a 255 se rechaza`() {
        AlertConfig(informationAmplitude = 0)
    }
}
