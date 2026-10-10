package com.soundalert.wear.api

import com.soundalert.wear.classifier.ClassifierInfo
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

/** Resultado de una llamada a la API. Nunca lanza: el llamante decide si reintenta. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** El servidor respondió con un código de error (4xx / 5xx). */
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>

    /** Sin red, sin respuesta o tiempo agotado: se puede reintentar más tarde. */
    data class NetworkError(val message: String) : ApiResult<Nothing>

    /** Respuesta 2xx que no se pudo leer. Reintentar no la arreglaría. */
    data class InvalidResponse(val message: String) : ApiResult<Nothing>
}

data class RemoteDevice(val id: String, val name: String, val currentContext: String, val effectiveMinConfidence: Float)

/** Detección ya decidida en el reloj: el backend solo la registra (POST …/detections/classified). */
data class DetectionUpload(
    val category: SoundCategory,
    val confidence: Float,
    val context: SoundAlertContext,
    val classifier: ClassifierInfo,
)

data class UploadedDetection(val detectionId: String, val alerted: Boolean, val outcome: String, val alertId: String?)

data class RemoteAlert(
    val id: String,
    val detectionId: String,
    val category: String,
    val soundName: String,
    /** DANGER, ATTENTION o INFORMATION. */
    val priority: String,
    val context: String,
    /** ACTIVE o ACKNOWLEDGED. */
    val status: String,
    val confidence: Float,
    val createdAtMs: Long,
)

data class RemoteDetection(
    val id: String,
    /** null si el backend no pudo clasificarla. */
    val category: String?,
    val soundName: String?,
    val context: String,
    /** ALERTED, DISABLED_IN_CONTEXT, COOLDOWN, LOW_CONFIDENCE… */
    val outcome: String,
    val alerted: Boolean,
    val createdAtMs: Long,
)

/**
 * Cliente de la API REST de SoundAlert (NestJS en Railway). Solo usa endpoints que
 * ya existen en el backend; no conoce ninguna clave (el reloj no lleva secretos).
 *
 * Las llamadas son bloqueantes (ver [HttpTransport]): usar fuera del hilo principal.
 */
class SoundAlertApi(baseUrl: String, private val transport: HttpTransport) {

    private val root = baseUrl.trimEnd('/') + "/api/v1"

    /** POST /devices. El reloj se registra una sola vez y guarda el id (ver BackendSync). */
    fun registerDevice(name: String, context: SoundAlertContext, minConfidence: Float): ApiResult<RemoteDevice> {
        val body = JSONObject()
            .put("name", name)
            .put("platform", "WEAR_OS")
            .put("currentContext", apiCode(context))
            .put("minConfidence", round(minConfidence))
        return call("POST", "/devices", body) { parseDevice(JSONObject(it)) }
    }

    /** GET /devices/:id. 404 si el dispositivo ya no existe en el backend. */
    fun getDevice(deviceId: String): ApiResult<RemoteDevice> =
        call("GET", "/devices/$deviceId") { parseDevice(JSONObject(it)) }

    /** PUT /devices/:id/context. */
    fun setContext(deviceId: String, context: SoundAlertContext): ApiResult<Unit> =
        call("PUT", "/devices/$deviceId/context", JSONObject().put("currentContext", apiCode(context))) { }

    /**
     * POST /devices/:id/detections/classified. Se envía el código de la categoría
     * (p. ej. "SIREN"): el backend lo acepta tal cual, así registra exactamente lo
     * que decidió el reloj sin volver a mapear la etiqueta de YAMNet.
     */
    fun uploadDetection(deviceId: String, detection: DetectionUpload): ApiResult<UploadedDetection> {
        val prediction = JSONObject()
            .put("label", detection.category.name)
            .put("confidence", round(detection.confidence))
        val body = JSONObject()
            .put("predictions", JSONArray().put(prediction))
            .put("context", apiCode(detection.context))
            .put("classifierName", detection.classifier.name)
            .put("classifierVersion", detection.classifier.version)
        return call("POST", "/devices/$deviceId/detections/classified", body) {
            val json = JSONObject(it)
            UploadedDetection(
                detectionId = json.getString("detectionId"),
                alerted = json.getBoolean("alerted"),
                outcome = json.getString("outcome"),
                alertId = json.optJSONObject("alert")?.getString("id"),
            )
        }
    }

