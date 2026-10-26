package com.soundalert.wear.ui.sessions

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SessionUiTest {

    private val utc = ZoneOffset.UTC
    private fun at(time: String) = Instant.parse("2026-10-07T${time}:00Z").toEpochMilli()

    private fun event(priority: Priority?, acknowledged: Boolean = false, atMs: Long = 0) =
        SessionEvent("a", SoundCategory.SIREN, priority, atMs, acknowledged)

    private fun session(id: String, context: SoundAlertContext, from: String, to: String?, vararg events: SessionEvent) =
        Session(id, context, at(from), to?.let(::at), events.toList())

    private fun sound(at: String, priority: Priority?, acknowledged: Boolean = false, category: SoundCategory = SoundCategory.SIREN) =
        SessionEvent(at, category, priority, at(at), acknowledged)

    private val yesterday = Session("ayer", SoundAlertContext.CASA, at("06:00") - 86_400_000L, at("07:00") - 86_400_000L, emptyList())
    private val morning = session("m", SoundAlertContext.CASA, "06:50", "07:40", sound("07:00", Priority.INFORMATION, true))
    private val street = session(
        "c", SoundAlertContext.CALLE, "07:40", "08:20",
        sound("07:50", Priority.ATTENTION), sound("08:05", Priority.DANGER, true), sound("08:09", null), sound("08:14", Priority.ATTENTION),
    )
    private val now = session("n", SoundAlertContext.OTRO, "12:30", null, sound("12:40", Priority.INFORMATION))

    @Test
    fun `la pila va de la sesion mas reciente a la mas antigua de hoy y termina en Tu dia`() {
        val cards = stackCards(listOf(street, yesterday, now, morning), at("13:15"), utc)
        assertEquals(listOf("n", "c", "m"), cards.dropLast(1).map { (it as StackCard.SessionCard).session.id })
        assertTrue(cards.last() is StackCard.DayCard)
        // Sin sesiones hoy, solo "Tu día".
        assertEquals(1, stackCards(listOf(yesterday), at("13:15"), utc).size)
    }

    @Test
    fun `Tu dia cuenta sesiones, tiempo escuchando, alertas por nivel y la mas tranquila`() {
        val day = (stackCards(listOf(morning, street, now), at("13:15"), utc).last() as StackCard.DayCard).day
        assertEquals(3, day.sessions)
        // 50 min + 40 min + 45 min (la actual, hasta ahora).
        assertEquals((50 + 40 + 45) * 60_000L, day.listeningMs)
        assertEquals(Triple(1, 2, 2), Triple(day.danger, day.attention, day.information))
        assertEquals("1 PELIGRO · 2 ATENCIÓN · 2 AVISO", levelsLine(day))
        // Mañana: 1 alerta en 50 min; actual: 1 en 45; calle: 3 en 40. La más tranquila es la de la mañana.
        assertEquals("m", day.calmest?.id)
        assertEquals("SIN ALERTAS HOY", levelsLine(daySummary(emptyList(), at("13:15"))))
        assertEquals(listOf("1 sesión", "4 sesiones"), listOf(sessionsText(1), sessionsText(4)))
    }

    @Test
    fun `resumen de cada sesion`() {
        assertEquals("4 sonidos · 1 peligro confirmado", sessionSummary(street))
        assertEquals("1 sonido · 1 aviso", sessionSummary(now))
        assertEquals("Sin alertas en esta sesión", sessionSummary(session("v", SoundAlertContext.CASA, "09:00", "09:30")))
        assertEquals("2 sonidos · sin alertas", sessionSummary(session("s", SoundAlertContext.CASA, "09:00", "09:30", sound("09:01", null), sound("09:02", null))))
        assertEquals("2 sonidos · 2 peligros sin confirmar", sessionSummary(session("d", SoundAlertContext.CALLE, "09:00", "09:30", sound("09:01", Priority.DANGER), sound("09:02", Priority.DANGER, true))))
    }

    @Test
    fun `al soltar, la pila avanza o retrocede una tarjeta solo pasado el umbral`() {
        assertEquals(1, settleIndex(current = 0, offset = 0.45f, count = 4))
        assertEquals(0, settleIndex(current = 0, offset = 0.2f, count = 4))
        assertEquals(1, settleIndex(current = 2, offset = -0.5f, count = 4))
        assertEquals(2, settleIndex(current = 2, offset = -0.1f, count = 4))
        // En los extremos no se sale de la pila.
        assertEquals(3, settleIndex(current = 3, offset = 0.9f, count = 4))
        assertEquals(0, settleIndex(current = 0, offset = -0.9f, count = 4))
    }

    @Test
    fun `las tarjetas de atras se mueven escalonadas, sin saltos al empezar ni al terminar`() {
        for (d in listOf(0f, 1f, 2f, 3f)) assertEquals(d, staggered(d), 0.0001f)
        assertEquals(-0.4f, staggered(-0.4f), 0.0001f)
        // A medio camino, la de atrás todavía no ha avanzado tanto como la de delante.
        assertTrue(staggered(1.5f) > 1.5f)
        assertTrue(staggered(2.5f) > staggered(1.5f) + 1f)
    }

    @Test
    fun `solo cuentan las sesiones que tocan el dia local`() {
        val late = Session("tarde", SoundAlertContext.CASA, at("00:00") - 3_600_000L, at("00:30"), emptyList())
        assertEquals(listOf("tarde", "m"), todaySessions(listOf(morning, yesterday, late), at("13:00"), utc).map { it.id })
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
