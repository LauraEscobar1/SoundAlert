package com.soundalert.wear.classifier

import com.soundalert.wear.config.StabilizerConfig
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.detection.DetectionStabilizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * LabelMapper + DetectionStabilizer juntos, como en AudioPipeline, con el CSV
 * real: las clases sin mapeo no producen eventos ni prolongan uno anterior.
 */
class UnknownSoundFlowTest {

    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val mapper = LabelMapper(labels)
    private val hop = 500L
    private val stabilizer = DetectionStabilizer(StabilizerConfig(), hop)
    private var t = 0L

    private fun window(label: String, score: Float): Pair<WindowClassification, List<DetectionEvent>> {
        val scores = FloatArray(labels.size).also { it[labels.indexOf(label)] = score }
        val result = mapper.classify(scores)
        // Igual que AudioPipeline: al estabilizador solo llegan las categorías conocidas.
        return (result to stabilizer.update(result.known, t)).also { t += hop }
    }

    @Test
    fun `voz, musica y pasos no generan eventos`() {
        val events = listOf("Speech", "Music", "Walk, footsteps").flatMap { label ->
            (1..6).flatMap { window(label, 0.89f).second }
        }
        assertTrue("No debería haber eventos: $events", events.isEmpty())
    }

    @Test
    fun `tras una sirena, la musica no conserva SIREN y la sirena termina`() {
        val (siren, started) = window("Police car (siren)", 0.9f)
        assertEquals(SoundCategory.SIREN, siren.topCategory)
        assertEquals(SoundCategory.SIREN, (started.single() as DetectionEvent.Started).category)

        val music = (1..10).map { window("Music", 0.89f) }
        music.forEach { (result, _) ->
            assertEquals(SoundCategory.UNKNOWN, result.topCategory)
            assertTrue(result.known.isEmpty())
        }
        val events = music.flatMap { it.second }
        assertTrue(events.single() is DetectionEvent.Ended)
        assertEquals(SoundCategory.SIREN, events.single().category)
    }

    @Test
    fun `un golpe en la puerta sigue detectandose`() {
        window("Knock", 0.6f)
        val (result, events) = window("Knock", 0.6f)
        assertEquals(SoundCategory.DOOR_KNOCK, result.topCategory)
        assertEquals(SoundCategory.DOOR_KNOCK, (events.single() as DetectionEvent.Started).category)
    }
}
