package com.soundalert.wear.evaluation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.LabelMapper
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundClassifier
import com.soundalert.wear.classifier.YamnetClassifier
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.pipeline.AudioPipeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * YAMNet REAL sobre grabaciones REALES (ESC-50), por el pipeline completo
 * (buffer, puerta de energía, YAMNet, LabelMapper, estabilizador), sin micrófono.
 *
 * Los clips no están en el repositorio: ejecutar antes `tools/fetch-eval-clips.sh`.
 * Si no hay clips, el test se omite.
 *
 * Informe en logcat:  adb logcat -s SA/Eval
 * Afirma solo lo que debe cumplirse siempre: los negativos (respiración, tecleo,
 * voz…) no generan ningún evento de una categoría ALERTABLE. Las campanas de
 * iglesia pueden generar BELL (solo registro: nunca alerta). Los aciertos de los
 * positivos se informan.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ClipEvaluationTest {

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    /** Categoría ESC-50 → categoría esperada en SoundAlert. */
    private val expected = mapOf(
        "siren" to SoundCategory.SIREN,
        "car_horn" to SoundCategory.CAR_HORN,
        "door_wood_knock" to SoundCategory.DOOR_KNOCK,
        "glass_breaking" to SoundCategory.GLASS_BREAK,
        "crying_baby" to SoundCategory.BABY_CRYING,
        "clock_alarm" to SoundCategory.ALARM_CLOCK,
        "dog" to SoundCategory.DOG_BARK,
        // Solo registro: puede detectarse, nunca alerta.
        "church_bells" to SoundCategory.BELL,
    )

    /** Guarda la mejor puntuación de cada categoría en todo el clip. */
    private class Recording(private val inner: SoundClassifier, private val mapper: LabelMapper) : SoundClassifier by inner {
        val best = HashMap<SoundCategory, Float>()
        override fun classify(waveform: FloatArray): FloatArray = inner.classify(waveform).also { scores ->
            mapper.map(scores).forEach { best.merge(it.category, it.score, ::maxOf) }
        }
    }

    private data class Result(val file: String, val group: String, val escCategory: String, val started: List<SoundCategory>, val best: Map<SoundCategory, Float>)

    @Test
    fun evaluarClipsReales() {
        val files = testContext.assets.list("clips")?.filter { it.endsWith(".wav") }.orEmpty().sorted()
        assumeTrue("Sin clips: ejecutar tools/fetch-eval-clips.sh", files.isNotEmpty())

        val config = PipelineConfig()
        val mapper = YamnetClassifier.loadLabelMapper(appContext, config.stabilizer)
        val yamnet = YamnetClassifier(appContext)
        val thread = newSingleThreadContext("sa-eval")
        val results = mutableListOf<Result>()
        try {
            for (file in files) {
                val (group, escCategory) = file.split("__").let { it[0] to it[1] }
                val wav = testContext.assets.open("clips/$file").use { it.readBytes() }
                val classifier = Recording(yamnet, mapper)
                val started = mutableListOf<SoundCategory>()
                runBlocking {
                    AudioPipeline(WavFileAudioSource(wav), classifier, mapper, config, thread, thread) { event ->
                        if (event is DetectionEvent.Started) started += event.category
                    }.run()
                }
                results += Result(file, group, escCategory, started, classifier.best)
                Log.i(TAG, String.format(Locale.US, "%-4s %-16s %-24s eventos=%s mejores=%s", group, escCategory, file.substringAfterLast("__"), started, top(classifier.best)))
            }
        } finally {
            yamnet.close()
            thread.close()
        }

        // Resumen por categoría ESC-50.
        results.groupBy { it.escCategory }.forEach { (escCategory, rs) ->
            val target = expected[escCategory]
            val line = if (target != null) {
                val hits = rs.count { target in it.started }
                val others = rs.flatMap { it.started }.filter { it != target }.toSet()
                "POS $escCategory → $target: $hits/${rs.size} detectados" + (if (others.isNotEmpty()) "; además: $others" else "")
            } else {
                val withEvents = rs.filter { it.started.isNotEmpty() }
                "NEG $escCategory: ${rs.size - withEvents.size}/${rs.size} sin eventos" + (if (withEvents.isNotEmpty()) "; eventos: ${withEvents.map { it.started }}" else "")
            }
            Log.i(TAG, "RESUMEN $line")
        }

        val falsePositives = results.filter { r -> r.group == "neg" && r.started.any { it.alertable } }
        assertTrue("Negativos con eventos alertables: ${falsePositives.map { "${it.file}=${it.started}" }}", falsePositives.isEmpty())
    }

    private fun top(best: Map<SoundCategory, Float>) =
        best.entries.sortedByDescending { it.value }.take(3).joinToString { String.format(Locale.US, "%s %.2f", it.key, it.value) }

    private companion object {
        const val TAG = "SA/Eval"
    }
}
