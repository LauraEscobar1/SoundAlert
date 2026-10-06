package com.soundalert.wear.alert

import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.context.ContextManager
import com.soundalert.wear.context.ContextualClassifier
import com.soundalert.wear.context.DetectionHistory
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.RuleEngine
import com.soundalert.wear.vibration.AlertVibrator
import kotlinx.coroutines.CoroutineScope

/**
 * Ensambla el tramo de producción ContextualClassifier → DetectionHistory →
 * AlertManager para los tests, igual que SoundAlertRuntime. Recibe los eventos del
 * estabilizador como lo hace AudioPipeline.onEvent.
 */
class AlertHarness(
    contextManager: ContextManager,
    rules: RuleEngine,
    vibrator: AlertVibrator,
    config: AlertConfig,
    scope: CoroutineScope,
    clock: () -> Long,
) {
    val history = DetectionHistory()
    val manager = AlertManager(vibrator, config, scope, clock)
    val classifier = ContextualClassifier(contextManager, rules, history, manager, clock)

    fun onDetection(event: DetectionEvent) = classifier.onEvent(event)

    val alerts get() = manager.alerts
    val activeAlerts get() = manager.activeAlerts

    fun acknowledge(id: String) = manager.acknowledge(id)
}
