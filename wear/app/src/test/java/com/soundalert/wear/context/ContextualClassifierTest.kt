package com.soundalert.wear.context

import com.soundalert.wear.alert.AlertHarness
import com.soundalert.wear.alert.FakeAlertVibrator
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.BELL
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOG_BARK
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.UNKNOWN
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.rules.SoundRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clasificación contextual: categoría ya reconocida + contexto activo → regla →
 * alerta. El contexto se congela en la detección y acompaña a la alerta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextualClassifierTest {

    private val contextManager = ContextManager()
    private val vibrator = FakeAlertVibrator()

    private fun TestScope.harness() =
        AlertHarness(contextManager, RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }

    private fun TestScope.detect(h: AlertHarness, category: SoundCategory, label: String = category.name) =
        h.onDetection(DetectionEvent.Started(category, 0.9f, label, fastPath = false, atMs = testScheduler.currentTime))

    @Test
    fun `caso 1 - CAR_HORN en CALLE da ATTENTION con contexto CALLE`() = runTest {
        contextManager.set(CALLE)
        val h = harness()
        detect(h, CAR_HORN, "Vehicle horn, car horn, honking")

        val detection = h.history.detections.value.single()
        assertEquals(CAR_HORN, detection.category)
        assertEquals(CALLE, detection.context)
        assertEquals(Priority.ATTENTION, detection.priority)
        val alert = h.alerts.value.single()
        assertEquals(Priority.ATTENTION, alert.priority)
        assertEquals(CALLE, alert.context)
    }

    @Test
    fun `caso 2 - DOORBELL en CASA da INFORMATION con contexto CASA`() = runTest {
        contextManager.set(CASA)
        val h = harness()
        detect(h, DOORBELL, "Doorbell")

        assertEquals(CASA, h.history.detections.value.single().context)
        val alert = h.alerts.value.single()
        assertEquals(Priority.INFORMATION, alert.priority)
        assertEquals(CASA, alert.context)
    }

    @Test
    fun `caso 3 - CAR_HORN en CASA no alerta pero la deteccion conserva CASA`() = runTest {
        contextManager.set(CASA)
        val h = harness()
        detect(h, CAR_HORN)

        assertTrue(h.alerts.value.isEmpty())
        assertTrue(vibrator.vibrations.isEmpty())
        val detection = h.history.detections.value.single()
        assertEquals(CAR_HORN, detection.category)
        assertEquals(CASA, detection.context)
        assertNull(detection.rule)
    }

    @Test
    fun `caso 4 - SIREN en CALLE da DANGER con contexto CALLE`() = runTest {
        contextManager.set(CALLE)
        val h = harness()
        detect(h, SIREN, "Police car (siren)")

        assertEquals(CALLE, h.history.detections.value.single().context)
        val alert = h.alerts.value.single()
        assertEquals(Priority.DANGER, alert.priority)
        assertEquals(CALLE, alert.context)
    }

    @Test
    fun `caso 5 - cambiar de contexto despues no modifica la deteccion ni la alerta anteriores`() = runTest {
        contextManager.set(CASA)
        val h = harness()
        detect(h, DOORBELL)
        val before = h.history.detections.value.single()
        val alertBefore = h.alerts.value.single()

        contextManager.set(CALLE)
        advanceTimeBy(60_000) // la alerta incluso expira: su contexto sigue siendo CASA

        assertEquals(CASA, h.history.detections.value.single().context)
        assertEquals(before, h.history.detections.value.single())
        assertEquals(CASA, h.alerts.value.single { it.id == alertBefore.id }.context)

        // Una detección posterior sí usa el contexto nuevo.
        detect(h, SIREN)
        assertEquals(CALLE, h.history.detections.value.first().context)
        assertEquals(CASA, h.history.detections.value.last().context)
    }

    @Test
    fun `caso 6 - el contexto llega a la alerta sin cambiar prioridad ni vibracion`() = runTest {
        val rules = RuleEngine()
        val patternFor = mapOf(Priority.DANGER to "3_LONG", Priority.ATTENTION to "2_MEDIUM", Priority.INFORMATION to "1_SHORT")
        val cases = listOf(CAR_HORN to CALLE, SIREN to CALLE, SIREN to CASA, DOORBELL to CASA, DOG_BARK to CASA)
        for ((category, context) in cases) {
            contextManager.set(context)
            val vib = FakeAlertVibrator()
            val h = AlertHarness(contextManager, rules, vib, AlertConfig(), backgroundScope) { testScheduler.currentTime }
            detect(h, category)

            val detection = h.history.detections.value.single()
            val alert = h.alerts.value.single()
            val expected = rules.match(category, context)!!.priority // la matriz es la fuente de verdad
            assertEquals("$category+$context", expected, alert.priority)
            assertEquals("$category+$context", context, alert.context)
            assertEquals(detection.id, alert.detectionId)
            assertEquals(detection.context, alert.context)
            assertEquals(listOf(patternFor.getValue(expected)), vib.vibrations.map { it.second.name })
        }
    }

    @Test
    fun `la regla de la deteccion es exactamente la de la matriz para su contexto`() = runTest {
        val rules = RuleEngine()
        val h = harness()
        for (context in SoundAlertContext.ACTIVE) {
            contextManager.set(context)
            for (category in SoundCategory.ALERTABLE) {
                detect(h, category)
                val detection = h.history.detections.value.first()
                assertEquals("$category+$context", rules.match(category, context), detection.rule)
                assertEquals(context, detection.context)
            }
        }
    }

    @Test
    fun `solo registro se guarda con su contexto pero sin regla ni alerta`() = runTest {
        contextManager.set(CASA)
        val h = harness()
        detect(h, BELL, "Church bell")
        val detection = h.history.detections.value.single()
        assertEquals(CASA, detection.context)
        assertTrue(detection.recordOnly)
        assertNull(detection.rule)
        assertTrue(h.alerts.value.isEmpty())
        assertTrue(vibrator.vibrations.isEmpty())
    }

    @Test
    fun `UNKNOWN no llega al historial`() = runTest {
        val h = harness()
        detect(h, UNKNOWN, "Music")
        assertTrue(h.history.detections.value.isEmpty())
        assertTrue(h.alerts.value.isEmpty())
    }

    @Test
    fun `el historial permite agrupar por contexto`() = runTest {
        val h = harness()
        contextManager.set(CASA); detect(h, DOORBELL); detect(h, DOG_BARK)
        contextManager.set(CALLE); detect(h, CAR_HORN); detect(h, SIREN)
        val grouped = h.history.byContext()
        assertEquals(listOf(DOG_BARK, DOORBELL), grouped.getValue(CASA).map { it.category })
        assertEquals(listOf(SIREN, CAR_HORN), grouped.getValue(CALLE).map { it.category })
    }

    @Test
    fun `el historial de detecciones esta acotado`() {
        val history = DetectionHistory(maxSize = 2)
        repeat(5) { i ->
            history.record(ContextualDetection("d$i", DOORBELL, "Doorbell", 0.9f, CASA, null, i.toLong()))
        }
        assertEquals(listOf("d4", "d3"), history.detections.value.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una deteccion no puede llevar la regla de otro contexto`() {
        ContextualDetection("x", CAR_HORN, "h", 0.9f, CASA, SoundRule(CALLE, CAR_HORN, Priority.ATTENTION), 0)
    }
}
