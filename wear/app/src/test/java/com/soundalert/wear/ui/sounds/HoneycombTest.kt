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
    fun `la fila de 3 ocupa el 92 por ciento del ancho`() {
        val rowWidth = 2 * slots.maxOf { it.x } + HEX_RADIUS * sqrt(3f)
        assertEquals(0.92f, rowWidth / 192f, 0.01f)
    }

    @Test
    fun `las celdas no se tocan y cada una recibe su zona de toque`() {
        val minDistance = HEX_RADIUS * sqrt(3f) + HEX_GAP
        for (a in slots.indices) {
            val nearest = slots.indices.filter { it != a }.minOf { b -> hypot(slots[a].x - slots[b].x, slots[a].y - slots[b].y) }
            assertEquals("celda $a", minDistance, nearest, 0.1f)
        }
        assertEquals(60.2f, minDistance, 0.1f)
    }

    @Test
    fun `la fila del centro se ve a tamano completo`() {
        // Fila de 3 con su centro en el de la pantalla.
        for (x in listOf(-60.2f, 0f, 60.2f)) assertEquals(1f to 1f, cellLens(x, 96f))
    }

    @Test
    fun `cerca de la curva las celdas se achican hasta caber y se desvanecen`() {
        for (y in listOf(30f, 50f, 140f, 165f)) for (x in listOf(-60.2f, -30.1f, 0f, 30.1f, 60.2f)) {
            val (scale, alpha) = cellLens(x, y)
            val outer = hypot(x, y - 96f) + HEX_RADIUS * scale
            // O cabe entera dentro de la pantalla, o está tan achicada que ya no se ve.
            assertTrue("($x, $y): radio $outer", outer <= 94.01f || alpha == 0f)
        }
        val (edgeScale, edgeAlpha) = cellLens(0f, 180f)
        assertTrue(edgeScale < 0.5f && edgeAlpha == 0f)
    }
}
