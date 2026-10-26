package com.soundalert.wear.ui.sessions

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SessionUiTest {

    private val utc = ZoneOffset.UTC
    private fun at(time: String) = Instant.parse("2026-10-07T${time}:00Z").toEpochMilli()

    private fun event(priority: Priority?, acknowledged: Boolean = false, atMs: Long = 0) =
        SessionEvent("a", SoundCategory.SIREN, priority, atMs, acknowledged)

    @Test
    fun `la esfera es de 12 horas - las 3 y las 15 caen en el mismo sitio`() {
        assertEquals(0f, dialAngle(at("00:00"), utc), 0.01f)
        assertEquals(90f, dialAngle(at("03:00"), utc), 0.01f)
        assertEquals(90f, dialAngle(at("15:00"), utc), 0.01f)
        assertEquals(255f, dialAngle(at("08:30"), utc), 0.01f)
    }

    @Test
    fun `arco de la sesion - proporcional, con un minimo visible y como mucho una vuelta`() {
        assertEquals(110f, dialSweep(at("08:30"), at("12:10")), 0.01f)
        assertEquals(MIN_SWEEP, dialSweep(at("08:30"), at("08:31")), 0.01f)
        assertEquals(360f, dialSweep(at("00:00"), at("00:00") + 2 * DIAL_WINDOW_MS), 0.01f)
    }

    @Test
    fun `solo se ven las sesiones que tocan las ultimas 12 horas`() {
        val now = at("20:00")
        val old = Session("old", SoundAlertContext.CASA, at("06:00"), at("07:00"), emptyList())
        val recent = Session("recent", SoundAlertContext.CALLE, at("07:40"), at("08:20"), emptyList())
        val ongoing = Session("now", SoundAlertContext.CASA, at("19:00"), null, emptyList())
        assertEquals(listOf("recent", "now"), visibleSessions(listOf(old, recent, ongoing), now).map { it.id })
    }

    @Test
    fun `textos de la sesion como en el diseno`() {
        val session = Session("s", SoundAlertContext.CASA, at("08:30"), at("12:10"), emptyList())
        assertEquals("08:30 – 12:10 · 3 h 40", rangeText(session, at("13:00"), is24Hour = true, zone = utc))
        assertEquals("8:30 AM – 12:10 PM · 3 h 40", rangeText(session, at("13:00"), is24Hour = false, zone = utc))
        assertEquals("07:40 – ahora · 40 min", rangeText(Session("o", SoundAlertContext.CALLE, at("07:40"), null, emptyList()), at("08:20"), true, utc))
        assertEquals("2 h", durationText(2 * 60 * 60_000L))
    }

    @Test
    fun `conteo por nivel del mas urgente al menos`() {
        val events = listOf(event(Priority.INFORMATION), event(Priority.DANGER), event(Priority.INFORMATION))
        assertEquals(listOf(Priority.DANGER to "1 peligro", Priority.INFORMATION to "2 avisos"), levelCounts(events))
        assertEquals(emptyList<Pair<Priority?, String>>(), levelCounts(emptyList()))
        // Los sonidos que no alertaron van al final.
        assertEquals(listOf(Priority.ATTENTION to "1 atención", null to "2 sin alerta"), levelCounts(listOf(event(null), event(Priority.ATTENTION), event(null))))
    }

    @Test
    fun `posicion de cada alerta en el arco del detalle`() {
        assertEquals(0f, sessionFraction(at("07:40"), at("07:40"), at("08:20")), 0.001f)
        assertEquals(0.625f, sessionFraction(at("08:05"), at("07:40"), at("08:20")), 0.001f)
        assertEquals(1f, sessionFraction(at("09:00"), at("07:40"), at("08:20")), 0.001f)
        assertEquals(0.5f, sessionFraction(at("07:40"), at("07:40"), at("07:40")), 0.001f)
    }

    @Test
    fun `que paso con cada alerta`() {
        assertEquals("Confirmada por ti", eventOutcome(event(Priority.DANGER, acknowledged = true)))
        assertEquals("Sin confirmar", eventOutcome(event(Priority.DANGER)))
        assertEquals("Se cerró sola", eventOutcome(event(Priority.INFORMATION)))
        assertEquals("Sin alerta en este contexto", eventOutcome(event(null)))
        assertEquals("Solo registro", eventOutcome(SessionEvent("b", SoundCategory.BELL, null, 0, false)))
    }
}
