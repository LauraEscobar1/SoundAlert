package com.soundalert.wear.session

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import com.soundalert.wear.rules.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private var now = 1_000L
    private var ids = 0

    private class MemoryStore(var saved: List<Session> = emptyList()) : SessionStore {
        var saves = 0
        override fun load() = saved
        override fun save(sessions: List<Session>) {
            saved = sessions
            saves++
        }
    }

    private fun tracker(store: SessionStore? = null) = SessionTracker(store, clock = { now }, newId = { "s${++ids}" })

    private fun alert(id: String, atMs: Long, priority: Priority = Priority.DANGER, status: AlertStatus = AlertStatus.ACTIVE) =
        Alert(id, "d-$id", SoundCategory.SIREN, "Siren", 0.9f, priority, CALLE, atMs, status)

    @Test
    fun `una sesion empieza al escuchar y termina al pausar`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA, emptyList())
        assertTrue(tracker.sessions.value.isEmpty())

        now = 2_000
        tracker.update(listening = true, CASA, emptyList())
        assertEquals(listOf(Session("s1", CASA, 2_000, null, emptyList())), tracker.sessions.value)

        now = 9_000
        tracker.update(listening = false, CASA, emptyList())
        assertEquals(9_000L, tracker.sessions.value.single().endMs)
    }

    @Test
    fun `cambiar de contexto mientras se escucha cierra la sesion y abre otra`() {
        val tracker = tracker()
        tracker.update(listening = true, CASA, emptyList())
        now = 5_000
        tracker.update(listening = true, CALLE, emptyList())
        val (home, street) = tracker.sessions.value
        assertEquals(CASA to 5_000L, home.context to home.endMs)
        assertEquals(CALLE to null, street.context to street.endMs)
        assertEquals(5_000L, street.startMs)
    }

    @Test
    fun `cambiar de contexto en pausa no crea sesion - empieza al reanudar`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA, emptyList())
        tracker.update(listening = false, CALLE, emptyList())
        assertTrue(tracker.sessions.value.isEmpty())
        now = 3_000
        tracker.update(listening = true, CALLE, emptyList())
        assertEquals(CALLE, tracker.sessions.value.single().context)
    }

    @Test
    fun `cada alerta va a la sesion en que ocurrio y se marca al confirmarla`() {
        val tracker = tracker()
        tracker.update(listening = true, CASA, emptyList())
        now = 5_000
        val early = alert("a1", atMs = 4_000)
        // Llegan juntos el cambio de contexto y una alerta anterior a él.
        tracker.update(listening = true, CALLE, listOf(early))
        now = 6_000
        val late = alert("a2", atMs = 5_500, Priority.INFORMATION)
        tracker.update(listening = true, CALLE, listOf(late, early))

        val (home, street) = tracker.sessions.value
        assertEquals(listOf("a1"), home.events.map { it.alertId })
        assertEquals(listOf("a2"), street.events.map { it.alertId })
        assertEquals(false, home.events.single().acknowledged)

        now = 7_000
        tracker.update(listening = true, CALLE, listOf(late, early.copy(status = AlertStatus.ACKNOWLEDGED)))
        assertEquals(true, tracker.sessions.value.first().events.single().acknowledged)
        // Una alerta no se anota dos veces.
        assertEquals(2, tracker.sessions.value.sumOf { it.events.size })
    }

    @Test
    fun `una alerta fuera de toda sesion no se anota`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA, listOf(alert("x", atMs = 500)))
        assertTrue(tracker.sessions.value.isEmpty())
    }

    @Test
    fun `se guarda en cada cambio y una sesion abierta se cierra al restaurar`() {
        val store = MemoryStore()
        tracker(store).apply {
            update(listening = true, CASA, emptyList())
            now = 4_000
            update(listening = true, CASA, listOf(alert("a1", atMs = 3_000)))
            update(listening = true, CASA, listOf(alert("a1", atMs = 3_000)))
        }
        assertEquals(2, store.saves)
        assertNull(store.saved.single().endMs)

        // El proceso murió con la sesión abierta: se cierra en su última alerta.
        val restored = tracker(store).sessions.value.single()
        assertEquals(3_000L, restored.endMs)
        assertEquals(listOf("a1"), restored.events.map { it.alertId })
    }

    @Test
    fun `se conservan las sesiones mas recientes`() {
        val tracker = SessionTracker(clock = { now }, maxSessions = 3)
        repeat(5) {
            now += 1_000
            tracker.update(listening = true, if (it % 2 == 0) CASA else CALLE, emptyList())
        }
        assertEquals(3, tracker.sessions.value.size)
        assertEquals(now, tracker.sessions.value.last().startMs)
    }

    @Test
    fun `el formato del archivo conserva todo y descarta lo desconocido`() {
        val sessions = listOf(
            Session("s1", CASA, 1_000, 2_000, listOf(SessionEvent("a1", SoundCategory.DOORBELL, Priority.INFORMATION, 1_500, acknowledged = true))),
            Session("s2", CALLE, 2_000, null, emptyList()),
        )
        assertEquals(sessions, SessionJson.decode(SessionJson.encode(sessions)))

        val unknown = """{"sessions":[{"id":"x","context":"MARTE","startMs":1,"endMs":null,"events":[]},""" +
            """{"id":"y","context":"CASA","startMs":1,"endMs":2,"events":[{"alertId":"a","category":"NO_EXISTE","priority":"DANGER","atMs":1,"acknowledged":false}]}]}"""
        assertEquals(listOf(Session("y", CASA, 1, 2, emptyList())), SessionJson.decode(unknown))
    }
}
