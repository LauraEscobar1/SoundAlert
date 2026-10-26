package com.soundalert.wear.ui

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.MainActivity
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.service.ListeningService
import com.soundalert.wear.ui.sessions.clockTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cadena completa de las sesiones con el servicio de escucha REAL: se activa la escucha,
 * entran detecciones por el mismo punto que usa el pipeline (ContextualClassifier.onEvent)
 * y deben aparecer en el reloj de sesiones y en el detalle sin reiniciar la app. Solo el
 * sonido del micrófono queda fuera (el emulador no tiene uno real).
 */
@RunWith(AndroidJUnit4::class)
class SessionFlowUiTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext
    private lateinit var original: SoundAlertContext
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        SoundAlertRuntime.backendSync(app).enabled = false
        original = SoundAlertRuntime.contextManager.current
        SoundAlertRuntime.contextManager.set(SoundAlertContext.CASA)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        val alerts = SoundAlertRuntime.alertManager(app)
        alerts.activeAlerts.forEach { alerts.acknowledge(it.id) }
        ListeningService.stop(app)
        SoundAlertRuntime.contextManager.set(original)
        scenario.close()
    }

    private fun detect(category: SoundCategory) = instrumentation.runOnMainSync {
        SoundAlertRuntime.contextualClassifier(app).onEvent(DetectionEvent.Started(category, 0.9f, category.name, false, 0))
    }

    /** Detecta y confirma la alerta (si la hay) para que la pantalla de alerta se cierre. */
    private fun detectAndAcknowledge(category: SoundCategory) {
        detect(category)
        val alerts = SoundAlertRuntime.alertManager(app)
        alerts.activeAlerts.filter { it.category == category }.forEach { alerts.acknowledge(it.id) }
    }

    @Test
    fun lasDeteccionesDeLaEscuchaRealAparecenEnElRelojYEnElDetalle() {
        // Escucha real: el servicio en primer plano pasa a LISTENING y abre la sesión.
        scenario.onActivity { ListeningService.start(it) }
        compose.waitUntil(15_000) { PipelineStatus.snapshot.value.phase == PipelineStatus.Phase.LISTENING }
        compose.waitUntil(5_000) { SoundAlertRuntime.sessionTracker.sessions.value.lastOrNull()?.let { it.ongoing && it.context == SoundAlertContext.CASA } == true }

        // Tres niveles en casa y una bocina, que en casa no tiene regla (no alerta).
        detectAndAcknowledge(SoundCategory.FIRE_ALARM)
        Thread.sleep(50)
        detectAndAcknowledge(SoundCategory.GLASS_BREAK)
        Thread.sleep(50)
        detectAndAcknowledge(SoundCategory.DOORBELL)
        Thread.sleep(50)
        detect(SoundCategory.CAR_HORN)

        compose.waitUntil(5_000) { (SoundAlertRuntime.sessionTracker.sessions.value.last().events.size) >= 4 }
        val events = SoundAlertRuntime.sessionTracker.sessions.value.last().events
        assertEquals(
            listOf(Priority.DANGER, Priority.ATTENTION, Priority.INFORMATION, null),
            events.filter { it.category in setOf(SoundCategory.FIRE_ALARM, SoundCategory.GLASS_BREAK, SoundCategory.DOORBELL, SoundCategory.CAR_HORN) }.map { it.priority },
        )

        // Reloj de sesiones (tercera página): la sesión actual con un conteo por nivel.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeLeft() }
        for (count in listOf("1 peligro", "1 atención", "1 aviso", "sin alerta")) {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(count, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }

        // Detalle: el primer sonido de la sesión con su hora local, y la corona pasa al siguiente.
        compose.onNode(hasText("GIRA LA CORONA", substring = true)).performClick()
        val is24 = DateFormat.is24HourFormat(app)
        val fire = events.first { it.category == SoundCategory.FIRE_ALARM }
        val glass = events.first { it.category == SoundCategory.GLASS_BREAK }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Incendio · ${clockTime(fire.atMs, is24)}", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(120f) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Vidrio roto · ${clockTime(glass.atMs, is24)}", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
