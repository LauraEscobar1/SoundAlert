package com.soundalert.wear.alert

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.FIRE_ALARM
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.UNKNOWN
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertManagerTest {

    private val contextManager = ContextManager(SoundAlertContext.CALLE)
    private val vibrator = FakeAlertVibrator()

    private fun TestScope.manager() = AlertManager(
        contextManager = contextManager,
        rules = RuleEngine(),
        vibrator = vibrator,
        config = AlertConfig(),
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    )

    private fun TestScope.started(category: SoundCategory, confidence: Float = 0.9f, label: String = category.name) =
        DetectionEvent.Started(category, confidence, label, fastPath = false, atMs = testScheduler.currentTime)

    @Test
    fun `una sirena en la calle crea una alerta DANGER activa con todos sus datos y vibra 3 largos`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN, 0.94f, "Police car (siren)"))

        val alert = m.activeAlerts.single()
        assertEquals(SIREN, alert.category)
        assertEquals("Police car (siren)", alert.label)
        assertEquals(0.94f, alert.confidence)
        assertEquals(Priority.DANGER, alert.priority)
        assertEquals(SoundAlertContext.CALLE, alert.context)
        assertEquals(AlertStatus.ACTIVE, alert.status)
        assertTrue(alert.id.isNotBlank())
        assertEquals(listOf(Priority.DANGER to "3_LONG"), vibrator.vibrations.map { it.first to it.second.name })
    }

    @Test
    fun `DANGER no expira sola y queda ACTIVE hasta el ACK`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(60 * 60 * 1000L) // una hora
        runCurrent()
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)

        val id = m.alerts.value.single().id
        assertTrue(m.acknowledge(id))
        val acked = m.alerts.value.single()
        assertEquals(AlertStatus.ACKNOWLEDGED, acked.status)
        assertEquals(testScheduler.currentTime, acked.closedAtMs)
        assertEquals(1, vibrator.cancels)
    }

    @Test
    fun `el ACK solo funciona sobre alertas activas`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        val id = m.alerts.value.single().id
        assertTrue(m.acknowledge(id))
        assertFalse(m.acknowledge(id))
        assertFalse(m.acknowledge("no-existe"))
    }

    @Test
    fun `ATTENTION expira a los 10 s`() = runTest {
        val m = manager()
        m.onDetection(started(CAR_HORN))
        assertEquals(listOf(Priority.ATTENTION to "2_MEDIUM"), vibrator.vibrations.map { it.first to it.second.name })

        advanceTimeBy(9_999); runCurrent()
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
        advanceTimeBy(1); runCurrent()
        assertEquals(AlertStatus.EXPIRED, m.alerts.value.single().status)
    }

    @Test
    fun `ATTENTION se puede cerrar antes por interaccion`() = runTest {
        val m = manager()
        m.onDetection(started(CAR_HORN))
        m.acknowledge(m.alerts.value.single().id)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(AlertStatus.ACKNOWLEDGED, m.alerts.value.single().status) // no pasa a EXPIRED después
    }

    @Test
    fun `INFORMATION expira a los 5 s y queda en el historial`() = runTest {
        contextManager.set(SoundAlertContext.CASA)
        val m = manager()
        m.onDetection(started(DOORBELL))
        assertEquals(listOf(Priority.INFORMATION to "1_SHORT"), vibrator.vibrations.map { it.first to it.second.name })

        advanceTimeBy(4_999); runCurrent()
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
        advanceTimeBy(1); runCurrent()
        assertEquals(AlertStatus.EXPIRED, m.alerts.value.single().status)
        assertTrue(m.activeAlerts.isEmpty())
        assertEquals(DOORBELL, m.alerts.value.single().category) // sigue en el historial
    }

    @Test
    fun `UNKNOWN nunca genera alerta ni vibracion`() = runTest {
        val m = manager()
        m.onDetection(started(UNKNOWN, 0.99f, "Music"))
        assertTrue(m.alerts.value.isEmpty())
        assertTrue(vibrator.vibrations.isEmpty())
    }

    @Test
    fun `las categorias de solo registro nunca alertan ni vibran`() = runTest {
        for (context in SoundAlertContext.entries) {
            contextManager.set(context)
            val m = manager()
            m.onDetection(started(SoundCategory.BELL, 0.9f, "Church bell"))
            m.onDetection(started(SoundCategory.WARNING_SIGNAL, 0.9f, "Beep, bleep"))
            assertTrue("$context", m.alerts.value.isEmpty())
        }
        assertTrue(vibrator.vibrations.isEmpty())
    }

    @Test
    fun `un sonido sin regla en el contexto no genera alerta (bocina en casa)`() = runTest {
        contextManager.set(SoundAlertContext.CASA)
        val m = manager()
        m.onDetection(started(CAR_HORN))
        assertTrue(m.alerts.value.isEmpty())
        assertTrue(vibrator.vibrations.isEmpty())
    }

    @Test
    fun `el cambio de contexto se aplica al siguiente evento`() = runTest {
        contextManager.set(SoundAlertContext.CASA)
        val m = manager()
        m.onDetection(started(CAR_HORN))
        assertTrue(m.alerts.value.isEmpty())

        contextManager.set(SoundAlertContext.CALLE)
        m.onDetection(started(CAR_HORN))
        assertEquals(SoundAlertContext.CALLE, m.alerts.value.single().context)
    }

    @Test
    fun `no se crea otra alerta de una categoria que ya esta ACTIVE`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(60_001); runCurrent() // pasa el cooldown, pero sigue ACTIVE sin confirmar
        m.onDetection(started(SIREN))
        assertEquals(1, m.alerts.value.size)
        // Solo la vibración inicial y las repeticiones de esa misma alerta (0, 15, 30, 45, 60 s).
        assertEquals(5, vibrator.vibrations.size)
    }

    // ---------- Auditoría: historial, arbitraje de vibración y ACK ----------

    @Test
    fun `una alerta DANGER activa nunca sale del historial aunque lleguen muchas otras`() = runTest {
        contextManager.set(SoundAlertContext.OTRO)
        val m = AlertManager(contextManager, RuleEngine(), vibrator, AlertConfig(historySize = 3), backgroundScope) { testScheduler.currentTime }
        m.onDetection(started(SIREN))
        val siren = m.alerts.value.single().id
        for (category in listOf(CAR_HORN, DOORBELL, SoundCategory.DOOR_KNOCK, SoundCategory.PHONE_RING, SoundCategory.DOG_BARK)) {
            m.onDetection(started(category))
            m.acknowledge(m.alerts.value.first().id)
        }
        assertEquals(siren, m.activeAlerts.single().id)
        assertTrue(m.alerts.value.size <= 3)
        // Sigue repitiendo y se puede confirmar.
        val before = vibrator.vibrations.count { it.first == Priority.DANGER }
        advanceTimeBy(15_000); runCurrent()
        assertEquals(before + 1, vibrator.vibrations.count { it.first == Priority.DANGER })
        assertTrue(m.acknowledge(siren))
    }

    @Test
    fun `una vibracion de menor prioridad no corta la de peligro en curso`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN)) // 3_LONG dura 2 300 ms
        m.onDetection(started(CAR_HORN)) // misma ventana
        assertEquals(2, m.activeAlerts.size) // las dos alertas existen
        assertEquals(listOf(Priority.DANGER), vibrator.vibrations.map { it.first }) // pero no se interrumpe el peligro
    }

    @Test
    fun `terminado el patron de peligro, otra alerta si vibra`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(2_300)
        m.onDetection(started(CAR_HORN))
        assertEquals(listOf(Priority.DANGER, Priority.ATTENTION), vibrator.vibrations.map { it.first })
    }

    @Test
    fun `un peligro si interrumpe una vibracion de menor prioridad`() = runTest {
        val m = manager()
        m.onDetection(started(CAR_HORN))
        m.onDetection(started(SIREN))
        assertEquals(listOf(Priority.ATTENTION, Priority.DANGER), vibrator.vibrations.map { it.first })
    }

    @Test
    fun `confirmar otra alerta no cancela la vibracion de peligro en curso`() = runTest {
        contextManager.set(SoundAlertContext.OTRO)
        val m = manager()
        m.onDetection(started(DOORBELL))
        advanceTimeBy(1_000)
        m.onDetection(started(SIREN))
        val doorbell = m.alerts.value.first { it.category == DOORBELL }.id
        m.acknowledge(doorbell)
        assertEquals(0, vibrator.cancels)
    }

    @Test
    fun `confirmar el peligro corta su propia vibracion en curso`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(1_000)
        m.acknowledge(m.alerts.value.single().id)
        assertEquals(1, vibrator.cancels)
    }

    // ---------- Alarma general (clase padre "Alarm") ----------

    @Test
    fun `alarma general sola genera ATTENTION 2_MEDIUM`() = runTest {
        val m = manager()
        m.onDetection(started(SoundCategory.GENERAL_ALARM, 0.6f, "Alarm"))
        assertEquals(Priority.ATTENTION, m.alerts.value.single().priority)
        assertEquals(listOf("2_MEDIUM"), vibrator.vibrations.map { it.second.name })
    }

    @Test
    fun `alarma general se descarta si hay una alarma especifica activa`() = runTest {
        val m = manager()
        m.onDetection(started(FIRE_ALARM))
        m.onDetection(started(SoundCategory.GENERAL_ALARM, 0.6f, "Alarm"))
        assertEquals(listOf(FIRE_ALARM), m.alerts.value.map { it.category })
        assertEquals(1, vibrator.vibrations.size)
    }

    @Test
    fun `alarma general se descarta durante el cooldown de una especifica ya confirmada`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.acknowledge(m.alerts.value.single().id)
        advanceTimeBy(5_000)
        m.onDetection(started(SoundCategory.GENERAL_ALARM, 0.6f, "Alarm"))
        assertEquals(1, m.alerts.value.size)
        advanceTimeBy(10_000) // pasado el cooldown sí puede alertar
        m.onDetection(started(SoundCategory.GENERAL_ALARM, 0.6f, "Alarm"))
        assertEquals(SoundCategory.GENERAL_ALARM, m.alerts.value.first().category)
    }

    @Test
    fun `una alerta no alarma (bocina) no bloquea la alarma general`() = runTest {
        val m = manager()
        m.onDetection(started(CAR_HORN))
        m.onDetection(started(SoundCategory.GENERAL_ALARM, 0.6f, "Alarm"))
        assertEquals(setOf(CAR_HORN, SoundCategory.GENERAL_ALARM), m.activeAlerts.map { it.category }.toSet())
    }

    // ---------- Repetición de DANGER ----------

    @Test
    fun `DANGER vibra una vez al crearse`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        runCurrent()
        assertEquals(1, vibrator.vibrations.size)
        advanceTimeBy(14_999); runCurrent()
        assertEquals(1, vibrator.vibrations.size)
    }

    @Test
    fun `mientras sigue ACTIVE repite la vibracion de la misma alerta sin crear otra`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(15_000); runCurrent()
        assertEquals(2, vibrator.vibrations.size)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(4, vibrator.vibrations.size)
        assertTrue(vibrator.vibrations.all { it.first == Priority.DANGER && it.second.name == "3_LONG" })
        assertEquals(1, m.alerts.value.size)
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
    }

    @Test
    fun `el ACK detiene las repeticiones al momento`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        advanceTimeBy(20_000); runCurrent() // vibración inicial + 1 repetición
        m.acknowledge(m.alerts.value.single().id)
        advanceTimeBy(60 * 60 * 1000L); runCurrent()
        assertEquals(2, vibrator.vibrations.size)
    }

    @Test
    fun `si el sonido termina sin ACK la alerta sigue repitiendo`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.onDetection(DetectionEvent.Ended(SIREN, 0.9f, 3_000, testScheduler.currentTime))
        advanceTimeBy(30_000); runCurrent()
        assertEquals(3, vibrator.vibrations.size)
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
    }

    @Test
    fun `una sirena nueva despues del ACK crea otra alerta con su propia repeticion`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        val first = m.alerts.value.single().id
        m.acknowledge(first)
        advanceTimeBy(10_000); runCurrent() // cooldown
        m.onDetection(started(SIREN))
        assertEquals(2, m.alerts.value.size)
        advanceTimeBy(15_000); runCurrent()
        // inicial de la 1ª + inicial de la 2ª + 1 repetición de la 2ª
        assertEquals(3, vibrator.vibrations.size)
        assertEquals(AlertStatus.ACKNOWLEDGED, m.alerts.value.first { it.id == first }.status)
    }

    @Test
    fun `ATTENTION e INFORMATION no se repiten`() = runTest {
        contextManager.set(SoundAlertContext.OTRO)
        val m = manager()
        m.onDetection(started(CAR_HORN))
        advanceTimeBy(1_000) // el patrón de la bocina (950 ms) ya terminó
        m.onDetection(started(DOORBELL))
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(Priority.ATTENTION, Priority.INFORMATION), vibrator.vibrations.map { it.first })
    }

    @Test
    fun `intervalo 0 desactiva la repeticion`() = runTest {
        val m = AlertManager(contextManager, RuleEngine(), vibrator, AlertConfig(dangerRepeatIntervalMs = 0), backgroundScope) { testScheduler.currentTime }
        m.onDetection(started(SIREN))
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, vibrator.vibrations.size)
    }

    @Test
    fun `cooldown - un nuevo evento de la misma categoria antes de 10 s no vibra`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.acknowledge(m.alerts.value.single().id)
        advanceTimeBy(5_000)
        m.onDetection(started(SIREN))
        assertEquals(1, m.alerts.value.size)
        assertEquals(1, vibrator.vibrations.size)
    }

    @Test
    fun `tras confirmar y pasado el cooldown, otra sirena genera otra alerta`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.acknowledge(m.alerts.value.single().id)
        advanceTimeBy(10_000)
        m.onDetection(started(SIREN))
        assertEquals(listOf(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED), m.alerts.value.map { it.status })
        assertEquals(2, vibrator.vibrations.size)
    }

    @Test
    fun `una alerta DANGER activa no bloquea otras categorias`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.onDetection(started(CAR_HORN))
        m.onDetection(started(FIRE_ALARM))
        assertEquals(setOf(SIREN, CAR_HORN, FIRE_ALARM), m.activeAlerts.map { it.category }.toSet())
        // La bocina no interrumpe el peligro en curso; otro peligro sí lo relanza.
        assertEquals(listOf(Priority.DANGER, Priority.DANGER), vibrator.vibrations.map { it.first })
    }

    @Test
    fun `el fin del sonido no cierra una alerta DANGER`() = runTest {
        val m = manager()
        m.onDetection(started(SIREN))
        m.onDetection(DetectionEvent.Ended(SIREN, 0.9f, 4_000, testScheduler.currentTime))
        assertEquals(AlertStatus.ACTIVE, m.alerts.value.single().status)
        assertNull(m.alerts.value.single().closedAtMs)
    }

    @Test
    fun `el historial esta acotado`() = runTest {
        val m = AlertManager(contextManager, RuleEngine(), vibrator, AlertConfig(historySize = 3, cooldownMs = 0), backgroundScope) { testScheduler.currentTime }
        repeat(5) {
            m.onDetection(started(CAR_HORN))
            m.acknowledge(m.activeAlerts.single().id)
        }
        assertEquals(3, m.alerts.value.size)
    }
}
