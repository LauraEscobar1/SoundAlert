package com.soundalert.wear.context

import com.soundalert.wear.alert.AlertHarness
import com.soundalert.wear.alert.AlertManager
import com.soundalert.wear.alert.FakeAlertVibrator
import com.soundalert.wear.classifier.LabelMapper
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import com.soundalert.wear.context.SoundAlertContext.OTRO
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.detection.DetectionStabilizer
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contexto activo CASA/CALLE/OTRO de punta a punta, con las 521 clases reales:
 * puntuaciones YAMNet → LabelMapper → DetectionStabilizer → contexto activo →
 * RuleEngine → AlertManager → vibración, y lo que queda guardado (historial y alerta).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveContextFlowTest {

    private val config = PipelineConfig()
    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val mapper = LabelMapper(labels, config.stabilizer)

    /** Un sonido sostenido 1,5 s (3 ventanas de 500 ms) a 0,8. */
    private fun TestScope.play(h: AlertHarness, label: String) {
        val stabilizer = DetectionStabilizer(config.stabilizer, config.hopMs)
        val scores = FloatArray(labels.size).also { it[labels.indexOf(label).also { i -> check(i >= 0) { label } }] = 0.8f }
        repeat(3) {
            stabilizer.update(mapper.classify(scores).known, testScheduler.currentTime).forEach(h::onDetection)
            advanceTimeBy(config.hopMs); runCurrent()
        }
    }

    // ---------- Contextos activos ----------

    @Test
    fun `los contextos activos son CASA, CALLE y OTRO, con OTRO como fallback`() {
        assertEquals(listOf(CASA, CALLE, OTRO), SoundAlertContext.ACTIVE)
        assertEquals(OTRO, SoundAlertContext.DEFAULT)
        assertEquals(OTRO, ContextManager().current)
    }

    @Test
    fun `los codigos de API coinciden con el backend (HOME, STREET, OTHER)`() {
        assertEquals(listOf("HOME", "STREET", "OTHER"), SoundAlertContext.ACTIVE.map { it.apiCode })
        assertEquals(CASA, SoundAlertContext.fromApiCode("HOME"))
        assertEquals(OTRO, SoundAlertContext.fromApiCode("OTHER"))
        assertNull(SoundAlertContext.fromApiCode("WORK")) // histórico: no activo
        assertNull(SoundAlertContext.fromApiCode("UNIVERSITY"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TRABAJO no se puede fijar como contexto activo`() {
        ContextManager().set(SoundAlertContext.TRABAJO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TRANSPORTE no puede ser el contexto inicial`() {
        ContextManager(SoundAlertContext.TRANSPORTE)
    }

    @Test
    fun `un intento de fijar un contexto inactivo no cambia el contexto activo`() {
        val manager = ContextManager(CALLE)
        runCatching { manager.set(SoundAlertContext.TRABAJO) }
        assertEquals(CALLE, manager.current)
    }

    // ---------- Matriz activa ----------

    @Test
    fun `matriz activa contexto x categoria`() {
        val D = Priority.DANGER; val A = Priority.ATTENTION; val I = Priority.INFORMATION
        // categoría → (CASA, CALLE, OTRO); null = sin alerta
        val matrix = mapOf<SoundCategory, Triple<Priority?, Priority?, Priority?>>(
            SoundCategory.SIREN to Triple(D, D, D), SoundCategory.FIRE_ALARM to Triple(D, D, D), SoundCategory.SMOKE_ALARM to Triple(D, D, D),
            SoundCategory.GENERAL_ALARM to Triple(A, A, A), SoundCategory.GLASS_BREAK to Triple(A, A, A), SoundCategory.SCREAM to Triple(A, A, A),
            SoundCategory.CAR_HORN to Triple(null, A, A), SoundCategory.TIRE_SKID to Triple(null, A, A), SoundCategory.TRAIN_HORN to Triple(null, A, A),
            SoundCategory.REVERSING_VEHICLE to Triple(null, A, A), SoundCategory.CAR_ALARM to Triple(null, A, A), SoundCategory.BICYCLE_BELL to Triple(null, A, A),
            SoundCategory.BABY_CRYING to Triple(I, null, I), SoundCategory.DOG_BARK to Triple(I, I, I), SoundCategory.DOORBELL to Triple(I, null, I),
            SoundCategory.DOOR_KNOCK to Triple(I, null, I), SoundCategory.PHONE_RING to Triple(I, null, I), SoundCategory.ALARM_CLOCK to Triple(I, null, I),
            SoundCategory.WATER_RUNNING to Triple(I, null, I),
            SoundCategory.BELL to Triple(null, null, null), SoundCategory.WARNING_SIGNAL to Triple(null, null, null),
        )
        assertEquals(SoundCategory.KNOWN.toSet(), matrix.keys)
        val rules = RuleEngine()
        for ((category, expected) in matrix) {
            assertEquals("$category CASA", expected.first, rules.match(category, CASA)?.priority)
            assertEquals("$category CALLE", expected.second, rules.match(category, CALLE)?.priority)
            assertEquals("$category OTRO", expected.third, rules.match(category, OTRO)?.priority)
        }
    }

    // ---------- Casos 1–9 de punta a punta ----------

    /** (caso, contexto, clase YAMNet, categoría, prioridad o null = sin alerta, pulsos de vibración). */
    private val cases = listOf(
        listOf("1", CASA, "Siren", SoundCategory.SIREN, Priority.DANGER, 3),
        listOf("2", CALLE, "Siren", SoundCategory.SIREN, Priority.DANGER, 3),
        listOf("3", OTRO, "Siren", SoundCategory.SIREN, Priority.DANGER, 3),
        listOf("4", CALLE, "Vehicle horn, car horn, honking", SoundCategory.CAR_HORN, Priority.ATTENTION, 2),
        listOf("5", CASA, "Vehicle horn, car horn, honking", SoundCategory.CAR_HORN, null, 0),
        listOf("6", OTRO, "Vehicle horn, car horn, honking", SoundCategory.CAR_HORN, Priority.ATTENTION, 2),
        listOf("7", CASA, "Doorbell", SoundCategory.DOORBELL, Priority.INFORMATION, 1),
        listOf("8", CALLE, "Doorbell", SoundCategory.DOORBELL, null, 0),
        listOf("9", OTRO, "Doorbell", SoundCategory.DOORBELL, Priority.INFORMATION, 1),
    )

    @Test
    fun `casos 1 a 9 - contexto + sonido, prioridad, vibraciones y contexto guardado`() = runTest {
        for (case in cases) {
            val (name, context, label, category, priority) = case
            val pulses = case[5] as Int
            context as SoundAlertContext; label as String; category as SoundCategory; priority as Priority?
            val vibrator = FakeAlertVibrator()
            val h = AlertHarness(ContextManager(context), RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }
            play(h, label)

            // La detección siempre queda registrada con su contexto, alerte o no.
            val detection = h.history.detections.value.single()
            assertEquals("caso $name", category, detection.category)
            assertEquals("caso $name", context, detection.context)
            assertEquals("caso $name", priority, detection.priority)

            if (priority == null) {
                assertTrue("caso $name: no debe alertar", h.alerts.value.isEmpty())
                assertTrue("caso $name: no debe vibrar", vibrator.vibrations.isEmpty())
            } else {
                val alert = h.alerts.value.single()
                assertEquals("caso $name", priority, alert.priority)
                assertEquals("caso $name", context, alert.context)
                assertEquals("caso $name", detection.id, alert.detectionId)
                assertEquals("caso $name", listOf(pulses), vibrator.vibrations.map { it.second.pulses })
            }
        }
    }

    @Test
    fun `CASA - CALLE - OTRO - cada deteccion usa el contexto activo de su momento`() = runTest {
        val contexts = ContextManager(CASA)
        val vibrator = FakeAlertVibrator()
        val h = AlertHarness(contexts, RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }

        play(h, "Vehicle horn, car horn, honking") // CASA: sin alerta
        contexts.set(CALLE)
        play(h, "Doorbell") // CALLE: sin alerta
        play(h, "Vehicle horn, car horn, honking") // CALLE: ATTENTION
        contexts.set(OTRO)
        play(h, "Doorbell") // OTRO: INFORMATION

        val history = h.history.detections.value.reversed() // cronológico
        assertEquals(listOf(CASA, CALLE, CALLE, OTRO), history.map { it.context })
        assertEquals(
            listOf(SoundCategory.CAR_HORN, SoundCategory.DOORBELL, SoundCategory.CAR_HORN, SoundCategory.DOORBELL),
            history.map { it.category },
        )
        assertEquals(listOf(null, null, Priority.ATTENTION, Priority.INFORMATION), history.map { it.priority })
        // Alertas: solo las de CALLE (bocina) y OTRO (timbre), cada una con su contexto.
        assertEquals(listOf(OTRO to SoundCategory.DOORBELL, CALLE to SoundCategory.CAR_HORN), h.alerts.value.map { it.context to it.category })
        // Agrupación por contexto para el historial futuro.
        val grouped = h.history.byContext()
        assertEquals(setOf(CASA, CALLE, OTRO), grouped.keys)
        assertEquals(1, grouped.getValue(CASA).size)
        assertEquals(2, grouped.getValue(CALLE).size)
    }

    @Test
    fun `el clasificador funciona con cualquier proveedor de contexto (p ej uno futuro por ubicacion)`() = runTest {
        val external = object : ActiveContextProvider {
            val state = MutableStateFlow(CALLE)
            override val activeContext: StateFlow<SoundAlertContext> = state
        }
        val history = DetectionHistory()
        val vibrator = FakeAlertVibrator()
        val alerts = AlertManager(vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }
        val classifier = ContextualClassifier(external, RuleEngine(), history, alerts) { testScheduler.currentTime }

        classifier.onEvent(DetectionEvent.Started(SoundCategory.CAR_HORN, 0.9f, "Vehicle horn", false, 0))
        external.state.value = CASA
        classifier.onEvent(DetectionEvent.Started(SoundCategory.DOORBELL, 0.9f, "Doorbell", false, 0))

        assertEquals(listOf(CASA, CALLE), history.detections.value.map { it.context })
        assertEquals(listOf(Priority.INFORMATION, Priority.ATTENTION), alerts.alerts.value.map { it.priority })
    }
}
