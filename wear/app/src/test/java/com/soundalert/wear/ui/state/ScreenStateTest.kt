package com.soundalert.wear.ui.state

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.classifier.LabelScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.ui.SoundUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ScreenStateTest {

    private val now = 100_000L

    private fun listening(top: List<LabelScore> = emptyList(), event: DetectionEvent? = null, eventAtMs: Long = 0) =
        PipelineStatus.Snapshot(phase = Phase.LISTENING, topLabels = top, lastEvent = event, lastEventWallClockMs = eventAtMs)

    // ---------- B · Sonido detectado ----------

    @Test
    fun `B muestra el sonido confirmado por el estabilizador con su confianza real`() {
        val tracker = DetectionTracker()
        val started = DetectionEvent.Started(SoundCategory.SIREN, 0.94f, "Siren", fastPath = true, atMs = 0)
        assertEquals(DetectionDisplay(SoundCategory.SIREN, 0.94f, confirmed = true), tracker.update(listening(event = started, eventAtMs = now - 500), now))
        // Pasados 3 s sin más señal, vuelve a reposo.
        assertNull(tracker.update(listening(event = started, eventAtMs = now - 3_500), now))
    }

    @Test
    fun `B analizando - clase conocida de YAMNet por encima del umbral de fin, aun sin confirmar`() {
        val tracker = DetectionTracker()
        val shown = tracker.update(listening(listOf(LabelScore("Speech", 0.9f), LabelScore("Bark", 0.4f))), now)
        assertEquals(DetectionDisplay(SoundCategory.DOG_BARK, 0.4f, confirmed = false), shown)
    }

    @Test
    fun `B nunca muestra UNKNOWN - voz, musica, Dog generico o puntuaciones bajas`() {
        val tracker = DetectionTracker()
        assertNull(tracker.update(listening(listOf(LabelScore("Speech", 0.95f), LabelScore("Music", 0.6f))), now))
        assertNull(tracker.update(listening(listOf(LabelScore("Dog", 0.9f), LabelScore("Animal", 0.8f))), now))
        assertNull(tracker.update(listening(listOf(LabelScore("Car passing by", 0.9f), LabelScore("Microwave oven", 0.8f))), now))
        assertNull(tracker.update(listening(listOf(LabelScore("Siren", 0.10f))), now))
    }

    @Test
    fun `B mantiene el candidato un momento para no parpadear y se oculta al pausar`() {
        val tracker = DetectionTracker()
        tracker.update(listening(listOf(LabelScore("Siren", 0.3f))), now)
        assertEquals(SoundCategory.SIREN, tracker.update(listening(), now + 1_000)?.category)
        assertNull(tracker.update(listening(), now + 2_000))

        tracker.update(listening(listOf(LabelScore("Siren", 0.3f))), now)
        assertNull(tracker.update(PipelineStatus.Snapshot(phase = Phase.IDLE), now + 100))
    }

    // ---------- C · Alerta en pantalla ----------

    private fun alert(id: String, priority: Priority, createdAtMs: Long, status: AlertStatus = AlertStatus.ACTIVE) =
        Alert(id, "d-$id", SoundCategory.SIREN, "Siren", 0.9f, priority, SoundAlertContext.CALLE, createdAtMs, status)

    @Test
    fun `C muestra la alerta activa mas urgente y, a igualdad, la mas reciente`() {
        val alerts = listOf(
            alert("info", Priority.INFORMATION, 30),
            alert("danger-old", Priority.DANGER, 10),
            alert("danger-new", Priority.DANGER, 20),
            alert("danger-ack", Priority.DANGER, 40, AlertStatus.ACKNOWLEDGED),
        )
        assertEquals("danger-new", alertOnScreen(alerts)?.id)
        assertNull(alertOnScreen(listOf(alert("x", Priority.ATTENTION, 1, AlertStatus.EXPIRED))))
    }

    // ---------- E · Sonidos del contexto (reglas reales, solo lectura) ----------

    @Test
    fun `E lista el catalogo actual del reloj con las reglas reales del contexto`() {
        val rules = RuleEngine()
        for (context in SoundAlertContext.ACTIVE) {
            val rows = soundRows(context, rules)
            assertEquals(SoundCategory.KNOWN.toSet(), rows.map { it.category }.toSet())
            assertEquals(rows.size, rows.map { it.category }.distinct().size)
            // Peligro primero y siempre activo; solo registro al final.
            assertEquals(listOf(SoundCategory.SIREN, SoundCategory.FIRE_ALARM, SoundCategory.SMOKE_ALARM), rows.take(3).map { it.category })
            assertTrue(rows.take(3).all { it.priority == Priority.DANGER })
            assertEquals(listOf(SoundCategory.BELL, SoundCategory.WARNING_SIGNAL), rows.takeLast(2).map { it.category })
            assertTrue(rows.takeLast(2).all { it.recordOnly && !it.enabled })
            for (row in rows) assertEquals("$context ${row.category}", rules.match(row.category, context)?.priority, row.priority)
        }
        val street = soundRows(SoundAlertContext.CALLE, rules).associateBy { it.category }
        assertEquals(Priority.ATTENTION, street.getValue(SoundCategory.CAR_HORN).priority)
        assertEquals(false, street.getValue(SoundCategory.DOORBELL).enabled)
    }

    @Test
    fun `A resume los sonidos vigilados en cada contexto`() {
        val rules = RuleEngine()
        assertEquals(listOf("Timbre", "Alarma", "Bebé"), watchedSummary(SoundAlertContext.CASA, rules, SoundUi::summaryName))
        assertEquals(listOf("Bocina", "Alarma", "Bicicleta"), watchedSummary(SoundAlertContext.CALLE, rules, SoundUi::summaryName))
        assertEquals(listOf("Timbre", "Bocina", "Alarma"), watchedSummary(SoundAlertContext.OTRO, rules, SoundUi::summaryName))
    }

    // ---------- Formatos de hora ----------

    @Test
    fun `hora del historial - formato 12 o 24 h del reloj hoy, dia y mes otro dia`() {
        val today = Instant.parse("2026-10-07T18:30:00Z").toEpochMilli()
        assertEquals("16:12", historyTime(Instant.parse("2026-10-07T16:12:00Z").toEpochMilli(), today, is24Hour = true, zone = ZoneOffset.UTC))
        assertEquals("4:12 PM", historyTime(Instant.parse("2026-10-07T16:12:00Z").toEpochMilli(), today, is24Hour = false, zone = ZoneOffset.UTC))
        assertEquals("06/10", historyTime(Instant.parse("2026-10-06T23:59:00Z").toEpochMilli(), today, is24Hour = true, zone = ZoneOffset.UTC))
        assertTrue(isToday(Instant.parse("2026-10-07T00:00:00Z").toEpochMilli(), today, ZoneOffset.UTC))
        assertEquals("ahora", relativeTime(today - 30_000, today))
        assertEquals("hace 2 min", relativeTime(today - 150_000, today))
    }
}