    /** POST /devices/:id/alerts/:alertId/ack. */
    fun acknowledgeAlert(deviceId: String, alertId: String): ApiResult<Unit> =
        call("POST", "/devices/$deviceId/alerts/$alertId/ack") { }

    /** GET /devices/:id/alerts (más reciente primero). */
    fun alerts(deviceId: String, limit: Int = 50): ApiResult<List<RemoteAlert>> =
        call("GET", "/devices/$deviceId/alerts?limit=$limit") { body ->
            JSONArray(body).objects().map { json ->
                RemoteAlert(
                    id = json.getString("id"),
                    detectionId = json.getString("detectionId"),
                    category = json.getString("category"),
                    soundName = json.optString("soundName"),
                    priority = json.getJSONObject("priority").getString("level"),
                    context = json.getString("context"),
                    status = json.getString("status"),
                    confidence = json.optDouble("confidence", 0.0).toFloat(),
                    createdAtMs = parseInstant(json.getString("createdAt")),
                )
            }
        }

    /** GET /devices/:id/detections (más reciente primero). */
    fun detections(deviceId: String, limit: Int = 50): ApiResult<List<RemoteDetection>> =
        call("GET", "/devices/$deviceId/detections?limit=$limit") { body ->
            JSONArray(body).objects().map { json ->
                val classification = json.optJSONObject("classification")
                RemoteDetection(
                    id = json.getString("id"),
                    category = classification?.getString("category"),
                    soundName = classification?.optString("soundName"),
                    context = json.getString("context"),
                    outcome = json.getString("outcome"),
                    alerted = json.getBoolean("alerted"),
                    createdAtMs = parseInstant(json.getString("createdAt")),
                )
            }
        }

    private fun <T> call(method: String, path: String, body: JSONObject? = null, parse: (String) -> T): ApiResult<T> {
        val response = try {
            transport.execute(HttpRequest(method, root + path, body?.toString()))
        } catch (e: IOException) {
            return ApiResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
        if (response.code !in 200..299) return ApiResult.HttpError(response.code, errorMessage(response.body))
        return try {
            ApiResult.Ok(parse(response.body))
        } catch (e: JSONException) {
            ApiResult.InvalidResponse(e.message ?: "JSON no válido")
        } catch (e: DateTimeParseException) {
            ApiResult.InvalidResponse(e.message ?: "Fecha no válida")
        }
    }

    private fun parseDevice(json: JSONObject) = RemoteDevice(
        id = json.getString("id"),
        name = json.optString("name"),
        currentContext = json.optString("currentContext"),
        effectiveMinConfidence = json.optDouble("effectiveMinConfidence", Double.NaN).toFloat(),
    )

    private companion object {
        /** Solo los contextos activos tienen código (HOME, STREET, OTHER). */
        fun apiCode(context: SoundAlertContext): String =
            requireNotNull(context.apiCode) { "$context no existe en la API" }

        /** 0.8999999761581421 → 0.9: el float del reloj no tiene más precisión real. */
        fun round(value: Float): Double = Math.round(value * 10_000.0) / 10_000.0

        fun parseInstant(value: String): Long = Instant.parse(value).toEpochMilli()

        fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

        /** NestJS: {"statusCode":404,"message":"…"} o "message": ["…", "…"] en validación. */
        fun errorMessage(body: String): String = try {
            val json = JSONObject(body)
            json.optJSONArray("message")?.let { messages -> (0 until messages.length()).joinToString("; ") { messages.getString(it) } }
                ?: json.optString("message", body)
        } catch (e: JSONException) {
            body.take(200)
        }
    }
}
