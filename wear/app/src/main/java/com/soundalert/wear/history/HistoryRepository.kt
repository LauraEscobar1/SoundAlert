package com.soundalert.wear.history

import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.api.ApiResult
import com.soundalert.wear.api.RemoteAlert
import com.soundalert.wear.api.RemoteDetection
import com.soundalert.wear.api.SoundAlertApi
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.ContextualDetection
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.sync.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Qué pasó con una detección, tal como lo ve la persona. */
enum class HistoryOutcome {
    /** Alertó y aún no se ha confirmado. */
    ALERT_ACTIVE,

    /** Alertó y la persona la confirmó. */
    ALERT_ACKNOWLEDGED,

    /** Alertó y se cerró sola (solo ATTENTION e INFORMATION, solo en el reloj). */
    ALERT_EXPIRED,

    /** Se detectó pero no alertó (sin regla en el contexto, cooldown…). */
    NO_ALERT,

    /** Categoría de solo registro (BELL, WARNING_SIGNAL): nunca alerta. */
    RECORD_ONLY,
}

data class HistoryEntry(
    val id: String,
    /** UNKNOWN si el backend devuelve una categoría que el reloj no conoce. */
    val category: SoundCategory,
    /** Nombre que da el backend; null en las entradas locales. */
    val remoteName: String?,
    /** Prioridad de la alerta; null si no alertó. */
    val priority: Priority?,
    val contextLabel: String,
    val atMs: Long,
    val outcome: HistoryOutcome,
)

/**
 * Historial de la pantalla F. Con conexión: detecciones y alertas guardadas en el
 * backend (sobreviven a reinicios). Sin conexión, o si el reloj aún no se ha
 * registrado: lo que hay en memoria en el reloj. Nunca inventa entradas.
 */
class HistoryRepository(private val api: SoundAlertApi, private val devices: DeviceStore) {

    sealed interface RemoteResult {
        data class Loaded(val entries: List<HistoryEntry>) : RemoteResult
        data class Unavailable(val reason: String) : RemoteResult
    }

    suspend fun loadRemote(limit: Int = 50): RemoteResult = withContext(Dispatchers.IO) {
        val deviceId = devices.load() ?: return@withContext RemoteResult.Unavailable("reloj aún no registrado")
        val detections = api.detections(deviceId, limit)
        val alerts = api.alerts(deviceId, limit)
        if (detections is ApiResult.Ok && alerts is ApiResult.Ok) {
            RemoteResult.Loaded(fromRemote(detections.value, alerts.value))
        } else {
            RemoteResult.Unavailable(describe(if (detections !is ApiResult.Ok) detections else alerts))
        }
    }

    companion object {
        fun fromRemote(detections: List<RemoteDetection>, alerts: List<RemoteAlert>): List<HistoryEntry> {
            val alertByDetection = alerts.associateBy { it.detectionId }
            return detections.map { detection ->
                val category = detection.category?.let(::categoryOf) ?: SoundCategory.UNKNOWN
                val alert = alertByDetection[detection.id]
                HistoryEntry(
                    id = detection.id,
                    category = category,
                    remoteName = alert?.soundName ?: detection.soundName,
                    priority = alert?.priority?.let { level -> Priority.entries.firstOrNull { it.name == level } },
                    contextLabel = contextLabel(detection.context),
                    atMs = detection.createdAtMs,
                    outcome = when {
                        alert != null && alert.status == "ACKNOWLEDGED" -> HistoryOutcome.ALERT_ACKNOWLEDGED
                        alert != null || detection.alerted -> HistoryOutcome.ALERT_ACTIVE
                        !category.alertable -> HistoryOutcome.RECORD_ONLY
                        else -> HistoryOutcome.NO_ALERT
                    },
                )
            }
        }

        fun fromLocal(detections: List<ContextualDetection>, alerts: List<Alert>): List<HistoryEntry> {
            val alertByDetection = alerts.associateBy { it.detectionId }
            return detections.map { detection ->
                val alert = alertByDetection[detection.id]
                HistoryEntry(
                    id = detection.id,
                    category = detection.category,
                    remoteName = null,
                    priority = alert?.priority,
                    contextLabel = detection.context.label,
                    atMs = detection.detectedAtMs,
                    outcome = when {
                        alert == null -> if (detection.recordOnly) HistoryOutcome.RECORD_ONLY else HistoryOutcome.NO_ALERT
                        alert.status == AlertStatus.ACTIVE -> HistoryOutcome.ALERT_ACTIVE
                        alert.status == AlertStatus.ACKNOWLEDGED -> HistoryOutcome.ALERT_ACKNOWLEDGED
                        else -> HistoryOutcome.ALERT_EXPIRED
                    },
                )
            }
        }

        /**
         * Con datos del backend: esos, más las detecciones locales que aún están en la
         * cola (todavía no llegaron al backend). Sin datos del backend: solo las locales.
         */
        fun merge(remote: List<HistoryEntry>?, local: List<HistoryEntry>, pendingIds: Set<String>): List<HistoryEntry> {
            val entries = if (remote == null) local else remote + local.filter { it.id in pendingIds }
            return entries.sortedByDescending { it.atMs }
        }

        private fun categoryOf(code: String): SoundCategory = SoundCategory.entries.firstOrNull { it.name == code } ?: SoundCategory.UNKNOWN

        /** HOME → Casa… Los contextos históricos del backend (UNIVERSITY, WORK) también se muestran. */
        private fun contextLabel(code: String): String =
            SoundAlertContext.entries.firstOrNull { it.apiCode == code }?.label ?: HISTORIC_CONTEXTS[code] ?: code

        private val HISTORIC_CONTEXTS = mapOf("UNIVERSITY" to "Universidad")

        private fun describe(result: ApiResult<*>): String = when (result) {
            is ApiResult.Ok -> "ok"
            is ApiResult.HttpError -> "error ${result.code}"
            is ApiResult.NetworkError -> "sin conexión"
            is ApiResult.InvalidResponse -> "respuesta no válida"
        }
    }
}
