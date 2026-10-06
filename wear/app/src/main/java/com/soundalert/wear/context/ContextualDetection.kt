package com.soundalert.wear.context

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.rules.SoundRule

/**
 * Detección confirmada por el estabilizador + contexto activo en ese instante.
 *
 * El contexto queda CONGELADO aquí: si después el usuario cambia de contexto,
 * esta detección (y la alerta que genere) conserva el contexto original.
 *
 * [rule]: la regla de la matriz para (categoría, contexto), o null si el sonido no
 * es relevante en ese contexto o es de solo registro. Que haya regla no garantiza
 * una alerta: el AlertManager aplica deduplicación y cooldown.
 */
data class ContextualDetection(
    val id: String,
    val category: SoundCategory,
    /** Clase original de YAMNet (p. ej. "Vehicle horn, car horn, honking"). */
    val label: String,
    val confidence: Float,
    val context: SoundAlertContext,
    val rule: SoundRule?,
    val detectedAtMs: Long,
) {
    init {
        require(category.known) { "UNKNOWN no genera detecciones" }
        require(rule == null || (rule.category == category && rule.context == context)) { "La regla no corresponde a la detección" }
    }

    /** Prioridad según la matriz de reglas, o null si no alerta en este contexto. */
    val priority: Priority? get() = rule?.priority

    /** Categoría de solo registro (BELL, WARNING_SIGNAL): nunca alerta. */
    val recordOnly: Boolean get() = !category.alertable
}
