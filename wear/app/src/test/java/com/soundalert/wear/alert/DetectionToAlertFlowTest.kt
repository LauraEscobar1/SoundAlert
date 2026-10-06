package com.soundalert.wear.alert

import com.soundalert.wear.classifier.CategoryScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.config.StabilizerConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionStabilizer
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DetectionStabilizer + AlertManager, ventana a ventana (500 ms) en tiempo
 * virtual, como en AudioPipeline: lo que importa es cuántas alertas y
 * vibraciones produce un sonido repetido o sostenido.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetectionToAlertFlowTest {

    private val hop = 500L
    private val vibrator = FakeAlertVibrator()
    private val stabilizer = DetectionStabilizer(StabilizerConfig(), hop)

    private fun TestScope.manager() = AlertHarness(
        ContextManager(SoundAlertContext.CALLE), RuleEngine(), vibrator, AlertConfig(), backgroundScope,
    ) { testScheduler.currentTime }

    private fun TestScope.windows(m: AlertHarness, n: Int, category: SoundCategory?, score: Float = 0.8f) = repeat(n) {
        val scores = category?.let { listOf(CategoryScore(it, score, it.name)) }.orEmpty()
        stabilizer.update(scores, testScheduler.currentTime).forEach(m::onDetection)
        advanceTimeBy(hop)
        runCurrent()
    }

    @Test
    fun `una sirena sostenida 30 s crea una sola alerta que repite su vibracion`() = runTest {
        val m = manager()
        windows(m, 60, SIREN) // 60 ventanas: no 60 alertas
        assertEquals(1, m.alerts.value.size)
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
        // Inicial (0 s) + repeticiones de la misma alerta a los 15 y 30 s.
        assertEquals(List(3) { Priority.DANGER }, vibrator.vibrations.map { it.first })
    }

    @Test
    fun `una bocina con 0,30 entra al flujo y genera ATTENTION 2_MEDIUM`() = runTest {
        val m = manager()
        windows(m, 3, CAR_HORN, 0.30f)
        assertEquals(Priority.ATTENTION, m.alerts.value.single().priority)
        assertEquals(listOf("2_MEDIUM"), vibrator.vibrations.map { it.second.name })
    }

    @Test
    fun `una bocina por debajo de su umbral no genera alerta`() = runTest {
        val m = manager()
        windows(m, 10, CAR_HORN, 0.24f)
        assertEquals(0, m.alerts.value.size)
        assertEquals(0, vibrator.vibrations.size)
    }

    @Test
    fun `una bocina intermitente no produce vibraciones repetidas`() = runTest {
        val m = manager()
        // Toca, para 2 s, toca, para 2 s, toca… (~10 s): el estabilizador ve 3 eventos.
        repeat(3) {
            windows(m, 4, CAR_HORN)
            windows(m, 4, null)
        }
        assertEquals(1, m.alerts.value.size)
        assertEquals(1, vibrator.vibrations.size)
    }

    @Test
    fun `un evento nuevo despues del cooldown genera otra alerta`() = runTest {
        val m = manager()
        windows(m, 4, CAR_HORN)
        windows(m, 30, null) // 15 s de silencio: la alerta expira y pasa el cooldown
        windows(m, 4, CAR_HORN)
        assertEquals(listOf(AlertStatus.ACTIVE, AlertStatus.EXPIRED), m.alerts.value.map { it.status })
        assertEquals(listOf(Priority.ATTENTION, Priority.ATTENTION), vibrator.vibrations.map { it.first })
    }

    @Test
    fun `musica y voz (UNKNOWN) no generan alertas`() = runTest {
        val m = manager()
        windows(m, 20, SoundCategory.UNKNOWN, 0.95f)
        assertEquals(0, m.alerts.value.size)
        assertEquals(0, vibrator.vibrations.size)
    }
}
