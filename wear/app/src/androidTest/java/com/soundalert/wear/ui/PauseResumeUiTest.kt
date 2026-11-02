package com.soundalert.wear.ui

import android.Manifest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.MainActivity
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.service.ListeningService
import com.soundalert.wear.ui.home.PAUSED_TOAST
import com.soundalert.wear.ui.home.RESUMED_TOAST
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App real (MainActivity + servicio de escucha): pausar y reanudar desde el Jarvis solo
 * cambia el estado de escucha. La pantalla sigue siendo el inicio nuevo, la Activity es la
 * misma (no se recrea ni se abre otra) y nunca aparece la pantalla de diagnóstico vieja.
 */
@RunWith(AndroidJUnit4::class)
class PauseResumeUiTest {

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
        ListeningService.stop(app)
        SoundAlertRuntime.contextManager.set(original)
        scenario.close()
    }

    private fun activity(): MainActivity {
        lateinit var current: MainActivity
        scenario.onActivity { current = it }
        return current
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Mantiene presionado el núcleo 1,3 s; con [tapFirst], antes un toque corto (el caso del fallo). */
    private fun holdCore(tapFirst: Boolean = false) {
        val core = compose.onNodeWithContentDescription("Mantén presionado", substring = true)
        // La interfaz corre con el reloj del test: el tiempo de la pulsación se avanza con él.
        if (tapFirst) {
            core.performTouchInput { click(center) }
            compose.mainClock.advanceTimeBy(80)
        }
        core.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_300)
        core.performTouchInput { up() }
    }

    /** Sigue en el inicio nuevo: píldora del contexto y Jarvis, sin diagnóstico. */
    private fun assertNewHome(first: MainActivity) {
        compose.onNodeWithContentDescription("Contexto: Casa").assertExists()
        compose.onNodeWithContentDescription("Mantén presionado", substring = true).assertExists()
        assertEquals(0, compose.onAllNodes(hasText("diagnóstico", substring = true)).fetchSemanticsNodes().size)
        assertSame("la Activity no se recrea", first, activity())
    }

    @Test
    fun pausarYReanudarDesdeElJarvisSeQuedaEnElInicioNuevo() {
        ListeningService.stop(app)
        compose.waitUntil(5_000) { PipelineStatus.snapshot.value.phase == Phase.IDLE }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("En pausa. Mantén presionado para escuchar")).fetchSemanticsNodes().isNotEmpty() }
        val first = activity()

        // Reanudar.
        holdCore()
        waitFor(RESUMED_TOAST)
        compose.waitUntil(15_000) { PipelineStatus.snapshot.value.phase == Phase.LISTENING }
        // El título animado se lee como descripción (cada letra va por separado).
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Escuchando")).fetchSemanticsNodes().isNotEmpty() }
        assertNewHome(first)

        // Pausar.
        holdCore()
        waitFor(PAUSED_TOAST)
        compose.waitUntil(5_000) { PipelineStatus.snapshot.value.phase == Phase.IDLE }
        waitFor("En pausa")
        assertNewHome(first)

        // Un toque y enseguida la pulsación larga: antes abría el diagnóstico; ahora solo reanuda.
        holdCore(tapFirst = true)
        waitFor(RESUMED_TOAST)
        compose.waitUntil(15_000) { PipelineStatus.snapshot.value.phase == Phase.LISTENING }
        assertNewHome(first)
    }
}
