package com.soundalert.wear.detection

import com.soundalert.wear.classifier.CategoryScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.config.DEFAULT_FAST_PATH_THRESHOLDS
import com.soundalert.wear.config.DEFAULT_ON_THRESHOLDS
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

    // ---------- Umbrales por categoría (DEFAULT_ON_THRESHOLDS) ----------

    @Test
    fun `tabla de umbrales revisada`() {
        assertEquals(
            mapOf(
                SIREN to 0.35f,
                SoundCategory.FIRE_ALARM to 0.35f,
                SoundCategory.SMOKE_ALARM to 0.35f,
                SoundCategory.GENERAL_ALARM to 0.50f,
                CAR_HORN to 0.25f,
                SoundCategory.CAR_ALARM to 0.50f,
                SoundCategory.TIRE_SKID to 0.50f,
                SoundCategory.REVERSING_VEHICLE to 0.50f,
                SoundCategory.TRAIN_HORN to 0.50f,
                SoundCategory.BICYCLE_BELL to 0.35f,
                SoundCategory.GLASS_BREAK to 0.50f,
                SoundCategory.SCREAM to 0.50f,
                SoundCategory.BABY_CRYING to 0.35f,
                SoundCategory.DOG_BARK to 0.35f,
                SoundCategory.BELL to 0.50f,
                SoundCategory.WARNING_SIGNAL to 0.50f,
                DOORBELL to 0.35f,
                SoundCategory.DOOR_KNOCK to 0.35f,
                SoundCategory.PHONE_RING to 0.35f,
                SoundCategory.ALARM_CLOCK to 0.35f,
                SoundCategory.WATER_RUNNING to 0.35f,
            ),
            DEFAULT_ON_THRESHOLDS,
        )
    }

    @Test
    fun `todas las categorias conocidas tienen un umbral explicito`() {
        assertEquals(SoundCategory.KNOWN.toSet(), DEFAULT_ON_THRESHOLDS.keys)
    }

    @Test
    fun `via rapida solo para peligro (0,60) y vidrio roto (0,70)`() {
        assertEquals(
            mapOf(SIREN to 0.60f, SoundCategory.FIRE_ALARM to 0.60f, SoundCategory.SMOKE_ALARM to 0.60f, SoundCategory.GLASS_BREAK to 0.70f),
            DEFAULT_FAST_PATH_THRESHOLDS,
        )
    }

    @Test
    fun `vidrio roto - una ventana fuerte basta, una media no`() {
        val fast = window(SoundCategory.GLASS_BREAK to 0.75f).single() as Started
        assertTrue(fast.fastPath)
        val other = DetectionStabilizer(StabilizerConfig(), hop)
        assertTrue(other.update(listOf(CategoryScore(SoundCategory.GLASS_BREAK, 0.65f, "Shatter")), 0).isEmpty())
    }

    @Test
    fun `vidrio roto con 0,55 en 2 de 3 ventanas tambien inicia evento`() {
        assertTrue(window(SoundCategory.GLASS_BREAK to 0.55f).isEmpty())
        assertTrue(window(SoundCategory.GLASS_BREAK to 0.55f).single() is Started)
    }

    @Test
    fun `categorias nuevas sin datos exigen 0,50 (grito con 0,45 no inicia evento)`() {
        assertTrue((1..10).flatMap { window(SoundCategory.SCREAM to 0.45f) }.isEmpty())
        assertTrue(window(SoundCategory.SCREAM to 0.55f).isEmpty())
        assertTrue(window(SoundCategory.SCREAM to 0.55f).single() is Started)
    }

    @Test
    fun `las categorias de solo registro entran al estabilizador con 2 de 3 y sin via rapida`() {
        assertTrue(window(SoundCategory.WARNING_SIGNAL to 0.95f).isEmpty())
        assertTrue(window(SoundCategory.WARNING_SIGNAL to 0.95f).single() is Started)
        // Campana por debajo de su umbral (0,50): no inicia evento (el fin del pitido sí puede aparecer).
        val bell = (1..10).flatMap { window(SoundCategory.BELL to 0.45f) }
        assertTrue(bell.none { it is Started })
    }

    @Test
    fun `una alarma general alta no tiene via rapida`() {
        assertTrue(window(SoundCategory.GENERAL_ALARM to 0.95f).isEmpty())
    }

    @Test
    fun `vidrio roto usa la histeresis normal (2 s), no la de peligro`() {
        window(SoundCategory.GLASS_BREAK to 0.9f)
        assertTrue((1..3).flatMap { window() }.isEmpty())
        assertTrue(window().single() is Ended)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `la via rapida no puede ser menor que el umbral de la categoria`() {
        StabilizerConfig(fastPathThresholdByCategory = mapOf(SoundCategory.SCREAM to 0.40f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNKNOWN no puede tener via rapida`() {
        StabilizerConfig(fastPathThresholdByCategory = mapOf(SoundCategory.UNKNOWN to 0.9f))
    }

    @Test
    fun `CAR_HORN con 0,30 en 2 de 3 ventanas inicia evento`() {
        window(CAR_HORN to 0.30f)
        assertEquals(CAR_HORN, (window(CAR_HORN to 0.30f).single() as Started).category)
    }

    @Test
    fun `CAR_HORN por debajo de su umbral no inicia evento`() {
        assertTrue((1..10).flatMap { window(CAR_HORN to 0.24f) }.isEmpty())
    }

    @Test
    fun `SIREN mantiene 0,35 - con 0,30 no inicia evento`() {
        assertTrue((1..10).flatMap { window(SIREN to 0.30f) }.isEmpty())
        assertEquals(1, (1..2).flatMap { window(SIREN to 0.36f) }.size)
    }

    @Test
    fun `bajar CAR_HORN no baja las demas categorias`() {
        assertTrue((1..10).flatMap { window(DOORBELL to 0.30f) }.isEmpty())
    }

    @Test
    fun `CAR_HORN conserva la histeresis y el 2 de 3`() {
        assertTrue(window(CAR_HORN to 0.30f).isEmpty()) // una ventana sola no basta
        assertTrue(window(CAR_HORN to 0.30f).single() is Started)
        assertTrue((1..3).flatMap { window() }.isEmpty()) // 1,5 s de silencio: aún no termina
        assertTrue(window().single() is Ended)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un umbral por categoria no puede ser menor o igual que offThreshold`() {
        StabilizerConfig(onThresholdByCategory = mapOf(CAR_HORN to 0.15f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNKNOWN no puede tener umbral`() {
        StabilizerConfig(onThresholdByCategory = mapOf(SoundCategory.UNKNOWN to 0.5f))
    }

    @Test
    fun `umbral especifico por categoria`() {
        val strict = DetectionStabilizer(StabilizerConfig(onThresholdByCategory = mapOf(DOORBELL to 0.8f)), hop)
        val w = listOf(CategoryScore(DOORBELL, 0.6f, "Doorbell"))
        assertTrue(strict.update(w, 0).isEmpty())
        assertTrue(strict.update(w, 500).isEmpty())
    }
}
