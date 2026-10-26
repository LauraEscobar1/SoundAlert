package com.soundalert.wear.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.MainActivity
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.rules.Priority
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Interfaz real (MainActivity + WearApp) con el cableado real de SoundAlertRuntime:
 * las detecciones entran por el mismo punto que usa el pipeline
 * (ContextualClassifier.onEvent), así que reglas, alertas y vibración son las de
 * producción. Solo el micrófono y YAMNet quedan fuera.
 *
 * La sincronización con el backend se apaga antes de abrir la app: los tests no
 * escriben datos en Supabase.
 */
@RunWith(AndroidJUnit4::class)
class WearAppUiTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var original: SoundAlertContext
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        SoundAlertRuntime.backendSync(app).enabled = false
        original = SoundAlertRuntime.contextManager.current
        SoundAlertRuntime.contextManager.set(SoundAlertContext.CASA)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        val alerts = SoundAlertRuntime.alertManager(app)
        alerts.activeAlerts.forEach { alerts.acknowledge(it.id) }
        SoundAlertRuntime.soundToggles.setEnabled(SoundAlertContext.CASA, SoundCategory.GLASS_BREAK, enabled = true)
        PipelineStatus.reset()
        SoundAlertRuntime.contextManager.set(original)
        scenario.close()
    }

    private fun detect(category: SoundCategory, confidence: Float = 0.9f) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        SoundAlertRuntime.contextualClassifier(app).onEvent(DetectionEvent.Started(category, confidence, category.name, false, 0))
    }

    private fun alertOf(category: SoundCategory) =
        SoundAlertRuntime.alertManager(app).alerts.value.first { it.category == category }

    @Test
    fun reposoNavegaAContextoSonidosYSesiones() {
        // Inicio: la hora, el botón del contexto, el emblema y el estado (sin el servicio de escucha).
        compose.onNodeWithText("En pausa").assertExists()
        assertEquals(0, compose.onAllNodes(hasText("Timbre", substring = true)).fetchSemanticsNodes().size)
        // El botón del contexto se ve pequeño, pero su zona de toque mide al menos 48 × 48 dp.
        compose.onNodeWithContentDescription("Contexto: Casa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)

        // F · Contexto: solo CASA, CALLE y OTRO, sin la etiqueta MANUAL; elegir CALLE vuelve con el nuevo contexto.
        compose.onNodeWithContentDescription("Contexto: Casa").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Contexto")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasText("MANUAL")).fetchSemanticsNodes().size)
        compose.onNodeWithText("Otro").assertExists()
        // Zona táctil de cada fila: la fila visible (30 dp) más la separación (4 dp).
        listOf("Casa", "Calle", "Otro").forEach { compose.onNodeWithText(it).assertHeightIsAtLeast(34.dp) }
        compose.onNodeWithText("Calle").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Contexto: Calle")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(SoundAlertContext.CALLE, SoundAlertRuntime.contextManager.current)
        assertEquals(0, compose.onAllNodes(hasText("Universidad")).fetchSemanticsNodes().size)

        // G · Sonidos: su encabezado (icono y nombre del contexto, sin contador) también abre el contexto.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Calle") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Sirena, peligro")).assertExists()

        // H: la pila de sesiones, en la página siguiente.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Sesiones")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tocarUnaCeldaDelPanalApagaElSonidoEnEseContexto() {
        compose.onRoot().performTouchInput { swipeLeft() }
        // Segunda fila de Casa, visible sin desplazar: vidrio roto (atención).
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Vidrio roto, atención")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Vidrio roto, atención")).performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Vidrio roto desactivado")).fetchSemanticsNodes().isNotEmpty() }
        // La confirmación ocupa el lugar del encabezado: termina por encima de donde empiezan las celdas.
        val message = compose.onNode(hasText("Vidrio roto desactivado")).getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("mensaje en ${message.bottom}", message.bottom <= screen.top + (screen.bottom - screen.top) * 45f / 192f)
        assertNull(SoundAlertRuntime.ruleEngine.match(SoundCategory.GLASS_BREAK, SoundAlertContext.CASA))
        assertEquals(Priority.ATTENTION, SoundAlertRuntime.ruleEngine.match(SoundCategory.GLASS_BREAK, SoundAlertContext.OTRO)?.priority)

        // Peligro: candado, siempre activo; tocarlo solo lo recuerda.
        compose.onNode(hasContentDescription("Sirena, peligro")).performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Siempre activo por seguridad")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Priority.DANGER, SoundAlertRuntime.ruleEngine.match(SoundCategory.SIREN, SoundAlertContext.CASA)?.priority)
    }

    @Test
    fun unaAlertaDePeligroOcupaLaPantallaYEntendidoLaConfirma() {
        detect(SoundCategory.FIRE_ALARM)
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("ALARMA DE INCENDIO")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("PELIGRO").assertExists()

        compose.onNodeWithText("Entendido").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Entendido")).fetchSemanticsNodes().isEmpty() }
        assertEquals(AlertStatus.ACKNOWLEDGED, alertOf(SoundCategory.FIRE_ALARM).status)
    }

    @Test
    fun atencionSeCierraConUnToqueYQuedaEnLaSesion() {
        // La sesión empieza al escuchar (el estado del pipeline, sin micrófono).
        PipelineStatus.update { it.copy(phase = PipelineStatus.Phase.LISTENING) }
        compose.waitUntil(3_000) { SoundAlertRuntime.sessionTracker.sessions.value.lastOrNull()?.let { it.ongoing && it.context == SoundAlertContext.CASA } == true }
        detect(SoundCategory.GLASS_BREAK)
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("VIDRIO ROTO")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ATENCIÓN").assertExists()
        compose.onNodeWithText("Casa · ahora").assertExists()

        compose.onNodeWithText("VIDRIO ROTO").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("ATENCIÓN")).fetchSemanticsNodes().isEmpty() }
        assertEquals(AlertStatus.ACKNOWLEDGED, alertOf(SoundCategory.GLASS_BREAK).status)

        compose.waitUntil(3_000) {
            SoundAlertRuntime.sessionTracker.sessions.value.lastOrNull()?.events?.any { it.category == SoundCategory.GLASS_BREAK && it.acknowledged } == true
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("1 atención", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun soloRegistroNoAlerta() {
        detect(SoundCategory.BELL, 0.6f)
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasText("AVISO")).fetchSemanticsNodes().size)
        assertEquals(true, SoundAlertRuntime.alertManager(app).alerts.value.none { it.category == SoundCategory.BELL })
    }
}
