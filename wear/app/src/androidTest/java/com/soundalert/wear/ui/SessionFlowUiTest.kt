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

        // Tres niveles en casa y una bocina, que en casa no tiene regla (no alerta). Categorías
        // que ningún otro test usa: el AlertManager no repite una categoría antes de 10 s.
        val start = System.currentTimeMillis()
        val injected = listOf(SoundCategory.SMOKE_ALARM, SoundCategory.SCREAM, SoundCategory.DOOR_KNOCK, SoundCategory.CAR_HORN)
        for (category in injected) {
            detectAndAcknowledge(category)
            Thread.sleep(50)
        }
        fun mine() = SoundAlertRuntime.sessionTracker.sessions.value.last().events.filter { it.atMs >= start && it.category in injected }
        compose.waitUntil(5_000) { mine().size == 4 && mine().take(3).all { it.priority != null } }
        val events = mine()
        assertEquals(listOf(Priority.DANGER, Priority.ATTENTION, Priority.INFORMATION, null), events.map { it.priority })

        // Reloj de sesiones (tercera página): la sesión actual con un conteo por nivel.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeLeft() }
        for (count in listOf("1 peligro", "1 atención", "1 aviso", "sin alerta")) {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(count, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }

        // Detalle: el primer sonido de la sesión con su hora local, y la corona pasa al siguiente.
        compose.onNode(hasText("GIRA LA CORONA", substring = true)).performClick()
        val is24 = DateFormat.is24HourFormat(app)
        // El detalle empieza en el primer sonido de la sesión: se avanza con la corona hasta los del test.
        val smoke = events[0]
        val scream = events[1]
        val first = SoundAlertRuntime.sessionTracker.sessions.value.last().events.indexOf(smoke)
        repeat(first) {
            compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(120f) }
            compose.waitForIdle()
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Humo · ${clockTime(smoke.atMs, is24)}", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(120f) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Grito · ${clockTime(scream.atMs, is24)}", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
