package com.soundalert.wear.ui.sounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Geometría de la rueda de sonidos, medida en la referencia (dp sobre 192). */
class WheelTest {

    @Test
    fun `el sonido actual es grande, opaco y esta a la izquierda y centrado en vertical`() {
        val current = wheelSlot(0f)
        assertEquals(0.28f, current.size / 192f, 0.005f) // 28 % del diámetro
        assertEquals(0.37f, current.x / 192f, 0.01f) // centro al 37 % del ancho
        assertEquals(96f, current.y, 0.01f)
        assertEquals(1f, current.alpha)
    }

    @Test
    fun `el anterior y el siguiente son pequenos, al 50 por ciento y sobre la curva`() {
        for (offset in listOf(-1f, 1f)) {
            val slot = wheelSlot(offset)
            assertEquals(0.13f, slot.size / 192f, 0.005f) // 13 % del diámetro
            assertEquals(0.5f, slot.alpha, 0.001f)
            assertEquals(84.3f, slot.x, 0.3f)
        }
        assertEquals(42f, wheelSlot(-1f).y, 0.3f)
        assertEquals(150f, wheelSlot(1f).y, 0.3f)
    }

    @Test
    fun `al girar, cada sonido viaja por la curva y crece o se achica sin saltos`() {
        var previous = wheelSlot(-1.6f)
        var k = -1.6f
        while (k <= 1.6f) {
            val slot = wheelSlot(k)
            // Siempre sobre la circunferencia de la rueda.
            assertEquals(WHEEL_R, hypot(WHEEL_CX - slot.x, WHEEL_CY - slot.y), 0.01f)
            assertTrue("tamaño sin saltos en $k", abs(slot.size - previous.size) < 2f)
            previous = slot
            k += 0.05f
        }
        // Más allá del vecino, se desvanece.
        assertEquals(0f, wheelSlot(1.6f).alpha, 0.001f)
        assertEquals(0f, wheelSlot(-1.7f).alpha, 0.001f)
    }

    @Test
    fun `la curva empieza bajo el encabezado, acaba en la zona segura y no llega a la columna de informacion`() {
        // El texto del encabezado acaba en y≈24; la curva empieza 6 más abajo.
        assertEquals(CURVE_TOP, wheelPoint(-CURVE_DEG).second, 0.01f)
        assertEquals(192f - CURVE_TOP, wheelPoint(CURVE_DEG).second, 0.01f)
        var angle = -CURVE_DEG
        while (angle <= CURVE_DEG) {
            val (x, y) = wheelPoint(angle)
            assertTrue("dentro de la zona segura en $angle°", hypot(x - 96f, y - 96f) <= 96f - 12f)
            assertTrue("lejos de la columna de información en $angle°", x < 100f)
            angle += 1f
        }
    }

    @Test
    fun `la rueda encaja en el sonido mas cercano y la inercia la empuja`() {
        assertEquals(2, wheelTarget(position = 2.2f, velocity = 0f, count = 10))
        assertEquals(3, wheelTarget(position = 2.6f, velocity = 0f, count = 10))
        // Un giro rápido hacia adelante avanza más de un sonido; hacia atrás, retrocede.
        assertEquals(5, wheelTarget(position = 2.3f, velocity = 15f, count = 10))
        assertEquals(0, wheelTarget(position = 2.3f, velocity = -15f, count = 10))
        // Nunca sale de la lista.
        assertEquals(9, wheelTarget(position = 8.8f, velocity = 40f, count = 10))
        assertEquals(0, wheelTarget(position = 0.2f, velocity = -40f, count = 10))
    }
}
