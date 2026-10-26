package com.soundalert.wear.ui.sounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class HoneycombTest {

    private val slots = honeycombSlots(13, HEX_RADIUS, HEX_GAP)

    @Test
    fun `panal continuo de filas alternas de 2 y 3, centrado`() {
        assertEquals(13, slots.size)
        assertEquals(listOf(2, 3, 2, 3, 2, 1), slots.groupBy { it.y }.toSortedMap().values.map { it.size })
        assertEquals(0f, slots.first().y)
        for (row in slots.groupBy { it.y }.values.filter { it.size > 1 }) assertEquals(0f, row.sumOf { it.x.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `celdas un 10 por ciento mayores que el mockup con centros vecinos a 44`() {
        assertEquals(43.2f * 1.1f, HEX_RADIUS * 2, 0.01f)
        assertEquals(41.2f, HEX_RADIUS * sqrt(3f), 0.05f)
        assertEquals(3f, HEX_GAP)
        // El toque va a la celda más cercana: la zona de cada una mide, entre lados
        // opuestos, la distancia a su vecina más próxima.
        for (a in slots.indices) {
            val nearest = slots.indices.filter { it != a }.minOf { b -> hypot(slots[a].x - slots[b].x, slots[a].y - slots[b].y) }
            assertEquals("celda $a", 44.2f, nearest, 0.1f)
        }
    }

    @Test
    fun `las celdas no se tocan - entre vecinas queda el hueco`() {
        val minDistance = HEX_RADIUS * sqrt(3f) + HEX_GAP
        for (a in slots.indices) for (b in a + 1 until slots.size) {
            val d = hypot(slots[a].x - slots[b].x, slots[a].y - slots[b].y)
            assertTrue("$a-$b a $d", d >= minDistance - 0.01f)
        }
    }

    @Test
    fun `las filas de 3 caben a lo ancho en el margen de seguridad`() {
        val halfWidth = slots.maxOf { it.x } + HEX_RADIUS * sqrt(3f) / 2
        assertTrue("media anchura $halfWidth", halfWidth < 96 - 19.2f + 2)
    }

    @Test
    fun `ojo de pez - tamano completo en el centro del area de celdas, pequeno y transparente en los bordes`() {
        assertEquals(1f to 1f, fisheye(108f))
        assertEquals(1f to 1f, fisheye(130f))
        val (edgeScale, edgeAlpha) = fisheye(178f)
        assertEquals(0.45f, edgeScale, 0.01f)
        assertEquals(0f, edgeAlpha, 0.01f)
        val (midScale, midAlpha) = fisheye(60f)
        assertTrue(midScale in 0.45f..1f && midAlpha in 0f..1f)
    }
}
