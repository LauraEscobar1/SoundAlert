package com.soundalert.wear.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.home.HomeContent
import com.soundalert.wear.ui.home.ListeningUi
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.theme.SoundAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Inicio: mantener presionado el núcleo ~1 s pausa o reanuda; un toque no; Escuchando ↔ Analizando. */
@RunWith(AndroidJUnit4::class)
class HomeUiTest {

    @get:Rule val compose = createComposeRule()

    private var toggles = 0
    private var contextOpened = 0
    private val detection = mutableStateOf<DetectionDisplay?>(null)

    private fun show() = compose.setContent {
        SoundAlertTheme {
            HomeContent(
                ListeningUi("Escuchando", running = true, listening = true),
                SoundAlertContext.CASA,
                detection.value,
                note = null,
                onToggle = { toggles++ },
                onOpenContext = { contextOpened++ },
                onOpenDiagnostic = null,
            )
        }
    }

    private val core get() = compose.onNodeWithContentDescription("Escuchando. Mantén presionado para pausar")

    @Test
    fun mantenerPresionadoUnSegundoElNucleoPausaYUnToqueNo() {
        show()
        core.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        core.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, toggles)

        core.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_100)
        core.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, toggles)

        // Con TalkBack, la acción del botón hace lo mismo.
        core.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(2, toggles)
    }

    @Test
    fun laPildoraDelContextoAbreContextoConZonaDe48() {
        show()
        compose.onNodeWithContentDescription("Contexto: Casa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        assertEquals(1, contextOpened)
    }

    @Test
    fun alDetectarPasaAAnalizandoYVuelve() {
        show()
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Escuchando")).fetchSemanticsNodes().isNotEmpty() }
        detection.value = DetectionDisplay(SoundCategory.SIREN, 0.94f, confirmed = false)
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Sonido detectado, Posible sirena, 94%")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Escuchando")).fetchSemanticsNodes().isEmpty() }
        detection.value = null
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Escuchando")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Sonido detectado", substring = true)).fetchSemanticsNodes().isEmpty() }
    }
}
