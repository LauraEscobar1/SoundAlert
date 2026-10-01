package com.soundalert.wear.detection

import com.soundalert.wear.classifier.CategoryScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.config.StabilizerConfig
import com.soundalert.wear.detection.DetectionEvent.Ended
import com.soundalert.wear.detection.DetectionEvent.Started
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionStabilizerTest {

    private val hop = 500L
    private val stabilizer = DetectionStabilizer(StabilizerConfig(), hop)
    private var t = 0L

    private fun window(vararg scores: Pair<SoundCategory, Float>): List<DetectionEvent> =
        stabilizer.update(scores.map { (c, s) -> CategoryScore(c, s, c.name) }, t).also { t += hop }

    private fun silence(n: Int) = (1..n).flatMap { window() }

    @Test
    fun `una sola ventana media no basta (golpe aislado)`() {
        assertTrue(window(CAR_HORN to 0.5f).isEmpty())
        assertTrue(silence(5).isEmpty())
    }

    @Test
    fun `dos de tres ventanas confirman el evento con la mejor confianza`() {
        assertTrue(window(CAR_HORN to 0.45f).isEmpty())
        assertTrue(window(CAR_HORN to 0.1f).isEmpty())
        val events = window(CAR_HORN to 0.52f)
        assertEquals(listOf(Started(CAR_HORN, 0.52f, "CAR_HORN", fastPath = false, atMs = 1_000)), events)
    }

    @Test
    fun `peligro alto alerta con una sola ventana (via rapida)`() {
        val events = window(SIREN to 0.7f)
        assertEquals(listOf(Started(SIREN, 0.7f, "SIREN", fastPath = true, atMs = 0)), events)
    }

    @Test
    fun `la via rapida no aplica a categorias que no son de peligro`() {
        assertTrue(window(DOORBELL to 0.9f).isEmpty())
        assertEquals(1, window(DOORBELL to 0.9f).size)
    }

    @Test
    fun `un sonido largo genera un unico evento y un fin con su duracion`() {
        val events = (1..20).flatMap { window(SIREN to 0.65f) } + silence(10)
        assertEquals(2, events.size)
        val end = events[1] as Ended
        assertEquals(SIREN, end.category)
        assertEquals(10_000L, end.durationMs) // 20 ventanas × 500 ms
    }

    @Test
    fun `histeresis - los huecos cortos de una sirena no la cortan`() {
        window(SIREN to 0.7f)
        val events = mutableListOf<DetectionEvent>()
        repeat(3) {
            events += silence(4) // 2 s de hueco (< 5 s de peligro)
            events += window(SIREN to 0.4f)
        }
        assertTrue("no debe haber fin ni nuevo inicio: $events", events.isEmpty())
        val end = silence(10)
        assertEquals(1, end.size)
    }

    @Test
    fun `tras terminar, el mismo sonido vuelve a generar evento`() {
        window(CAR_HORN to 0.5f); window(CAR_HORN to 0.5f)
        val end = silence(4)
        assertTrue(end.single() is Ended)
        window(CAR_HORN to 0.5f)
        assertTrue(window(CAR_HORN to 0.5f).single() is Started)
    }

    @Test
    fun `puntuaciones entre off y on mantienen vivo el evento`() {
        window(DOORBELL to 0.5f); window(DOORBELL to 0.5f)
        val events = (1..10).flatMap { window(DOORBELL to 0.2f) }
        assertTrue(events.isEmpty())
        assertEquals(setOf(DOORBELL), stabilizer.activeCategories)
    }

    @Test
    fun `categorias distintas se siguen por separado`() {
        window(SIREN to 0.4f, CAR_HORN to 0.4f)
        val events = window(SIREN to 0.4f, CAR_HORN to 0.4f)
        assertEquals(setOf(SIREN, CAR_HORN), events.map { it.category }.toSet())
    }

    @Test
    fun `UNKNOWN nunca genera eventos aunque llegue con puntuacion alta`() {
        val unknown = listOf(CategoryScore(SoundCategory.UNKNOWN, 0.95f, "Music"))
        val events = (0 until 10).flatMap { stabilizer.update(unknown, it * hop) }
        assertTrue(events.isEmpty())
        assertTrue(stabilizer.activeCategories.isEmpty())
    }

    @Test
    fun `despues de una sirena, ventanas desconocidas solo cierran la sirena`() {
        assertTrue(window(SIREN to 0.9f).single() is Started)
        val events = (1..10).flatMap { window(SoundCategory.UNKNOWN to 0.89f) }
        assertEquals(1, events.size)
        assertEquals(Ended(SIREN, 0.9f, 500, t - hop), events.single())
        assertTrue(stabilizer.activeCategories.isEmpty())
    }

    @Test
    fun `umbral especifico por categoria`() {
        val strict = DetectionStabilizer(StabilizerConfig(onThresholdByCategory = mapOf(DOORBELL to 0.8f)), hop)
        val w = listOf(CategoryScore(DOORBELL, 0.6f, "Doorbell"))
        assertTrue(strict.update(w, 0).isEmpty())
        assertTrue(strict.update(w, 500).isEmpty())
    }
}
