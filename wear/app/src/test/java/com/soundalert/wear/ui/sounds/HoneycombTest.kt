package com.soundalert.wear.ui.sounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class HoneycombTest {

    private val slots = honeycombSlots(HEX_RADIUS, HEX_GAP)

    @Test
    fun `panal de 2 - 3 - 2 centrado`() {
        assertEquals(7, slots.size)
        assertEquals(listOf(2, 3, 2), slots.groupBy { it.y }.toSortedMap().values.map { it.size })
        assertEquals(0f, slots.sumOf { it.x.toDouble() }.toFloat(), 0.001f)
        assertEquals(0f, slots.sumOf { it.y.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `las celdas no se tocan - entre vecinas queda el hueco`() {
        // Hexágonos con la punta arriba: el apotema es el radio · √3 / 2.
        val minDistance = HEX_RADIUS * sqrt(3f) + HEX_GAP
        for (a in slots.indices) for (b in a + 1 until slots.size) {
            val d = hypot(slots[a].x - slots[b].x, slots[a].y - slots[b].y)
            assertTrue("$a-$b a $d", d >= minDistance - 0.01f)
        }
    }

    @Test
    fun `medidas del mockup - celdas de 37 x 43 con centros vecinos a 40`() {
        assertEquals(43.2f, HEX_RADIUS * 2, 0.01f)
        assertEquals(37.4f, HEX_RADIUS * sqrt(3f), 0.05f)
        // El toque va a la celda más cercana: la zona de cada una mide, entre lados
        // opuestos, la distancia a su vecina más próxima.
        for (a in slots.indices) {
            val nearest = slots.indices.filter { it != a }.minOf { b -> hypot(slots[a].x - slots[b].x, slots[a].y - slots[b].y) }
            assertEquals("celda $a", 40.2f, nearest, 0.1f)
        }
    }

    @Test
    fun `todo el panal cabe en la esfera de 192`() {
        for (slot in slots) {
            // Vértices de cada hexágono (con el desplazamiento vertical del encabezado).
            for (k in 0 until 6) {
                val angle = Math.toRadians(-90.0 + 60 * k)
                val x = slot.x + HEX_RADIUS * Math.cos(angle)
                val y = slot.y + 2.6 + HEX_RADIUS * Math.sin(angle)
                // Dentro del margen de seguridad (~10 % del diámetro).
                assertTrue("vértice fuera: $x, $y", hypot(x, y) < 96 - 19.2)
            }
        }
    }
}
