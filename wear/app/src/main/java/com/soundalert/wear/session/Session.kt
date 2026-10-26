package com.soundalert.wear.session

import android.util.AtomicFile
import android.util.Log
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Sonido detectado durante una sesión: tipo, nivel, hora y si la persona confirmó su
 * alerta. [id] es el de la detección (ContextualDetection.id). [priority] null: se
 * detectó pero no alertó (sin regla en ese contexto, enfriamiento o solo registro).
 * [alertId] enlaza con la Alert, si la hubo, para seguir su confirmación.
 */
data class SessionEvent(
    val id: String,
    val category: SoundCategory,
    val priority: Priority?,
    val atMs: Long,
    val acknowledged: Boolean,
    val alertId: String? = null,
)

/**
 * Periodo de escucha en un mismo contexto. Empieza al cambiar de contexto o al
 * reanudar la escucha y termina con el siguiente cambio (o al pausar). [endMs] null:
 * es la sesión en curso.
 */
data class Session(
    val id: String,
    val context: SoundAlertContext,
    val startMs: Long,
    val endMs: Long?,
    val events: List<SessionEvent>,
) {
    val ongoing: Boolean get() = endMs == null

    /** Fin de la sesión, o [nowMs] si sigue en curso. */
    fun endOr(nowMs: Long): Long = endMs ?: nowMs
}

/** Dónde se guardan las sesiones para que sobrevivan a un reinicio del proceso. */
interface SessionStore {
    fun load(): List<Session>
    fun save(sessions: List<Session>)
}

/** Implementación real: un JSON en los archivos privados de la app, escrito de forma atómica. */
class FileSessionStore(file: File) : SessionStore {

    private val file = AtomicFile(file)

    override fun load(): List<Session> = try {
        SessionJson.decode(file.readFully().toString(Charsets.UTF_8))
    } catch (e: IOException) {
        emptyList() // primera ejecución: no hay archivo
    } catch (e: JSONException) {
        Log.w(TAG, "Sesiones ilegibles; se descartan", e)
        emptyList()
    }

    override fun save(sessions: List<Session>) {
        val out = file.startWrite()
        try {
            out.write(SessionJson.encode(sessions).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.w(TAG, "No se pudieron guardar las sesiones", e)
        }
    }

    private companion object {
        const val TAG = "SA/Session"
    }
}

/** Formato del archivo de sesiones. Lo que ya no existe (contextos, categorías) se descarta al leer. */
object SessionJson {
    fun encode(sessions: List<Session>): String =
        JSONObject().put("sessions", JSONArray(sessions.map(::toJson))).toString()

    fun decode(text: String): List<Session> {
        val array = JSONObject(text).optJSONArray("sessions") ?: return emptyList()
        return (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
    }

    private fun toJson(session: Session) = JSONObject()
        .put("id", session.id)
        .put("context", session.context.name)
        .put("startMs", session.startMs)
        .put("endMs", session.endMs ?: JSONObject.NULL)
        .put(
            "events",
            JSONArray(
                session.events.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("alertId", it.alertId ?: JSONObject.NULL)
                        .put("category", it.category.name)
                        .put("priority", it.priority?.name ?: JSONObject.NULL)
                        .put("atMs", it.atMs)
                        .put("acknowledged", it.acknowledged)
                },
            ),
        )

    private fun fromJson(json: JSONObject): Session? {
        val context = SoundAlertContext.entries.firstOrNull { it.name == json.optString("context") } ?: return null
        val events = json.optJSONArray("events") ?: JSONArray()
        return Session(
            id = json.getString("id"),
            context = context,
            startMs = json.getLong("startMs"),
            endMs = if (json.isNull("endMs")) null else json.getLong("endMs"),
            events = (0 until events.length()).mapNotNull { i ->
                val event = events.getJSONObject(i)
                // Formato anterior: solo alertas, identificadas por su alertId.
                val alertId = if (event.isNull("alertId")) null else event.optString("alertId").ifEmpty { null }
                SessionEvent(
                    id = event.optString("id").ifEmpty { alertId ?: return@mapNotNull null },
                    category = SoundCategory.entries.firstOrNull { it.name == event.optString("category") } ?: return@mapNotNull null,
                    priority = if (event.isNull("priority")) null else Priority.entries.firstOrNull { it.name == event.optString("priority") } ?: return@mapNotNull null,
                    atMs = event.getLong("atMs"),
                    acknowledged = event.optBoolean("acknowledged"),
                    alertId = alertId,
                )
            },
        )
    }
}
