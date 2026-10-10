package com.soundalert.wear.sync

import android.util.AtomicFile
import android.util.Log
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Algo que el reloj ya hizo localmente y falta contar al backend. */
sealed interface SyncOperation {

    /** El usuario cambió de contexto. Solo importa el último. */
    data class SetContext(val context: SoundAlertContext) : SyncOperation

    /** Detección confirmada en el reloj (alertara o no), con su contexto congelado. */
    data class UploadDetection(
        /** ContextualDetection.id: enlaza la alerta local con la del backend. */
        val detectionId: String,
        val category: SoundCategory,
        val confidence: Float,
        val context: SoundAlertContext,
        val detectedAtMs: Long,
    ) : SyncOperation

    /** El usuario confirmó la alerta generada por esta detección. */
    data class AcknowledgeAlert(val detectionId: String) : SyncOperation

    fun toJson(): JSONObject = when (this) {
        is SetContext -> JSONObject().put("type", "context").put("context", context.name)
        is UploadDetection -> JSONObject()
            .put("type", "detection")
            .put("detectionId", detectionId)
            .put("category", category.name)
            .put("confidence", confidence.toDouble())
            .put("context", context.name)
            .put("detectedAtMs", detectedAtMs)
        is AcknowledgeAlert -> JSONObject().put("type", "ack").put("detectionId", detectionId)
    }

    companion object {
        /** null si la operación guardada ya no es válida (p. ej. una categoría que ya no existe). */
        fun fromJson(json: JSONObject): SyncOperation? = try {
            when (json.getString("type")) {
                "context" -> SetContext(SoundAlertContext.valueOf(json.getString("context")))
                "detection" -> UploadDetection(
                    detectionId = json.getString("detectionId"),
                    category = SoundCategory.valueOf(json.getString("category")),
                    confidence = json.getDouble("confidence").toFloat(),
                    context = SoundAlertContext.valueOf(json.getString("context")),
                    detectedAtMs = json.getLong("detectedAtMs"),
                )
                "ack" -> AcknowledgeAlert(json.getString("detectionId"))
                else -> null
            }
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Estado de la sincronización que debe sobrevivir a un reinicio del proceso:
 * la cola pendiente y qué alerta del backend corresponde a cada detección local.
 */
data class SyncSnapshot(
    val queue: List<SyncOperation> = emptyList(),
    /** ContextualDetection.id → id de la alerta en el backend (para confirmarla). */
    val remoteAlertIds: Map<String, String> = emptyMap(),
)

interface SyncStore {
    fun load(): SyncSnapshot
    fun save(snapshot: SyncSnapshot)
}

/** Implementación real: un JSON en el almacenamiento interno, escrito de forma atómica. */
class FileSyncStore(file: File) : SyncStore {

    private val file = AtomicFile(file)

    override fun load(): SyncSnapshot = try {
        val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
        val queue = json.optJSONArray("queue") ?: JSONArray()
        val alerts = json.optJSONObject("remoteAlertIds") ?: JSONObject()
        SyncSnapshot(
            queue = (0 until queue.length()).mapNotNull { SyncOperation.fromJson(queue.getJSONObject(it)) },
            remoteAlertIds = alerts.keys().asSequence().associateWith { alerts.getString(it) },
        )
    } catch (e: IOException) {
        SyncSnapshot() // primera ejecución: no hay archivo
    } catch (e: JSONException) {
        Log.w(TAG, "Cola de sincronización ilegible; se descarta", e)
        SyncSnapshot()
    }

    override fun save(snapshot: SyncSnapshot) {
        val json = JSONObject()
            .put("queue", JSONArray(snapshot.queue.map { it.toJson() }))
            .put("remoteAlertIds", JSONObject(snapshot.remoteAlertIds))
        val out = file.startWrite()
        try {
            out.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.w(TAG, "No se pudo guardar la cola de sincronización", e)
        }
    }

    private companion object {
        const val TAG = "SA/Sync"
    }
}
