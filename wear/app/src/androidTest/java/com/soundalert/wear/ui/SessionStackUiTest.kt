package com.soundalert.wear.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import com.soundalert.wear.ui.sessions.SessionStackContent
import com.soundalert.wear.ui.sessions.stackCards
import com.soundalert.wear.ui.theme.SoundAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Pila de sesiones: orden, navegación con gesto y corona, "Tu día" y toque para abrir el detalle. */
@RunWith(AndroidJUnit4::class)
class SessionStackUiTest {

    @get:Rule val compose = createComposeRule()

    private fun today(h: Int, m: Int) = LocalDate.now().atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = today(13, 15)
    private val sessions = listOf(
        Session("m", SoundAlertContext.CASA, today(6, 50), today(7, 40), listOf(SessionEvent("a", SoundCategory.ALARM_CLOCK, Priority.INFORMATION, today(7, 0), true))),
        Session("c", SoundAlertContext.CALLE, today(7, 40), today(8, 20), listOf(SessionEvent("b", SoundCategory.SIREN, Priority.DANGER, today(8, 5), true))),
        Session("n", SoundAlertContext.OTRO, today(12, 30), null, emptyList()),
    )
    private var opened: String? = null

    private fun show() = compose.setContent {
        SoundAlertTheme { SessionStackContent(stackCards(sessions, now), now, is24Hour = true, onOpen = { opened = it }) }
    }

    private fun waitFor(text: String) = compose.waitUntil(3_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun crown(steps: Int) {
        val px = with(compose.density) { 40.dp.toPx() } * steps
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(px) }
        compose.waitForIdle()
    }

    @Test
    fun empiezaEnLaSesionMasRecienteYAvanzaYRetrocedeConElDedo() {
        show()
        waitFor("1/4")
        compose.onNode(hasText("Sin alertas en esta sesión", substring = true) and hasClickAction()).assertExists()

        compose.onRoot().performTouchInput { swipeUp() }
        waitFor("2/4")
        compose.onNode(hasText("1 peligro confirmado", substring = true) and hasClickAction()).assertExists()

        compose.onRoot().performTouchInput { swipeDown() }
        waitFor("1/4")
    }

    @Test
    fun unArrastreCortoVuelveASuLugar() {
        show()
        waitFor("1/4")
        compose.onRoot().performTouchInput { swipe(center, center.copy(y = center.y - 12.dp.toPx()), durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithText("1/4").assertExists()
    }

    @Test
    fun laCoronaPasaUnaTarjetaPorClicHastaTuDiaYVuelve() {
        show()
        waitFor("1/4")
        repeat(3) { crown(1) }
        waitFor("4/4")
        waitFor("TU DÍA")
        compose.onNodeWithText("Más tranquila: Otro").assertExists()

        crown(-1)
        waitFor("3/4")
    }

    @Test
    fun unToqueEnUnaSesionAbreSuDetalle() {
        show()
        waitFor("1/4")
        compose.onNode(hasText("Sin alertas en esta sesión", substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals("n", opened)
    }
}
