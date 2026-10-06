package com.soundalert.wear.context

import android.util.Log
import com.soundalert.wear.alert.AlertManager
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.RuleEngine
import java.util.Locale
import java.util.UUID

/**
 * Clasificación contextual: categoría ya reconocida (YAMNet → LabelMapper →
 * DetectionStabilizer) + contexto activo → regla. No es otra IA.
 *
 * Es el ÚNICO punto que lee el contexto activo para una detección, y lo lee una
 * sola vez: la detección, su entrada en el historial y su alerta comparten
 * exactamente el mismo contexto aunque el usuario lo cambie justo después.
 *
 * Flujo: DetectionEvent → [ContextualDetection] → DetectionHistory → AlertManager.
 */
class ContextualClassifier(
    private val contextManager: ContextManager,
    private val rules: RuleEngine,
    private val history: DetectionHistory,
    private val alerts: AlertManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Recibe los eventos del estabilizador (AudioPipeline.onEvent). */
    fun onEvent(event: DetectionEvent) {
        when (event) {
            is DetectionEvent.Started -> onStarted(event)
            is DetectionEvent.Ended -> alerts.onSoundEnded(event)
        }
    }

    private fun onStarted(event: DetectionEvent.Started) {
        if (!event.category.known) {
            Log.w(TAG, "Evento UNKNOWN descartado (no debería llegar aquí)")
            return
        }
        val context = contextManager.current // se congela aquí
        val rule = if (event.category.alertable) rules.match(event.category, context) else null
        val detection = ContextualDetection(
            id = UUID.randomUUID().toString(),
            category = event.category,
            label = event.label,
            confidence = event.confidence,
            context = context,
            rule = rule,
            detectedAtMs = clock(),
        )
        Log.i(
            TAG,
            String.format(Locale.US, "%s + %s confidence=%.2f id=%s", detection.category, context, detection.confidence, detection.id),
        )
        when {
            detection.recordOnly -> Unit // el AlertManager lo anota como REGISTRO
            rule == null -> Log.i(RULE_TAG, "${event.category} + $context -> sin regla (no relevante, se ignora)")
            else -> Log.i(RULE_TAG, "${event.category} + $context -> ${rule.priority}")
        }
        history.record(detection)
        alerts.onDetection(detection)
    }

    private companion object {
        const val TAG = "SA/Context"
        const val RULE_TAG = "SA/Rule"
    }
}
