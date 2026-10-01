package com.soundalert.wear.classifier

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ejecuta el modelo real en el dispositivo/emulador. No valida la precisión
 * con sonidos reales (eso se hace con el micrófono); comprueba que el modelo
 * carga, que sus tensores son los esperados, que la salida es coherente y mide
 * la latencia.
 */
@RunWith(AndroidJUnit4::class)
class YamnetClassifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val classifier = YamnetClassifier(context)
    private val mapper = YamnetClassifier.loadLabelMapper(context)

    @After
    fun tearDown() = classifier.close()

    @Test
    fun modeloYEtiquetasCoinciden() {
        assertEquals(15_600, classifier.inputSamples)
        assertEquals(521, mapper.labels.size)
        assertEquals(521, classifier.classify(FloatArray(15_600)).size)
    }

    @Test
    fun puntuacionesEnRango() {
        val noise = FloatArray(15_600) { Random(1).nextFloat() * 0.2f - 0.1f }
        classifier.classify(noise).forEach { assertTrue("$it fuera de [0,1]", it in 0f..1f) }
    }

    /** El silencio no debe producir ninguna categoría de SoundAlert por encima del umbral inicial. */
    @Test
    fun silencioNoDisparaCategorias() {
        val scores = classifier.classify(FloatArray(15_600))
        Log.i(TAG, "Silencio digital → top 5: ${mapper.topLabels(scores, 5)}")
        val strong = mapper.map(scores).filter { it.score >= 0.35f }
        assertTrue("Falsos positivos en silencio: $strong", strong.isEmpty())
    }

    /** Solo informativo: qué responde el modelo a señales sintéticas. */
    @Test
    fun senalesSinteticasInformativo() {
        val tone = FloatArray(15_600) { (0.5 * sin(2 * PI * 1_000 * it / 16_000)).toFloat() }
        // "Sirena" sintética: frecuencia que oscila 700–1 400 Hz dos veces por segundo (FM).
        val center = 1_050.0
        val deviation = 350.0
        val rate = 2.0
        val sweep = FloatArray(15_600) {
            val t = it / 16_000.0
            val phase = 2 * PI * center * t - deviation / rate * cos(2 * PI * rate * t)
            (0.5 * sin(phase)).toFloat()
        }
        for ((name, wave) in listOf("tono 1 kHz" to tone, "barrido tipo sirena" to sweep)) {
            val scores = classifier.classify(wave)
            Log.i(TAG, "$name → top 5: ${mapper.topLabels(scores, 5)} · categorías: ${mapper.map(scores).take(3)}")
        }
    }

    @Test
    fun latencia() {
        val wave = FloatArray(15_600) { Random(2).nextFloat() * 0.2f - 0.1f }
        repeat(3) { classifier.classify(wave) } // calentamiento
        val times = (1..20).map { classifier.classify(wave); classifier.lastInferenceMs }.sorted()
        Log.i(TAG, "Latencia (20 inferencias, 1 hilo): mediana=${"%.1f".format(times[10])} ms, p95=${"%.1f".format(times[18])} ms, máx=${"%.1f".format(times.last())} ms")
        // Debe caber holgadamente en el salto de 500 ms entre ventanas.
        assertTrue("Inferencia demasiado lenta: ${times[10]} ms", times[10] < 250)
    }

    companion object {
        private const val TAG = "SA/YAMNetTest"
    }
}
