package com.soundalert.wear.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/**
 * Backend SoundAlert simulado en memoria con el mismo contrato que la API real
 * (rutas, códigos y JSON de NestJS). Registra cada petición en [log].
 */
class FakeBackend : HttpTransport {

    /** false = sin red: cada petición lanza IOException, como un timeout real. */
    var online = true

    /** Peticiones recibidas: "POST /devices", "PUT /devices/dev-1/context"… */
    val log = mutableListOf<String>()
    val bodies = mutableListOf<JSONObject?>()

    val devices = linkedMapOf<String, JSONObject>()
    val detections = mutableListOf<JSONObject>()
    val alerts = mutableListOf<JSONObject>()

    /** Respuestas forzadas para la siguiente petición cuya ruta empiece así (método + ruta). */
    private val forced = mutableListOf<Pair<String, Int>>()

    fun failNext(prefix: String, code: Int) {
        forced += prefix to code
    }

    private var nextId = 1
    private var clockMs = Instant.parse("2026-10-07T10:00:00Z").toEpochMilli()

    override fun execute(request: HttpRequest): HttpResponse {
        if (!online) throw IOException("failed to connect (sin red)")
        val path = request.url.substringAfter("/api/v1")
        val route = "${request.method} ${path.substringBefore('?')}"
        log += route
        bodies += request.body?.let(::JSONObject)
        forced.firstOrNull { route.startsWith(it.first) }?.let {
            forced.remove(it)
            return error(it.second, "forzado por el test")
        }
        val parts = path.substringBefore('?').trim('/').split('/')
        val body = request.body?.let(::JSONObject)
        return when {
            route == "POST /devices" -> {
                val id = "dev-${nextId++}"
                val device = JSONObject()
                    .put("id", id)
                    .put("name", body!!.getString("name"))
                    .put("platform", body.getString("platform"))
                    .put("currentContext", body.getString("currentContext"))
                    .put("minConfidence", body.getDouble("minConfidence"))
                    .put("effectiveMinConfidence", body.getDouble("minConfidence"))
                devices[id] = device
                HttpResponse(201, device.toString())
            }
            parts.size >= 2 && parts[0] == "devices" && parts[1] !in devices -> error(404, "Dispositivo ${parts[1]} no encontrado")
            request.method == "GET" && parts.size == 2 -> HttpResponse(200, devices.getValue(parts[1]).toString())
            request.method == "PUT" && parts.getOrNull(2) == "context" -> {
                devices.getValue(parts[1]).put("currentContext", body!!.getString("currentContext"))
                HttpResponse(200, JSONObject().put("deviceId", parts[1]).put("context", body.getString("currentContext")).toString())
            }
            request.method == "POST" && parts.getOrNull(3) == "classified" -> classified(parts[1], body!!)
            request.method == "POST" && parts.getOrNull(4) == "ack" -> {
                val alert = alerts.firstOrNull { it.getString("id") == parts[3] } ?: return error(404, "Alerta no encontrada")
                alert.put("status", "ACKNOWLEDGED")
                HttpResponse(201, alert.toString())
            }
            request.method == "GET" && parts.getOrNull(2) == "detections" ->
                HttpResponse(200, JSONArray(detections.filter { it.getString("deviceId") == parts[1] }.reversed()).toString())
            request.method == "GET" && parts.getOrNull(2) == "alerts" ->
                HttpResponse(200, JSONArray(alerts.filter { it.getString("deviceId") == parts[1] }.reversed()).toString())
            else -> error(404, "Cannot ${request.method} $path")
        }
    }

    /** Como el backend: las categorías de solo registro no alertan; el resto sí. */
    private fun classified(deviceId: String, body: JSONObject): HttpResponse {
        val prediction = body.getJSONArray("predictions").getJSONObject(0)
        val category = prediction.getString("label")
        val createdAt = Instant.ofEpochMilli(clockMs++).toString()
        val detectionId = "det-${nextId++}"
        val alerted = category !in setOf("BELL", "WARNING_SIGNAL")
        val classification = JSONObject().put("label", category).put("category", category).put("soundName", category).put("confidence", prediction.getDouble("confidence"))
        detections += JSONObject()
            .put("id", detectionId)
            .put("deviceId", deviceId)
            .put("context", body.getString("context"))
            .put("classification", classification)
            .put("outcome", if (alerted) "ALERTED" else "DISABLED_IN_CONTEXT")
            .put("alerted", alerted)
            .put("createdAt", createdAt)
        val alert = if (alerted) {
            JSONObject()
                .put("id", "alert-${nextId++}")
                .put("deviceId", deviceId)
                .put("detectionId", detectionId)
                .put("status", "ACTIVE")
                .put("category", category)
                .put("soundName", category)
                .put("priority", JSONObject().put("level", "DANGER"))
                .put("context", body.getString("context"))
                .put("confidence", prediction.getDouble("confidence"))
                .put("createdAt", createdAt)
                .also { alerts += it }
        } else {
            null
        }
        val response = JSONObject()
            .put("detectionId", detectionId)
            .put("alerted", alerted)
            .put("outcome", if (alerted) "ALERTED" else "DISABLED_IN_CONTEXT")
            .put("alert", alert ?: JSONObject.NULL)
        return HttpResponse(201, response.toString())
    }

    private fun error(code: Int, message: String) =
        HttpResponse(code, JSONObject().put("statusCode", code).put("message", message).toString())

    companion object {
        const val BASE_URL = "https://soundalert.test"
    }
}
