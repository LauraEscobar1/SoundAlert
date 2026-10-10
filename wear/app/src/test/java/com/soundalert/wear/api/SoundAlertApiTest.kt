package com.soundalert.wear.api

import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundAlertApiTest {

    private val backend = FakeBackend()
    private val api = SoundAlertApi(FakeBackend.BASE_URL + "/", backend)
    private val yamnet = ClassifierInfo("yamnet-litert", "classification-tflite-1")

    private fun <T> ApiResult<T>.ok(): T = (this as? ApiResult.Ok)?.value ?: throw AssertionError("Se esperaba Ok y llegó $this")

    @Test
    fun `registra el reloj como WEAR_OS con el codigo de contexto de la API`() {
        val device = api.registerDevice("SoundAlert · test", SoundAlertContext.CALLE, 0.25f).ok()

        assertEquals(listOf("POST /devices"), backend.log)
        val body = backend.bodies.single()!!
        assertEquals("WEAR_OS", body.getString("platform"))
        assertEquals("STREET", body.getString("currentContext"))
        assertEquals(0.25, body.getDouble("minConfidence"), 1e-9)
        assertEquals("STREET", device.currentContext)
        assertEquals(device, api.getDevice(device.id).ok())
    }

    @Test
    fun `la deteccion se envia con el codigo de categoria, el contexto congelado y el modelo`() {
        val id = api.registerDevice("r", SoundAlertContext.OTRO, 0.25f).ok().id
        val result = api.uploadDetection(id, DetectionUpload(SoundCategory.SIREN, 0.8999999f, SoundAlertContext.CALLE, yamnet)).ok()

        assertEquals("POST /devices/$id/detections/classified", backend.log.last())
        val body = backend.bodies.last()!!
        val prediction = body.getJSONArray("predictions").getJSONObject(0)
        assertEquals("SIREN", prediction.getString("label"))
        assertEquals(0.9, prediction.getDouble("confidence"), 1e-9)
        assertEquals("STREET", body.getString("context"))
        assertEquals("yamnet-litert", body.getString("classifierName"))
        assertTrue(result.alerted)
        assertEquals("ALERTED", result.outcome)
        assertTrue(result.alertId!!.startsWith("alert-"))
    }

    @Test
    fun `una deteccion de solo registro no trae alerta`() {
        val id = api.registerDevice("r", SoundAlertContext.CASA, 0.25f).ok().id
        val result = api.uploadDetection(id, DetectionUpload(SoundCategory.BELL, 0.6f, SoundAlertContext.CASA, yamnet)).ok()
        assertEquals(false, result.alerted)
        assertNull(result.alertId)
    }

    @Test
    fun `lee historial de detecciones y alertas con fechas ISO`() {
        val id = api.registerDevice("r", SoundAlertContext.CASA, 0.25f).ok().id
        api.uploadDetection(id, DetectionUpload(SoundCategory.DOORBELL, 0.7f, SoundAlertContext.CASA, yamnet)).ok()

        val detections = api.detections(id).ok()
        val alerts = api.alerts(id).ok()
        assertEquals("DOORBELL", detections.single().category)
        assertEquals("HOME", detections.single().context)
        assertEquals(detections.single().id, alerts.single().detectionId)
        assertEquals("ACTIVE", alerts.single().status)
        assertTrue(alerts.single().createdAtMs > 0)
        assertTrue(backend.log.last().startsWith("GET /devices/$id/alerts"))
    }

    @Test
    fun `sin red devuelve NetworkError y no lanza`() {
        backend.online = false
        val result = api.getDevice("dev-1")
        assertTrue(result is ApiResult.NetworkError)
    }

    @Test
    fun `los errores HTTP traen el codigo y el mensaje de NestJS`() {
        val result = api.getDevice("no-existe")
        assertEquals(404, (result as ApiResult.HttpError).code)
        assertTrue(result.message.contains("no encontrado"))
    }

    @Test
    fun `una respuesta 2xx ilegible es InvalidResponse`() {
        val api = SoundAlertApi(FakeBackend.BASE_URL) { HttpResponse(200, "<html>proxy</html>") }
        assertTrue(api.getDevice("dev-1") is ApiResult.InvalidResponse)
    }
}
