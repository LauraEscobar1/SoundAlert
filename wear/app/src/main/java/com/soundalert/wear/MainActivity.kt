package com.soundalert.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.alert.AlertStatus
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.service.ListeningService
import java.util.Locale

/**
 * PANTALLA DE DIAGNÓSTICO TEMPORAL (no es la interfaz de los mockups).
 * Sirve para pedir permisos, activar/pausar la escucha, elegir el contexto,
 * confirmar/cerrar alertas activas y ver el estado del pipeline. Activar debe
 * hacerse aquí: Android solo concede el micrófono a un foreground service
 * iniciado con la app visible.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DiagnosticScreen() } }
    }
}

@Composable
private fun DiagnosticScreen() {
    val context = LocalContext.current
    val status by PipelineStatus.snapshot.collectAsStateWithLifecycle()
    val alertManager = remember { SoundAlertRuntime.alertManager(context) }
    val alerts by alertManager.alerts.collectAsStateWithLifecycle()
    val currentContext by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val active = alerts.filter { it.status == AlertStatus.ACTIVE }.sortedBy { it.priority.ordinal }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            permissionMessage = null
            ListeningService.start(context)
        } else {
            permissionMessage = "Sin permiso de micrófono no se puede escuchar"
        }
    }

    val running = status.phase in setOf(Phase.STARTING, Phase.LISTENING, Phase.SILENCED)

    ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("SoundAlert · diagnóstico", style = MaterialTheme.typography.titleSmall) }
        // Alertas activas primero: DANGER exige confirmación explícita.
        active.forEach { alert ->
            item { Text(alertText(alert), color = priorityColor(alert.priority), textAlign = TextAlign.Center) }
            item {
                Button(onClick = { alertManager.acknowledge(alert.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (alert.priority == Priority.DANGER) "Confirmar" else "Cerrar")
                }
            }
        }
        item { Text(phaseText(status.phase), color = phaseColor(status.phase), textAlign = TextAlign.Center) }
        item {
            Button(
                onClick = {
                    if (running) {
                        ListeningService.stop(context)
                    } else if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                        ListeningService.start(context)
                    } else {
                        launcher.launch(permissions.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "Pausar" else "Activar") }
        }
        item { Text("Contexto: ${currentContext.label}", style = MaterialTheme.typography.labelSmall) }
        SoundAlertContext.ACTIVE.forEach { option ->
            item {
                Button(
                    onClick = { SoundAlertRuntime.contextManager.set(option) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (option == currentContext) "✓ ${option.label}" else option.label) }
            }
        }
        (permissionMessage ?: status.error)?.let { item { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) } }
        if (running) {
            item { Text(String.format(Locale.US, "Nivel %.0f dBFS · fondo %.0f", status.dbfs, status.noiseFloorDbfs), style = MaterialTheme.typography.bodySmall) }
            item { Text("Último evento", style = MaterialTheme.typography.labelSmall) }
            item { Text(status.lastEvent?.let(::eventText) ?: "—", textAlign = TextAlign.Center) }
            if (status.activeCategories.isNotEmpty()) {
                item { Text("Sonando: ${status.activeCategories.joinToString()}", style = MaterialTheme.typography.bodySmall) }
            }
            item { Text("YAMNet ahora", style = MaterialTheme.typography.labelSmall) }
            status.topLabels.forEach { top ->
                item { Text(String.format(Locale.US, "%s %.2f", top.label, top.score), style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Text(
                    String.format(Locale.US, "%d inferencias · %.1f ms · %d descartadas", status.inferences, status.avgInferenceMs, status.droppedWindows),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun alertText(alert: Alert) = String.format(
    Locale.US,
    "%s · %s %.2f\n%s · %s",
    alert.priority,
    alert.category,
    alert.confidence,
    alert.label,
    alert.context.label,
)

@Composable
private fun priorityColor(priority: Priority) = when (priority) {
    Priority.DANGER -> MaterialTheme.colorScheme.error
    Priority.ATTENTION -> MaterialTheme.colorScheme.tertiary
    Priority.INFORMATION -> MaterialTheme.colorScheme.primary
}

private fun phaseText(phase: Phase) = when (phase) {
    Phase.IDLE -> "En pausa"
    Phase.STARTING -> "Iniciando…"
    Phase.LISTENING -> "Escuchando"
    Phase.SILENCED -> "Micrófono ocupado por otra app"
    Phase.ERROR -> "Error"
}

@Composable
private fun phaseColor(phase: Phase) = when (phase) {
    Phase.LISTENING -> MaterialTheme.colorScheme.primary
    Phase.SILENCED, Phase.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun eventText(event: DetectionEvent) = when (event) {
    is DetectionEvent.Started -> String.format(Locale.US, "%s %.2f", event.category, event.confidence)
    is DetectionEvent.Ended -> "Fin ${event.category} (${event.durationMs / 1000.0} s)"
}
