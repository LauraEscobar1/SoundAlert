package com.soundalert.wear.sync

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.api.FakeBackend
import com.soundalert.wear.api.SoundAlertApi
import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendSyncTest {

    private class MemoryDeviceStore(var id: String? = null) : DeviceStore {
        override fun load() = id
        override fun save(deviceId: String) { id = deviceId }
        override fun clear() { id = null }
    }

    private class MemorySyncStore(var snapshot: SyncSnapshot = SyncSnapshot()) : SyncStore {
        override fun load() = snapshot
        override fun save(snapshot: SyncSnapshot) { this.snapshot = snapshot }
    }

    private val backend = FakeBackend()
    private val devices = MemoryDeviceStore()
    private val store = MemorySyncStore()
    private val detections = MutableStateFlow<List<ContextualDetection>>(emptyList())
    private val alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val context = MutableStateFlow(SoundAlertContext.CASA)
    private val rules = RuleEngine()

    private fun sync(scope: CoroutineScope) = BackendSync(
        api = SoundAlertApi(FakeBackend.BASE_URL, backend),
        devices = devices,
        store = store,
        classifier = ClassifierInfo("yamnet-litert", "classification-tflite-1"),
        registration = { DeviceRegistration("SoundAlert · test", context.value, 0.25f) },
        scope = scope,
    )

    private fun TestScope.started(): BackendSync = sync(backgroundScope).also {
        it.start(detections, alerts, context)
        runCurrent()
    }

    private var nextId = 1

    private fun detect(category: SoundCategory, ctx: SoundAlertContext = context.value): ContextualDetection {
        val detection = ContextualDetection("local-${nextId++}", category, category.name, 0.8f, ctx, rules.match(category, ctx), 1_000L)
        detections.value = listOf(detection) + detections.value
        return detection
    }

    private fun acknowledge(detection: ContextualDetection) {
        val rule = requireNotNull(detection.rule)
        alerts.value = listOf(
            Alert("a-${detection.id}", detection.id, detection.category, detection.label, detection.confidence, rule.priority, detection.context, 1_000L, AlertStatus.ACKNOWLEDGED),
        ) + alerts.value
    }

    @Test
    fun `se registra una sola vez y despues reutiliza el id guardado`() = runTest {
        started()
        assertEquals(listOf("POST /devices", "PUT /devices/dev-1/context"), backend.log)
        assertEquals("dev-1", devices.id)
        assertEquals("HOME", backend.devices.getValue("dev-1").getString("currentContext"))

        // Reinicio del proceso: mismo almacenamiento, nueva instancia.
        backend.log.clear()
        started()
        assertEquals(listOf("GET /devices/dev-1", "PUT /devices/dev-1/context"), backend.log)
        assertEquals(1, backend.devices.size)
    }

    @Test
    fun `las detecciones del reloj se registran en orden con su contexto congelado`() = runTest {
        val sync = started()
        detect(SoundCategory.DOORBELL)
        context.value = SoundAlertContext.CALLE
        detect(SoundCategory.CAR_HORN)
        runCurrent()

        val uploads = backend.bodies.filterNotNull().filter { it.has("predictions") }
        assertEquals(listOf("DOORBELL", "CAR_HORN"), uploads.map { it.getJSONArray("predictions").getJSONObject(0).getString("label") })
        assertEquals(listOf("HOME", "STREET"), uploads.map { it.getString("context") })
        assertEquals("STREET", backend.devices.getValue("dev-1").getString("currentContext"))
        assertEquals(0, sync.status.value.pending)
        assertEquals(SyncState.IDLE, sync.status.value.state)
    }

    @Test
    fun `sin internet la cola se guarda y se envia al volver la conexion`() = runTest {
        backend.online = false
        val sync = started()
        val siren = detect(SoundCategory.SIREN)
        runCurrent()

        assertEquals(SyncState.WAITING_RETRY, sync.status.value.state)
        assertEquals(2, sync.status.value.pending) // contexto + detección
        assertEquals(setOf(siren.id), sync.status.value.pendingDetectionIds)
        assertEquals(2, store.snapshot.queue.size) // persistida
        assertTrue(backend.log.isEmpty())

        backend.online = true
        advanceTimeBy(BackendSync.DEFAULT_RETRY_DELAYS_MS.last() + 1)
        runCurrent()
        assertEquals(0, sync.status.value.pending)
        assertTrue(store.snapshot.queue.isEmpty())
        assertEquals("POST /devices/dev-1/detections/classified", backend.log.last())
    }

    @Test
    fun `la cola sobrevive a un reinicio del proceso`() = runTest {
        backend.online = false
        started()
        detect(SoundCategory.SMOKE_ALARM)
        runCurrent()
        assertEquals(2, store.snapshot.queue.size)

        // Nuevo proceso: DetectionHistory vacío, la cola sale del almacenamiento.
        detections.value = emptyList()
        backend.online = true
        val restarted = started()
        assertEquals(0, restarted.status.value.pending)
        assertEquals("SMOKE_ALARM", backend.detections.single().getJSONObject("classification").getString("category"))
    }

    @Test
    fun `confirmar en el reloj confirma la alerta del backend`() = runTest {
        started()
        val doorbell = detect(SoundCategory.DOORBELL)
        runCurrent()
        acknowledge(doorbell)
        runCurrent()

        val alertId = backend.alerts.single().getString("id")
        assertEquals("POST /devices/dev-1/alerts/$alertId/ack", backend.log.last())
        assertEquals("ACKNOWLEDGED", backend.alerts.single().getString("status"))
    }

    @Test
    fun `una confirmacion sin alerta en el backend se descarta sin bloquear la cola`() = runTest {
        val sync = started()
        val bell = detect(SoundCategory.BELL) // solo registro: el backend no crea alerta
        runCurrent()
        sync.enqueue(SyncOperation.AcknowledgeAlert(bell.id))
        detect(SoundCategory.SIREN)
        runCurrent()

        assertTrue(backend.log.none { it.endsWith("/ack") })
        assertEquals(0, sync.status.value.pending)
        assertEquals(2, backend.detections.size)
    }

    @Test
    fun `si el backend ya no tiene el reloj se registra de nuevo y reintenta`() = runTest {
        devices.id = "dev-borrado"
        val sync = started()
        assertEquals(listOf("GET /devices/dev-borrado", "POST /devices", "PUT /devices/dev-1/context"), backend.log)

        // Lo borran del backend con la app abierta: el siguiente envío da 404.
        backend.devices.clear()
        detect(SoundCategory.SIREN)
        runCurrent()
        assertEquals("dev-2", devices.id)
        assertEquals(0, sync.status.value.pending)
        assertEquals("dev-2", backend.detections.single().getString("deviceId"))
    }

    @Test
    fun `un 400 descarta solo esa operacion y un 503 se reintenta`() = runTest {
        val sync = started()
        backend.failNext("POST /devices/dev-1/detections", 400)
        detect(SoundCategory.GLASS_BREAK)
        runCurrent()
        assertEquals(0, sync.status.value.pending)
        assertTrue(backend.detections.isEmpty())

        backend.failNext("POST /devices/dev-1/detections", 503)
        detect(SoundCategory.SCREAM)
        runCurrent()
        assertEquals(1, sync.status.value.pending)
        advanceTimeBy(BackendSync.DEFAULT_RETRY_DELAYS_MS.first() + 1)
        runCurrent()
        assertEquals(0, sync.status.value.pending)
        assertEquals(1, backend.detections.size)
    }

    @Test
    fun `solo cuenta el ultimo cambio de contexto pendiente`() = runTest {
        backend.online = false
        val sync = started()
        context.value = SoundAlertContext.CALLE
        runCurrent()
        context.value = SoundAlertContext.OTRO
        runCurrent()
        assertEquals(listOf<SyncOperation>(SyncOperation.SetContext(SoundAlertContext.OTRO)), store.snapshot.queue)
        assertEquals(1, sync.status.value.pending)
    }

    @Test
    fun `desactivada no encola ni llama a la red`() = runTest {
        val sync = sync(backgroundScope)
        sync.enabled = false
        sync.start(detections, alerts, context)
        detect(SoundCategory.SIREN)
        runCurrent()

        assertTrue(backend.log.isEmpty())
        assertEquals(0, sync.status.value.pending)
        assertEquals(SyncState.DISABLED, sync.status.value.state)
        assertNull(devices.id)
    }
}
