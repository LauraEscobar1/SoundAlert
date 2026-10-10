package com.soundalert.wear.history

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.api.FakeBackend
import com.soundalert.wear.api.RemoteAlert
import com.soundalert.wear.api.RemoteDetection
import com.soundalert.wear.api.SoundAlertApi
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.sync.DeviceStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {

    private val rules = RuleEngine()

    private fun detection(id: String, category: SoundCategory, context: SoundAlertContext, atMs: Long) =
        ContextualDetection(id, category, category.name, 0.8f, context, rules.match(category, context), atMs)

    private fun alert(detection: ContextualDetection, status: AlertStatus) =
        Alert("a-${detection.id}", detection.id, detection.category, detection.label, detection.confidence, detection.rule!!.priority, detection.context, detection.detectedAtMs, status)

    @Test
    fun `local - cada deteccion con su contexto y el estado real de su alerta`() {
        val siren = detection("1", SoundCategory.SIREN, SoundAlertContext.CALLE, 1_000)
        val horn = detection("2", SoundCategory.CAR_HORN, SoundAlertContext.CALLE, 2_000)
        val bell = detection("3", SoundCategory.BELL, SoundAlertContext.CASA, 3_000)
        val doorbellInStreet = detection("4", SoundCategory.DOORBELL, SoundAlertContext.CALLE, 4_000)
        val entries = HistoryRepository.fromLocal(
            listOf(doorbellInStreet, bell, horn, siren),
            listOf(alert(horn, AlertStatus.EXPIRED), alert(siren, AlertStatus.ACKNOWLEDGED)),
        ).associateBy { it.id }

        assertEquals(HistoryOutcome.ALERT_ACKNOWLEDGED, entries.getValue("1").outcome)
        assertEquals(Priority.DANGER, entries.getValue("1").priority)
        assertEquals("Calle", entries.getValue("1").contextLabel)
        assertEquals(HistoryOutcome.ALERT_EXPIRED, entries.getValue("2").outcome)
        assertEquals(HistoryOutcome.RECORD_ONLY, entries.getValue("3").outcome)
        assertNull(entries.getValue("3").priority)
        assertEquals(HistoryOutcome.NO_ALERT, entries.getValue("4").outcome)
    }

    @Test
    fun `remoto - estados del backend, categorias desconocidas y contextos historicos`() {
        val detections = listOf(
            RemoteDetection("d1", "SIREN", "Sirena", "STREET", "ALERTED", true, 3_000),
            RemoteDetection("d2", "BELL", "Campana", "HOME", "DISABLED_IN_CONTEXT", false, 2_000),
            RemoteDetection("d3", "MICROWAVE_BEEP", "Microondas", "UNIVERSITY", "ALERTED", true, 1_000),
            RemoteDetection("d4", null, null, "OTHER", "UNKNOWN_SOUND", false, 500),
        )
        val alerts = listOf(
            RemoteAlert("a1", "d1", "SIREN", "Sirena", "DANGER", "STREET", "ACKNOWLEDGED", 0.9f, 3_000),
            RemoteAlert("a3", "d3", "MICROWAVE_BEEP", "Microondas", "INFORMATION", "UNIVERSITY", "ACTIVE", 0.9f, 1_000),
        )
        val entries = HistoryRepository.fromRemote(detections, alerts).associateBy { it.id }

        assertEquals(HistoryOutcome.ALERT_ACKNOWLEDGED, entries.getValue("d1").outcome)
        assertEquals(Priority.DANGER, entries.getValue("d1").priority)
        assertEquals(HistoryOutcome.RECORD_ONLY, entries.getValue("d2").outcome)
        // El reloj no conoce MICROWAVE_BEEP: se muestra con el nombre del backend, sin inventar categoría.
        assertEquals(SoundCategory.UNKNOWN, entries.getValue("d3").category)
        assertEquals("Microondas", entries.getValue("d3").remoteName)
        assertEquals("Universidad", entries.getValue("d3").contextLabel)
        assertEquals(HistoryOutcome.ALERT_ACTIVE, entries.getValue("d3").outcome)
        assertEquals("Otro", entries.getValue("d4").contextLabel)
    }

    @Test
    fun `con backend se anaden solo las locales pendientes de envio, sin backend solo las locales`() {
        val remote = listOf(HistoryEntry("r1", SoundCategory.SIREN, "Sirena", Priority.DANGER, "Calle", 1_000, HistoryOutcome.ALERT_ACTIVE))
        val sent = HistoryEntry("l1", SoundCategory.SIREN, null, Priority.DANGER, "Calle", 1_000, HistoryOutcome.ALERT_ACTIVE)
        val pending = HistoryEntry("l2", SoundCategory.DOORBELL, null, Priority.INFORMATION, "Casa", 5_000, HistoryOutcome.ALERT_EXPIRED)

        assertEquals(listOf("l2", "r1"), HistoryRepository.merge(remote, listOf(pending, sent), setOf("l2")).map { it.id })
        assertEquals(listOf("l2", "l1"), HistoryRepository.merge(null, listOf(sent, pending), emptySet()).map { it.id })
    }

    @Test
    fun `carga remota - sin registrar, sin red y con datos`() = runTest {
        val backend = FakeBackend()
        val store = object : DeviceStore {
            var id: String? = null
            override fun load() = id
            override fun save(deviceId: String) { id = deviceId }
            override fun clear() { id = null }
        }
        val repository = HistoryRepository(SoundAlertApi(FakeBackend.BASE_URL, backend), store)
        assertTrue(repository.loadRemote() is HistoryRepository.RemoteResult.Unavailable)

        val api = SoundAlertApi(FakeBackend.BASE_URL, backend)
        store.id = (api.registerDevice("r", SoundAlertContext.CASA, 0.25f) as com.soundalert.wear.api.ApiResult.Ok).value.id
        backend.execute(
            com.soundalert.wear.api.HttpRequest(
                "POST",
                "${FakeBackend.BASE_URL}/api/v1/devices/${store.id}/detections/classified",
                JSONObject().put("predictions", org.json.JSONArray().put(JSONObject().put("label", "DOORBELL").put("confidence", 0.7))).put("context", "HOME").toString(),
            ),
        )
        val loaded = repository.loadRemote() as HistoryRepository.RemoteResult.Loaded
        assertEquals(SoundCategory.DOORBELL, loaded.entries.single().category)
        assertEquals("Casa", loaded.entries.single().contextLabel)

        backend.online = false
        assertEquals(HistoryRepository.RemoteResult.Unavailable("sin conexión"), repository.loadRemote())
    }
}
