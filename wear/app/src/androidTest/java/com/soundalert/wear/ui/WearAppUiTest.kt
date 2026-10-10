package com.soundalert.wear.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.MainActivity
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import org.junit.After
import org.junit.Assert.assertEquals
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
        SoundAlertRuntime.contextManager.set(original)
        scenario.close()
    }

    private fun detect(category: SoundCategory, confidence: Float = 0.9f) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        SoundAlertRuntime.contextualClassifier(app).onEvent(DetectionEvent.Started(category, confidence, category.name, false, 0))
    }

    private fun alertOf(category: SoundCategory) =
        SoundAlertRuntime.alertManager(app).alerts.value.first { it.category == category }

    @Test
    fun reposoMuestraContextoYNavegaAContextoSonidosEHistorial() {
        compose.onNodeWithContentDescription("Contexto: Casa").assertExists()
        // Sin el servicio de escucha arrancado (el test no usa el micrófono).
        compose.onNodeWithText("En pausa").assertExists()
        compose.onNodeWithText("Toca para escuchar").assertExists()

        // D · Contexto: solo CASA, CALLE y OTRO; elegir CALLE vuelve al reposo con el nuevo contexto.
        compose.onNodeWithContentDescription("Contexto: Casa").performClick()
        compose.onNodeWithText("Contexto").assertExists()
        compose.onNodeWithText("Otro").assertExists()
        compose.onNodeWithText("Calle").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Contexto: Calle")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(SoundAlertContext.CALLE, SoundAlertRuntime.contextManager.current)
        assertEquals(0, compose.onAllNodes(hasText("Universidad")).fetchSemanticsNodes().size)

        // E y F: páginas a la derecha del reposo.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Sonidos en calle")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Sirena: alerta en Calle, prioridad peligro")).assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Historial")).fetchSemanticsNodes().isNotEmpty() }
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
    fun atencionSeCierraConUnToqueYQuedaEnElHistorial() {
        detect(SoundCategory.GLASS_BREAK)
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Vidrio roto")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ATENCIÓN").assertExists()
        compose.onNodeWithText("Casa · ahora").assertExists()

        compose.onNodeWithText("Vidrio roto").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("ATENCIÓN")).fetchSemanticsNodes().isEmpty() }
        assertEquals(AlertStatus.ACKNOWLEDGED, alertOf(SoundCategory.GLASS_BREAK).status)

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) {
            compose.onAllNodes(hasContentDescription("Vidrio roto, Casa, Confirmada", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun soloRegistroNoAlertaPeroApareceEnElHistorial() {
        detect(SoundCategory.BELL, 0.6f)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) {
            compose.onAllNodes(hasContentDescription("Campana, Casa, Solo registro", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodes(hasText("AVISO")).fetchSemanticsNodes().size)
        assertEquals(true, SoundAlertRuntime.alertManager(app).alerts.value.none { it.category == SoundCategory.BELL })
    }
}
