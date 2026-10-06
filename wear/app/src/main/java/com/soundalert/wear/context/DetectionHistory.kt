package com.soundalert.wear.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Historial en memoria de TODAS las detecciones confirmadas con su contexto
 * (alerten o no: sin regla en el contexto, solo registro). Más reciente primero.
 * Base para consultar después los sonidos agrupados por contexto. UNKNOWN nunca
 * llega aquí. La persistencia (Room) queda para una fase posterior.
 */
class DetectionHistory(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    private val state = MutableStateFlow<List<ContextualDetection>>(emptyList())
    val detections: StateFlow<List<ContextualDetection>> = state

    init {
        require(maxSize > 0) { "maxSize debe ser positivo" }
    }

    fun record(detection: ContextualDetection) = state.update { (listOf(detection) + it).take(maxSize) }

    /** Detecciones agrupadas por el contexto en que ocurrieron (cada grupo, más reciente primero). */
    fun byContext(): Map<SoundAlertContext, List<ContextualDetection>> = state.value.groupBy { it.context }

    companion object {
        const val DEFAULT_MAX_SIZE = 200
    }
}
