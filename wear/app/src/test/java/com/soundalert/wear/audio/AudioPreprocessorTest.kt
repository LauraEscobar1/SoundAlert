package com.soundalert.wear.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPreprocessorTest {
    @Test
    fun `convierte PCM16 a float en el rango -1 a 1`() {
        val src = shortArrayOf(Short.MIN_VALUE, -16384, 0, 16384, Short.MAX_VALUE)
        val dest = FloatArray(src.size)
        AudioPreprocessor.toFloat(src, dest)
        assertEquals(-1f, dest[0], 0f)
        assertEquals(-0.5f, dest[1], 0f)
        assertEquals(0f, dest[2], 0f)
        assertEquals(0.5f, dest[3], 0f)
        assertEquals(32767f / 32768f, dest[4], 0f)
        dest.forEach { check(it in -1f..1f) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza tamanos distintos`() {
        AudioPreprocessor.toFloat(ShortArray(3), FloatArray(4))
    }
}
