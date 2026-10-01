package com.soundalert.wear.alert

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority

enum class AlertStatus {
    /** Visible y sin resolver. */
    ACTIVE,

    /** El usuario la confirmó o cerró. */
    ACKNOWLEDGED,

    /** Se cerró sola por tiempo (solo ATTENTION e INFORMATION). */
    EXPIRED,
}

/** Alerta generada a partir de un evento de sonido. Queda en el historial aunque se cierre. */
data class Alert(
    val id: String,
    val category: SoundCategory,
    /** Clase original de YAMNet (p. ej. "Police car (siren)"). */
    val label: String,
    val confidence: Float,
    val priority: Priority,
    val context: SoundAlertContext,
    val createdAtMs: Long,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val closedAtMs: Long? = null,
)
