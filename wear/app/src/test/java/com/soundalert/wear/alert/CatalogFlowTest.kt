package com.soundalert.wear.alert

import com.soundalert.wear.classifier.LabelMapper
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.config.StabilizerConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.detection.DetectionStabilizer
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Catálogo completo de punta a punta con las 521 clases reales:
 * puntuaciones YAMNet → LabelMapper → DetectionStabilizer → contexto → reglas →
 * AlertManager → vibración. 3 ventanas de 500 ms por sonido (tiempo virtual).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogFlowTest {

    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val mapper = LabelMapper(labels, PipelineConfig().stabilizer)
    private val hop = 500L

    private class Run(val events: List<DetectionEvent>, val alerts: List<Alert>, val vibrations: List<String>)

    private fun TestScope.play(context: SoundAlertContext, windows: Int, vararg scores: Pair<String, Float>): Run {
        val vibrator = FakeAlertVibrator()
        val alerts = AlertHarness(ContextManager(context), RuleEngine(), vibrator, AlertConfig(), backgroundScope) { testScheduler.currentTime }
        val stabilizer = DetectionStabilizer(StabilizerConfig(), hop)
        val events = mutableListOf<DetectionEvent>()
        val vector = FloatArray(labels.size).also { v -> scores.forEach { (label, s) -> v[labels.indexOf(label).also { check(it >= 0) { label } }] = s } }
        repeat(windows) {
            val result = mapper.classify(vector)
            stabilizer.update(result.known, testScheduler.currentTime).forEach { e -> events += e; alerts.onDetection(e) }
            advanceTimeBy(hop); runCurrent()
        }
        return Run(events, alerts.alerts.value, vibrator.vibrations.map { it.second.name })
    }

    /** label → (categoría, contexto con regla, prioridad, patrón). Puntuación 0,8 sostenida 1,5 s. */
    private val positives = listOf(
        Triple("Police car (siren)", SoundAlertContext.CALLE, SoundCategory.SIREN to Priority.DANGER),
        Triple("Ambulance (siren)", SoundAlertContext.CASA, SoundCategory.SIREN to Priority.DANGER),
        Triple("Fire engine, fire truck (siren)", SoundAlertContext.TRABAJO, SoundCategory.SIREN to Priority.DANGER),
        Triple("Fire alarm", SoundAlertContext.CASA, SoundCategory.FIRE_ALARM to Priority.DANGER),
        Triple("Smoke detector, smoke alarm", SoundAlertContext.TRABAJO, SoundCategory.SMOKE_ALARM to Priority.DANGER),
        Triple("Alarm", SoundAlertContext.CASA, SoundCategory.GENERAL_ALARM to Priority.ATTENTION),
        Triple("Vehicle horn, car horn, honking", SoundAlertContext.CALLE, SoundCategory.CAR_HORN to Priority.ATTENTION),
        Triple("Air horn, truck horn", SoundAlertContext.TRANSPORTE, SoundCategory.CAR_HORN to Priority.ATTENTION),
        Triple("Car alarm", SoundAlertContext.CALLE, SoundCategory.CAR_ALARM to Priority.ATTENTION),
        Triple("Tire squeal", SoundAlertContext.CALLE, SoundCategory.TIRE_SKID to Priority.ATTENTION),
        Triple("Reversing beeps", SoundAlertContext.TRABAJO, SoundCategory.REVERSING_VEHICLE to Priority.ATTENTION),
        Triple("Train horn", SoundAlertContext.TRANSPORTE, SoundCategory.TRAIN_HORN to Priority.ATTENTION),
        Triple("Bicycle bell", SoundAlertContext.CALLE, SoundCategory.BICYCLE_BELL to Priority.ATTENTION),
        Triple("Shatter", SoundAlertContext.CASA, SoundCategory.GLASS_BREAK to Priority.ATTENTION),
        Triple("Screaming", SoundAlertContext.CALLE, SoundCategory.SCREAM to Priority.ATTENTION),
        Triple("Baby cry, infant cry", SoundAlertContext.CASA, SoundCategory.BABY_CRYING to Priority.ATTENTION),
        Triple("Doorbell", SoundAlertContext.CASA, SoundCategory.DOORBELL to Priority.INFORMATION),
        Triple("Knock", SoundAlertContext.CASA, SoundCategory.DOOR_KNOCK to Priority.INFORMATION),
        Triple("Ringtone", SoundAlertContext.TRABAJO, SoundCategory.PHONE_RING to Priority.INFORMATION),
        Triple("Alarm clock", SoundAlertContext.CASA, SoundCategory.ALARM_CLOCK to Priority.INFORMATION),
        Triple("Bark", SoundAlertContext.CALLE, SoundCategory.DOG_BARK to Priority.INFORMATION),
        Triple("Water tap, faucet", SoundAlertContext.CASA, SoundCategory.WATER_RUNNING to Priority.INFORMATION),
    )

    @Test
    fun `cada categoria del catalogo genera un evento y una alerta con su prioridad y vibracion`() = runTest {
        val expectedPattern = mapOf(Priority.DANGER to "3_LONG", Priority.ATTENTION to "2_MEDIUM", Priority.INFORMATION to "1_SHORT")
        for ((label, context, expected) in positives) {
            val (category, priority) = expected
            val run = play(context, 3, label to 0.8f)
            val started = run.events.filterIsInstance<DetectionEvent.Started>()
            assertEquals("$label: evento", listOf(category), started.map { it.category })
            val alert = run.alerts.single()
            assertEquals("$label: prioridad", priority, alert.priority)
            assertEquals("$label: etiqueta original", label, alert.label)
            assertEquals("$label: vibración", expectedPattern.getValue(priority), run.vibrations.first())
        }
        // Todas las categorías alertables están cubiertas por algún caso.
        assertEquals(SoundCategory.ALERTABLE.toSet(), positives.map { it.third.first }.toSet())
    }

    @Test
    fun `sonidos irrelevantes con puntuacion alta no generan eventos ni alertas en ningun contexto`() = runTest {
        val irrelevant = listOf(
            "Breathing", "Snoring", "Speech", "Whispering", "Music", "Inside, small room", "Typing",
            "Computer keyboard", "Walk, footsteps", "Laughter", "Cough", "Television", "Silence",
            "Environmental noise", "Sine wave", "Traffic noise, roadway noise",
        )
        for (context in SoundAlertContext.entries) {
            for (label in irrelevant) {
                val run = play(context, 10, label to 0.95f)
                assertTrue("$label en $context generó ${run.events}", run.events.isEmpty())
                assertTrue(run.alerts.isEmpty())
            }
        }
    }

    @Test
    fun `clases sin mapeo fiable siguen siendo UNKNOWN aunque suenen peligrosas`() = runTest {
        val unmapped = listOf(
            "Explosion", "Boom", "Gunshot, gunfire", "Fireworks", "Bang", "Slam", "Smash, crash",
            "Shout", "Yell", "Dog", "Honk", "Ding", "Glass", "Car passing by",
            "Motorcycle", "Microwave oven", "Crying, sobbing", "Rumble", "Conversation", "Applause", "Clapping",
        )
        for (label in unmapped) {
            val run = play(SoundAlertContext.OTRO, 10, label to 0.99f)
            assertTrue("$label generó ${run.events}", run.events.isEmpty())
            assertTrue(run.vibrations.isEmpty())
        }
    }

    @Test
    fun `campanas y pitidos se registran como evento pero nunca alertan ni vibran`() = runTest {
        val recordOnly = listOf(
            "Church bell" to SoundCategory.BELL,
            "Bell" to SoundCategory.BELL,
            "Beep, bleep" to SoundCategory.WARNING_SIGNAL,
            "Buzzer" to SoundCategory.WARNING_SIGNAL,
        )
        for (context in SoundAlertContext.entries) {
            for ((label, category) in recordOnly) {
                val run = play(context, 3, label to 0.9f)
                assertEquals("$label en $context", listOf(category), run.events.filterIsInstance<DetectionEvent.Started>().map { it.category })
                assertTrue(run.alerts.isEmpty())
                assertTrue(run.vibrations.isEmpty())
            }
        }
    }

    @Test
    fun `un detector de humo que pita no se queda en solo registro`() = runTest {
        val run = play(SoundAlertContext.CASA, 3, "Beep, bleep" to 0.7f, "Smoke detector, smoke alarm" to 0.4f)
        assertEquals(listOf(SoundCategory.SMOKE_ALARM), run.alerts.map { it.category })
        assertTrue(run.events.none { it.category == SoundCategory.WARNING_SIGNAL })
    }

    // ---------- Precedencia específica/genérica, de punta a punta ----------

    @Test
    fun `alarma generica valida con incendio debil - alerta GENERAL_ALARM y no FIRE_ALARM`() = runTest {
        val run = play(SoundAlertContext.CASA, 6, "Alarm" to 0.60f, "Fire alarm" to 0.20f)
        assertEquals(listOf(SoundCategory.GENERAL_ALARM), run.alerts.map { it.category })
        assertEquals(Priority.ATTENTION, run.alerts.single().priority)
    }

    @Test
    fun `alarma generica e incendio por debajo de sus umbrales - ningun evento ni alerta`() = runTest {
        val run = play(SoundAlertContext.CASA, 6, "Alarm" to 0.41f, "Fire alarm" to 0.26f)
        assertTrue(run.events.isEmpty())
        assertTrue(run.alerts.isEmpty())
    }

    @Test
    fun `incendio por encima de su umbral - solo FIRE_ALARM (DANGER), sin alarma general`() = runTest {
        val run = play(SoundAlertContext.CASA, 6, "Alarm" to 0.60f, "Fire alarm" to 0.40f)
        assertEquals(listOf(SoundCategory.FIRE_ALARM), run.alerts.map { it.category })
        assertEquals(Priority.DANGER, run.alerts.single().priority)
    }

    @Test
    fun `una sirena con Alarm de fondo genera una sola alerta DANGER y ninguna de alarma general`() = runTest {
        val run = play(SoundAlertContext.CALLE, 6, "Police car (siren)" to 0.74f, "Alarm" to 0.41f)
        assertEquals(listOf(SoundCategory.SIREN), run.alerts.map { it.category })
    }

    @Test
    fun `una sirena mezclada con voz sigue alertando`() = runTest {
        val run = play(SoundAlertContext.CALLE, 3, "Speech" to 0.9f, "Siren" to 0.5f)
        assertEquals(listOf(SoundCategory.SIREN), run.alerts.map { it.category })
    }

    @Test
    fun `varias categorias en la misma ventana - solo alerta la que supera su umbral`() = runTest {
        // Sirena fuerte, bocina por debajo de 0,25, música y voz altas (UNKNOWN), campana (solo registro).
        val run = play(
            SoundAlertContext.CALLE, 6,
            "Siren" to 0.8f, "Vehicle horn, car horn, honking" to 0.2f, "Music" to 0.9f, "Speech" to 0.7f, "Church bell" to 0.6f,
        )
        assertEquals(listOf(SoundCategory.SIREN), run.alerts.map { it.category })
        assertEquals(
            setOf(SoundCategory.SIREN, SoundCategory.BELL),
            run.events.filterIsInstance<DetectionEvent.Started>().map { it.category }.toSet(),
        )
        assertEquals(listOf("3_LONG"), run.vibrations)
    }

    @Test
    fun `una sirena activa no impide que una bocina posterior alerte`() = runTest {
        val run = play(SoundAlertContext.CALLE, 3, "Siren" to 0.8f, "Vehicle horn, car horn, honking" to 0.6f)
        assertEquals(setOf(SoundCategory.SIREN, SoundCategory.CAR_HORN), run.alerts.map { it.category }.toSet())
    }

    @Test
    fun `un sonido sin regla en el contexto genera evento pero no alerta`() = runTest {
        val run = play(SoundAlertContext.CASA, 3, "Vehicle horn, car horn, honking" to 0.8f)
        assertEquals(listOf(SoundCategory.CAR_HORN), run.events.filterIsInstance<DetectionEvent.Started>().map { it.category })
        assertTrue(run.alerts.isEmpty())
    }
}
