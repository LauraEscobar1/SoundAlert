package com.soundalert.wear.context

import com.soundalert.wear.alert.AlertHarness
import com.soundalert.wear.alert.FakeAlertVibrator
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.BABY_CRYING
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import com.soundalert.wear.context.SoundAlertContext.OTRO
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contexto SELECCIONADO llega a cada detección posterior y se conserva tras un
 * reinicio del proceso (causa del fallo: el contexto solo vivía en memoria y cada
 * reinicio lo devolvía en silencio a OTRO).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextSelectionTest {

    /** Almacenamiento en memoria que sobrevive a "reinicios" (nuevos ContextManager). */
    private class FakeStore(var value: String? = null) : ContextStore {
        override fun load() = value
        override fun save(name: String) { value = name }
    }

    private val store = FakeStore()
    private val vibrator = FakeAlertVibrator()

    private fun TestScope.harness(contexts: ContextManager) =
        AlertHarness(contexts, RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }

    private fun TestScope.detect(h: AlertHarness, category: SoundCategory) {
        h.onDetection(DetectionEvent.Started(category, 0.9f, category.name, fastPath = false, atMs = testScheduler.currentTime))
        advanceTimeBy(15_000) // fuera del cooldown de la misma categoría
    }

    private fun AlertHarness.lastDetection() = history.detections.value.first()

    @Test
    fun `1 - el contexto inicial es OTRO`() {
        assertEquals(OTRO, ContextManager().current)
        assertEquals(OTRO, ContextManager(store = FakeStore()).current)
    }

    @Test
    fun `2, 3 y 4 - el contexto seleccionado se usa en la siguiente deteccion`() = runTest {
        val contexts = ContextManager(store = store)
        val h = harness(contexts)
        for (selected in listOf(CASA, CALLE, OTRO)) {
            contexts.set(selected)
            detect(h, DOORBELL)
            assertEquals("seleccionado $selected", selected, h.lastDetection().context)
        }
    }

    @Test
    fun `5 a 8 y bebe - reglas segun el contexto seleccionado`() = runTest {
        val contexts = ContextManager(store = store)
        val h = harness(contexts)
        // (contexto, sonido, prioridad esperada o null = sin alerta)
        val cases = listOf(
            Triple(CASA, DOORBELL, Priority.INFORMATION),
            Triple(CALLE, CAR_HORN, Priority.ATTENTION),
            Triple(CALLE, SIREN, Priority.DANGER),
            Triple(CASA, CAR_HORN, null),
            Triple(CASA, BABY_CRYING, Priority.INFORMATION),
        )
        for ((context, category, priority) in cases) {
            contexts.set(context)
            val alertsBefore = h.alerts.value.size
            detect(h, category)
            val detection = h.lastDetection()
            assertEquals("$category+$context", context, detection.context)
            assertEquals("$category+$context", priority, detection.priority)
            if (priority == null) {
                assertEquals("$category+$context no debe alertar", alertsBefore, h.alerts.value.size)
            } else {
                val alert = h.alerts.value.first()
                assertEquals(priority, alert.priority)
                assertEquals(context, alert.context)
            }
        }
    }

    @Test
    fun `9 - deteccion y alerta conservan exactamente el contexto con que se evaluaron`() = runTest {
        val contexts = ContextManager(store = store)
        val h = harness(contexts)
        contexts.set(CALLE)
        detect(h, SIREN)
        contexts.set(CASA) // cambio posterior: no debe alterar lo ya evaluado
        val detection = h.history.detections.value.single()
        val alert = h.alerts.value.single()
        assertEquals(CALLE, detection.context)
        assertEquals(CALLE, detection.rule!!.context)
        assertEquals(CALLE, alert.context)
        assertEquals(detection.id, alert.detectionId)
        assertEquals(listOf("3_LONG"), vibrator.vibrations.take(1).map { it.second.name })
    }

    @Test
    fun `10 - cambiar CASA a CALLE con la escucha activa afecta a las detecciones siguientes`() = runTest {
        val contexts = ContextManager(CASA, store)
        val h = harness(contexts) // misma instancia de principio a fin: no se reinicia nada
        detect(h, CAR_HORN) // CASA: sin alerta
        contexts.set(CALLE)
        detect(h, CAR_HORN) // CALLE: ATTENTION
        assertEquals(listOf(CALLE, CASA), h.history.detections.value.map { it.context })
        assertEquals(listOf(CALLE to Priority.ATTENTION), h.alerts.value.map { it.context to it.priority })
    }

    // ---------- Regresión del fallo: reinicio del proceso ----------

    @Test
    fun `el contexto elegido sobrevive a un reinicio del proceso`() = runTest {
        ContextManager(store = store).set(CASA)
        assertEquals("CASA", store.value)

        val afterRestart = ContextManager(store = store) // proceso nuevo
        assertEquals(CASA, afterRestart.current)
        val h = harness(afterRestart)
        detect(h, DOORBELL)
        assertEquals(CASA, h.lastDetection().context)
        assertEquals(Priority.INFORMATION, h.alerts.value.single().priority)
    }

    @Test
    fun `un valor guardado invalido o historico vuelve a OTRO`() {
        for (saved in listOf("TRABAJO", "TRANSPORTE", "UNIVERSIDAD", "basura", "")) {
            assertEquals(saved, OTRO, ContextManager(store = FakeStore(saved)).current)
        }
    }

    @Test
    fun `seleccionar un contexto inactivo no lo guarda ni lo aplica`() {
        val contexts = ContextManager(CALLE, store)
        runCatching { contexts.set(SoundAlertContext.TRABAJO) }
        assertEquals(CALLE, contexts.current)
        assertNull(store.value) // nada guardado aún
        assertTrue(runCatching { contexts.set(SoundAlertContext.TRANSPORTE) }.isFailure)
    }
}
