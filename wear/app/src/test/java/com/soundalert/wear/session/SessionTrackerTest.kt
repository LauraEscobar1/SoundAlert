package com.soundalert.wear.session

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.context.SoundAlertContext.CALLE
import com.soundalert.wear.context.SoundAlertContext.CASA
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.SoundRule
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

    /** Sin descarte de sesiones vacías salvo que el test lo pida: aquí los tiempos son de milisegundos. */
    private fun tracker(store: SessionStore? = null, minEmptySessionMs: Long = 0) =
        SessionTracker(store, clock = { now }, minEmptySessionMs = minEmptySessionMs, newId = { "s${++ids}" })

    private fun detection(id: String, atMs: Long, category: SoundCategory = SoundCategory.SIREN, context: SoundAlertContext = CALLE, priority: Priority? = Priority.DANGER) =
        ContextualDetection(id, category, category.name, 0.9f, context, priority?.let { SoundRule(context, category, it) }, atMs)

    private fun alertFor(d: ContextualDetection, status: AlertStatus = AlertStatus.ACTIVE) =
        Alert("alert-${d.id}", d.id, d.category, d.label, d.confidence, d.priority!!, d.context, d.detectedAtMs, status)

    private fun SessionTracker.update(listening: Boolean, context: SoundAlertContext) = update(listening, context, emptyList(), emptyList())

    @Test
    fun `una sesion empieza al escuchar y termina al pausar`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA)
        assertTrue(tracker.sessions.value.isEmpty())

        now = 2_000
        tracker.update(listening = true, CASA)
        assertEquals(listOf(Session("s1", CASA, 2_000, null, emptyList())), tracker.sessions.value)

        now = 9_000
        tracker.update(listening = false, CASA)
        assertEquals(9_000L, tracker.sessions.value.single().endMs)
    }

    @Test
    fun `cambiar de contexto mientras se escucha cierra la sesion y abre otra`() {
        val tracker = tracker()
        tracker.update(listening = true, CASA)
        now = 5_000
        tracker.update(listening = true, CALLE)
        val (home, street) = tracker.sessions.value
        assertEquals(CASA to 5_000L, home.context to home.endMs)
        assertEquals(CALLE to null, street.context to street.endMs)
        assertEquals(5_000L, street.startMs)
    }

    @Test
    fun `cambiar de contexto en pausa no crea sesion - empieza al reanudar`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA)
        tracker.update(listening = false, CALLE)
        assertTrue(tracker.sessions.value.isEmpty())
        now = 3_000
        tracker.update(listening = true, CALLE)
        assertEquals(CALLE, tracker.sessions.value.single().context)
    }

    @Test
    fun `cada sonido va a la sesion en que ocurrio, alerte o no`() {
        val tracker = tracker()
        tracker.update(listening = true, CASA)
        now = 5_000
        val siren = detection("d1", atMs = 4_000)
        // Llegan juntos el cambio de contexto y un sonido anterior a él.
        tracker.update(listening = true, CALLE, listOf(siren), listOf(alertFor(siren)))
        now = 6_000
        // Bocina en casa: se detecta pero no tiene regla, así que no alerta.
        val horn = detection("d2", atMs = 5_500, category = SoundCategory.CAR_HORN, priority = null)
        tracker.update(listening = true, CALLE, listOf(horn, siren), listOf(alertFor(siren)))

        val (home, street) = tracker.sessions.value
        assertEquals(listOf(SessionEvent("d1", SoundCategory.SIREN, Priority.DANGER, 4_000, false, "alert-d1")), home.events)
        assertEquals(listOf(SessionEvent("d2", SoundCategory.CAR_HORN, null, 5_500, false, null)), street.events)
    }

    @Test
    fun `la alerta que llega despues de su deteccion y su confirmacion actualizan el sonido`() {
        val tracker = tracker()
        tracker.update(listening = true, CALLE)
        val siren = detection("d1", atMs = 1_500)
        now = 2_000
        tracker.update(listening = true, CALLE, listOf(siren), emptyList())
        assertNull(tracker.sessions.value.single().events.single().priority)

        tracker.update(listening = true, CALLE, listOf(siren), listOf(alertFor(siren)))
        assertEquals(Priority.DANGER, tracker.sessions.value.single().events.single().priority)

        tracker.update(listening = true, CALLE, listOf(siren), listOf(alertFor(siren, AlertStatus.ACKNOWLEDGED)))
        val event = tracker.sessions.value.single().events.single()
        assertEquals(true, event.acknowledged)
        assertEquals("alert-d1", event.alertId)
        // Un sonido no se anota dos veces.
        assertEquals(1, tracker.sessions.value.sumOf { it.events.size })
    }

    @Test
    fun `una alerta cuya deteccion ya salio del historial tambien se anota`() {
        val tracker = tracker()
        tracker.update(listening = true, CALLE)
        now = 3_000
        tracker.update(listening = true, CALLE, emptyList(), listOf(alertFor(detection("d9", atMs = 2_000))))
        assertEquals(listOf("d9"), tracker.sessions.value.single().events.map { it.id })
    }

    @Test
    fun `un sonido fuera de toda sesion no se anota`() {
        val tracker = tracker()
        tracker.update(listening = false, CASA, listOf(detection("x", atMs = 500)), emptyList())
        assertTrue(tracker.sessions.value.isEmpty())
    }

    @Test
    fun `una sesion vacia de menos de un minuto se descarta al cerrarse`() {
        val tracker = tracker(minEmptySessionMs = 60_000)
        tracker.update(listening = true, CASA)
        now += 20_000
        tracker.update(listening = false, CASA)
        assertTrue(tracker.sessions.value.isEmpty())

        // Con algún sonido, o más larga, se conserva.
        tracker.update(listening = true, CASA)
        now += 10_000
        tracker.update(listening = true, CASA, listOf(detection("d1", atMs = now, context = CASA, priority = null)), emptyList())
        now += 10_000
        tracker.update(listening = false, CASA)
        tracker.update(listening = true, CALLE)
        now += 90_000
        tracker.update(listening = false, CALLE)
        assertEquals(listOf(CASA, CALLE), tracker.sessions.value.map { it.context })
    }

    @Test
    fun `se guarda en cada cambio y una sesion abierta se cierra al restaurar`() {
        val store = MemoryStore()
        val siren = detection("d1", atMs = 3_000)
        tracker(store).apply {
            update(listening = true, CASA)
            now = 4_000
            update(listening = true, CASA, listOf(siren), listOf(alertFor(siren)))
            update(listening = true, CASA, listOf(siren), listOf(alertFor(siren)))
        }
        assertEquals(2, store.saves)
        assertNull(store.saved.single().endMs)

        // El proceso murió con la sesión abierta: se cierra en su último sonido.
        val restored = tracker(store).sessions.value.single()
        assertEquals(3_000L, restored.endMs)
        assertEquals(listOf("d1"), restored.events.map { it.id })
    }

    @Test
    fun `se conservan las sesiones mas recientes`() {
        val tracker = SessionTracker(clock = { now }, maxSessions = 3, minEmptySessionMs = 0)
        repeat(5) {
            now += 1_000
            tracker.update(listening = true, if (it % 2 == 0) CASA else CALLE, emptyList(), emptyList())
        }
        assertEquals(3, tracker.sessions.value.size)
        assertEquals(now, tracker.sessions.value.last().startMs)
    }

    @Test
    fun `el formato del archivo conserva todo, lee el anterior y descarta lo desconocido`() {
        val sessions = listOf(
            Session(
                "s1",
                CASA,
                1_000,
                2_000,
                listOf(
                    SessionEvent("d1", SoundCategory.DOORBELL, Priority.INFORMATION, 1_500, acknowledged = true, alertId = "a1"),
                    SessionEvent("d2", SoundCategory.BELL, null, 1_700, acknowledged = false),
                ),
            ),
            Session("s2", CALLE, 2_000, null, emptyList()),
        )
        assertEquals(sessions, SessionJson.decode(SessionJson.encode(sessions)))

        // Formato anterior: solo alertas, sin "id" de detección.
        val previous = """{"sessions":[{"id":"y","context":"CASA","startMs":1,"endMs":2,"events":[{"alertId":"a","category":"SIREN","priority":"DANGER","atMs":1,"acknowledged":true}]}]}"""
        assertEquals(listOf(SessionEvent("a", SoundCategory.SIREN, Priority.DANGER, 1, true, "a")), SessionJson.decode(previous).single().events)

        val unknown = """{"sessions":[{"id":"x","context":"MARTE","startMs":1,"endMs":null,"events":[]},""" +
            """{"id":"y","context":"CASA","startMs":1,"endMs":2,"events":[{"id":"d","category":"NO_EXISTE","priority":"DANGER","atMs":1,"acknowledged":false}]}]}"""
        assertEquals(listOf(Session("y", CASA, 1, 2, emptyList())), SessionJson.decode(unknown))
    }
}
