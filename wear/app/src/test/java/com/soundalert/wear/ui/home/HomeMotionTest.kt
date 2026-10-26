package com.soundalert.wear.ui.home

import com.soundalert.wear.ui.components.ANALYZING_BARS
import com.soundalert.wear.ui.components.ARC_LENGTH
import com.soundalert.wear.ui.components.CONTEXT_CHIP_HEIGHT
import com.soundalert.wear.ui.components.JarvisClock
import com.soundalert.wear.ui.components.LISTENING_BARS
import com.soundalert.wear.ui.components.arcStrokes
import com.soundalert.wear.ui.components.pingPong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** Medidas y movimientos del inicio rediseñado (dp sobre 192, viewBox de 200 del Jarvis). */
class HomeMotionTest {

    @Test
    fun `columna centrada sin nada encimado y el Jarvis dentro de la zona segura`() {
        val chipTop = CHIP_Y - CONTEXT_CHIP_HEIGHT / 2
        val chipBottom = CHIP_Y + CONTEXT_CHIP_HEIGHT / 2
        val jarvisTop = JARVIS_Y - JARVIS / 2
        val jarvisBottom = JARVIS_Y + JARVIS / 2
        // Cifras de la hora (9 sp): de CLOCK_Y − 3 a CLOCK_Y + 2,5, medidas en el emulador.
        assertEquals(22.5f, CLOCK_Y - 3f, 0.5f) // ~22 dp del borde de arriba
        assertEquals(4f, chipTop - (CLOCK_Y + 2.5f), 0.6f)
        assertEquals(7f, jarvisTop - chipBottom, 0.01f)
        assertEquals(0.5f, JARVIS / 192f, 0.01f) // ~50 % del ancho
        // Mayúsculas del título (16 sp): 11 de alto, centradas medio dp por encima de TITLE_Y.
        assertEquals(7f, (TITLE_Y - 0.5f - 5.5f) - jarvisBottom, 0.01f)
        // Analizando: mismo borde de arriba, 88 dp; "Sonido detectado" y "Posible sirena" debajo, sin tocarlo.
        val analyzingBottom = jarvisTop + JARVIS_ANALYZING
        assertEquals(7f, (DETECTED_Y - 0.5f - 5f) - analyzingBottom, 0.01f)
        assertTrue(POSSIBLE_Y - 4f > DETECTED_Y + 5f)
        // Lo más bajo (y≈167) sigue dentro de la zona segura para su ancho (≈ 70 dp).
        assertTrue(hypot(35f, POSSIBLE_Y + 4f - 96f) < 96f - 8f)
        // Las marcas (r 94 + medio grosor, sobre 200) caben con holgura en el círculo de la pantalla.
        val ticks = (94f + 2.5f) / 200f * JARVIS
        assertTrue(hypot(0f, JARVIS_Y - 96f) + ticks < 96f - 8f)
    }

    @Test
    fun `arco con estela del diseño, al escuchar y al analizar`() {
        assertEquals((2 * PI * 82).toFloat(), ARC_LENGTH, 0.05f)
        val listening = arcStrokes(0f)
        assertEquals(listOf(70f, 40f, 30f, 14f), listening.map { it.dash })
        assertEquals(listOf(0f, 44f, 78f, -250f), listening.map { it.offset })
        assertEquals(listOf(3.5f, 3f, 2.5f, 2f), listening.map { it.width })
        assertEquals(listOf(1f, 0.45f, 0.18f, 0.6f), listening.map { it.alpha })
        val analyzing = arcStrokes(1f)
        assertEquals(listOf(110f, 50f, 40f), analyzing.take(3).map { it.dash })
        assertEquals(listOf(0f, 54f, 98f), analyzing.take(3).map { it.offset })
        assertEquals(4f, analyzing[0].width)
        assertEquals(0f, analyzing[3].alpha) // sin destello
        // Guion + hueco = la circunferencia, también a mitad de la transición.
        for (a in listOf(0f, 0.37f, 1f)) arcStrokes(a).forEach { assertEquals(ARC_LENGTH, it.dash + it.gap, 0.001f) }
    }

    @Test
    fun `el arco da una vuelta cada 8 s al escuchar y cada 2,6 s al analizar, y el anillo al reves`() {
        val clock = JarvisClock()
        repeat(480) { clock.advance(1f / 60f, analyzing = 0f) }
        assertTrue(abs(clock.arc) < 0.5f || abs(clock.arc - 360f) < 0.5f)
        assertEquals(-180f, clock.inner, 0.5f) // 16 s por vuelta: media vuelta en 8 s
        val fast = JarvisClock()
        repeat(60) { fast.advance(1f / 60f, analyzing = 1f) }
        assertEquals(360f / 2.6f, fast.arc, 0.5f)
        assertEquals(-90f, fast.inner, 0.5f) // 4 s por vuelta
    }

    @Test
    fun `barras del diseño`() {
        assertEquals(listOf(82f, 90f, 98f, 106f, 114f), LISTENING_BARS.map { it.x })
        assertEquals(listOf(18f, 30f, 40f, 30f, 18f), LISTENING_BARS.map { it.height })
        assertEquals(listOf(-0.5f, -0.25f, 0f, -0.75f, -1f), LISTENING_BARS.map { it.delay })
        assertEquals((0 until 9).map { 66f + 8f * it }, ANALYZING_BARS.map { it.x })
        assertEquals(listOf(12f, 24f, 36f, 48f, 56f, 44f, 30f, 20f, 12f), ANALYZING_BARS.map { it.height })
    }

    @Test
    fun `ida y vuelta suave`() {
        assertEquals(0f, pingPong(0f, 1.2f), 0.001f)
        assertEquals(1f, pingPong(1.2f, 1.2f), 0.001f)
        assertEquals(0f, pingPong(2.4f, 1.2f), 0.001f)
        assertEquals(0.5f, pingPong(0.6f, 1.2f), 0.01f) // curva simétrica
        assertEquals(pingPong(0.7f, 1.2f), pingPong(-0.7f, 1.2f), 0.001f)
    }

    @Test
    fun `onda y brillo de las letras`() {
        assertEquals(0f, titleWave(0f), 0.001f)
        assertEquals(-1.25f, titleWave(0.4f), 0.001f)
        assertEquals(0.25f, titleWave(0.7f), 0.001f)
        assertEquals(0f, titleWave(0.9999f), 0.01f)
        assertEquals(0f, titleShine(0f), 0.001f)
        assertEquals(1f, titleShine(0.5f), 0.001f)
        // Cada letra va un poco después que la anterior: la onda recorre la palabra.
        val rhythm = TitleRhythm.Listening
        assertEquals(cyclePhase(1f, rhythm.waveCycle) - rhythm.waveDelay / rhythm.waveCycle, cyclePhase(1f - rhythm.waveDelay, rhythm.waveCycle), 0.0001f)
        assertEquals(TitleRhythm(2.8f, 0.09f, 3.6f, 0.12f), TitleRhythm.Listening)
        assertEquals(TitleRhythm(1.6f, 0.05f, 2.2f, 0.07f), TitleRhythm.Analyzing)
    }
}
