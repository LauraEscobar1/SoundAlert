package com.soundalert.wear.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRingBufferTest {

    private fun seq(from: Int, n: Int) = ShortArray(n) { (from + it).toShort() }

    @Test
    fun `no hay ventana hasta tener suficientes muestras`() {
        val ring = AudioRingBuffer(10)
        ring.write(seq(0, 5))
        assertFalse(ring.copyLatest(ShortArray(6)))
        assertTrue(ring.copyLatest(ShortArray(5)))
    }

    @Test
    fun `devuelve las ultimas muestras en orden al dar la vuelta`() {
        val ring = AudioRingBuffer(10)
        ring.write(seq(0, 8))
        ring.write(seq(8, 7)) // escribió 0..14; quedan 5..14
        val out = ShortArray(10)
        assertTrue(ring.copyLatest(out))
        assertArrayEquals(seq(5, 10), out)
        assertEquals(15, ring.totalWritten)
    }

    @Test
    fun `un bloque mayor que la capacidad conserva solo el final`() {
        val ring = AudioRingBuffer(4)
        ring.write(seq(0, 10))
        val out = ShortArray(4)
        assertTrue(ring.copyLatest(out))
        assertArrayEquals(seq(6, 4), out)
    }

    @Test
    fun `ventanas solapadas con la configuracion real de YAMNet`() {
        val ring = AudioRingBuffer(16_000)
        val window = ShortArray(15_600)
        ring.write(seq(0, 8_000))
        assertFalse(ring.copyLatest(window)) // 0,5 s: aún no hay ventana completa
        ring.write(seq(8_000, 8_000))
        assertTrue(ring.copyLatest(window))
        assertEquals(400.toShort(), window.first()) // 16 000 - 15 600
        ring.write(seq(16_000, 8_000)) // salto de 500 ms
        assertTrue(ring.copyLatest(window))
        assertEquals(8_400.toShort(), window.first())
        assertEquals(23_999.toShort(), window.last())
    }
}
