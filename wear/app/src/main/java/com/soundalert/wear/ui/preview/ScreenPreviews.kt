package com.soundalert.wear.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.rules.SoundToggles
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import com.soundalert.wear.ui.alert.AlertContent
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.components.rememberToastState
import com.soundalert.wear.ui.context.ContextContent
import com.soundalert.wear.ui.home.AnalyzingContent
import com.soundalert.wear.ui.home.ListeningContent
import com.soundalert.wear.ui.home.ListeningUi
import com.soundalert.wear.ui.sessions.SessionClockContent
import com.soundalert.wear.ui.sessions.SessionDetailContent
import com.soundalert.wear.ui.sounds.SoundsContent
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.state.soundCells
import com.soundalert.wear.ui.theme.SoundAlertTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * Previews de las pantallas A–I en la pantalla redonda pequeña (384 × 384 px, 192 dp)
 * con datos de ejemplo. Usan las versiones sin estado de cada pantalla.
 */

@Preview(name = "A · Escuchando", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun ListeningPreview() = SoundAlertTheme {
    ListeningContent(ListeningUi("Escuchando", "Timbre · Alarma · Bebé", running = true, listening = true), SoundAlertContext.CASA, {}, {}, null)
}

@Preview(name = "A · Escuchando (ambiente)", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun ListeningAmbientPreview() = SoundAlertTheme {
    CompositionLocalProvider(LocalAmbient provides true) {
        ListeningContent(ListeningUi("Escuchando", "Timbre · Alarma · Bebé", running = true, listening = true), SoundAlertContext.CASA, {}, {}, null)
    }
}

@Preview(name = "B · Analizando", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun AnalyzingPreview() = SoundAlertTheme {
    AnalyzingContent(DetectionDisplay(SoundCategory.SIREN, 0.94f, confirmed = false), note = null)
}

@Preview(name = "C · Peligro", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun DangerPreview() = SoundAlertTheme {
    AlertContent(alert(SoundCategory.FIRE_ALARM, Priority.DANGER, SoundAlertContext.CASA), stillSounding = true, nowMs = NOW, timeoutMs = null, onAcknowledge = {})
}

@Preview(name = "D · Atención", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun AttentionPreview() = SoundAlertTheme {
    AlertContent(alert(SoundCategory.CAR_HORN, Priority.ATTENTION, SoundAlertContext.CALLE), stillSounding = true, nowMs = NOW, timeoutMs = 10_000, onAcknowledge = {})
}

@Preview(name = "E · Aviso", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun InformationPreview() = SoundAlertTheme {
    AlertContent(alert(SoundCategory.DOORBELL, Priority.INFORMATION, SoundAlertContext.CASA, agoMs = 2_000), stillSounding = false, nowMs = NOW, timeoutMs = 5_000, onAcknowledge = {})
}

@Preview(name = "F · Contexto", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun ContextPreview() = SoundAlertTheme {
    ContextContent(SoundAlertContext.ACTIVE, current = SoundAlertContext.CASA, onSelect = {})
}

@Preview(name = "G · Sonidos de la calle", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun SoundsPreview() = SoundAlertTheme {
    SoundsContent(SoundAlertContext.CALLE, streetCells(), page = 0, onPage = {}, onToggle = {}, toast = rememberToastState())
}

@Preview(name = "G · Sonidos con toast", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun SoundsToastPreview() = SoundAlertTheme {
    val toast = rememberToastState()
    remember { toast.show("Perro activado", check = true) }
    SoundsContent(SoundAlertContext.CALLE, streetCells(), page = 1, onPage = {}, onToggle = {}, toast = toast)
}

@Preview(name = "H · Reloj de sesiones", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun SessionClockPreview() = SoundAlertTheme {
    SessionClockContent(SESSIONS, selected = 2, nowMs = today(13, 15), is24Hour = true, onStep = {}, onOpen = {})
}

@Preview(name = "I · Detalle de sesión", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun SessionDetailPreview() = SoundAlertTheme {
    SessionDetailContent(SESSIONS[1], index = 1, total = SESSIONS.size, selectedEvent = 1, nowMs = today(13, 15), is24Hour = true, onStep = {})
}

/** Wear OS pequeño redondo (384 × 384 px), sin depender de wear-tooling-preview. */
private const val SMALL_ROUND = "id:wearos_small_round"

private val NOW = System.currentTimeMillis()

private fun alert(category: SoundCategory, priority: Priority, context: SoundAlertContext, agoMs: Long = 0) =
    Alert("preview", "preview", category, category.name, 0.9f, priority, context, NOW - agoMs)

/** Calle con el perro y el grito apagados, como en el ejemplo del diseño. */
private fun streetCells() = SoundToggles().let { toggles ->
    toggles.setEnabled(SoundAlertContext.CALLE, SoundCategory.DOG_BARK, enabled = false)
    toggles.setEnabled(SoundAlertContext.CALLE, SoundCategory.SCREAM, enabled = false)
    toggles.setEnabled(SoundAlertContext.CALLE, SoundCategory.CAR_ALARM, enabled = false)
    soundCells(SoundAlertContext.CALLE, RuleEngine(toggles = toggles))
}

private fun today(hour: Int, minute: Int): Long =
    LocalDate.now().atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val SESSIONS = listOf(
    Session("1", SoundAlertContext.CASA, today(6, 50), today(7, 40), listOf(SessionEvent("a1", SoundCategory.ALARM_CLOCK, Priority.INFORMATION, today(7, 0), true))),
    Session(
        "2",
        SoundAlertContext.CALLE,
        today(7, 40),
        today(8, 20),
        listOf(
            SessionEvent("a2", SoundCategory.CAR_HORN, Priority.ATTENTION, today(7, 52), false),
            SessionEvent("a3", SoundCategory.SIREN, Priority.DANGER, today(8, 5), true),
        ),
    ),
    Session(
        "3",
        SoundAlertContext.OTRO,
        today(8, 30),
        today(12, 10),
        listOf(
            SessionEvent("a4", SoundCategory.PHONE_RING, Priority.INFORMATION, today(9, 40), true),
            SessionEvent("a5", SoundCategory.DOOR_KNOCK, Priority.INFORMATION, today(11, 15), false),
        ),
    ),
    Session("4", SoundAlertContext.CASA, today(12, 30), null, emptyList()),
)
