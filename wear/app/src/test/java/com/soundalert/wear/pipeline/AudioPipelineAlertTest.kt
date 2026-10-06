package com.soundalert.wear.pipeline

import com.soundalert.wear.alert.AlertHarness
import com.soundalert.wear.alert.FakeAlertVibrator
import com.soundalert.wear.audio.AudioSource
import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.LabelMapper
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundClassifier
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AudioPipeline real (buffer, puerta, mapeo, estabilizador) con una fuente y
 * un clasificador falsos, conectado al AlertManager por `onEvent` como en
 * ListeningService. Cambia el contexto en mitad de la escucha sin reiniciar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPipelineAlertTest {

    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val config = PipelineConfig()

    private enum class Block { QUIET, HORN, MUSIC }

    /**
     * Cada bloque es un nivel constante; el clasificador falso lo reconoce por su
     * amplitud. Como el micrófono real, entrega un bloque cada 500 ms (tiempo virtual).
     */
    private class ScriptedSource(private val script: List<Block>, private val beforeBlock: (Int) -> Unit) : AudioSource {
        override val sampleRate = 16_000
        override fun blocks(blockSamples: Int): Flow<ShortArray> = flow {
            script.forEachIndexed { i, block ->
                delay(blockSamples * 1_000L / sampleRate)
                beforeBlock(i)
                val value: Short = when (block) {
                    Block.QUIET -> 10
                    Block.HORN -> 8_000
                    Block.MUSIC -> 6_000
                }
                emit(ShortArray(blockSamples) { value })
            }
        }
    }

    private inner class AmplitudeClassifier : SoundClassifier {
        var calls = 0
        override val info = ClassifierInfo("fake", "1")
        override val inputSamples = 15_600
        override fun classify(waveform: FloatArray): FloatArray {
            calls++
            val label = when (waveform.last()) {
                in 0.22f..0.3f -> "Vehicle horn, car horn, honking"
                in 0.15f..0.2f -> "Music"
                else -> "Silence"
            }
            return FloatArray(labels.size).also { it[labels.indexOf(label)] = 0.8f }
        }
        override fun close() {}
    }

    @Test
    fun `el contexto cambia en mitad de la escucha y el pipeline sigue clasificando`() = runTest {
        val contextManager = ContextManager(SoundAlertContext.CASA)
        val vibrator = FakeAlertVibrator()
        val alerts = AlertHarness(contextManager, RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }
        val classifier = AmplitudeClassifier()

        val q = List(4) { Block.QUIET }
        val script = q + List(6) { Block.HORN } + List(12) { Block.QUIET } + // bocina en CASA
            List(6) { Block.MUSIC } + List(12) { Block.QUIET } + // música (UNKNOWN)
            List(6) { Block.HORN } + List(4) { Block.QUIET } // bocina en CALLE
        val switchAt = script.size - 10
        val source = ScriptedSource(script) { i -> if (i == switchAt) contextManager.set(SoundAlertContext.CALLE) }

        val dispatcher = StandardTestDispatcher(testScheduler)
        AudioPipeline(source, classifier, LabelMapper(labels, config.stabilizer), config, dispatcher, dispatcher, onEvent = alerts::onDetection).run()

        // La bocina en CASA no tiene regla; la música es UNKNOWN; solo alerta la bocina en CALLE.
        val alert = alerts.alerts.value.single()
        assertEquals(SoundAlertContext.CALLE, alert.context)
        assertEquals(Priority.ATTENTION, alert.priority)
        assertEquals("Vehicle horn, car horn, honking", alert.label)
        assertEquals(listOf(Priority.ATTENTION), vibrator.vibrations.map { it.first })
        // El pipeline siguió clasificando después del cambio de contexto.
        assertTrue("inferencias=${classifier.calls}", classifier.calls >= 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `el pipeline rechaza un LabelMapper con otra configuracion de estabilizacion`() = runTest {
        val other = config.stabilizer.copy(onThresholdByCategory = config.stabilizer.onThresholdByCategory + (SoundCategory.CAR_HORN to 0.40f))
        val dispatcher = StandardTestDispatcher(testScheduler)
        AudioPipeline(ScriptedSource(emptyList()) {}, AmplitudeClassifier(), LabelMapper(labels, other), config, dispatcher, dispatcher).run()
    }
}
