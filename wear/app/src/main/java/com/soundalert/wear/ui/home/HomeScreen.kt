package com.soundalert.wear.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.service.ListeningService
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.components.Clock
import com.soundalert.wear.ui.components.ContextChip
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.Waveform
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.state.DetectionTracker
import com.soundalert.wear.ui.state.watchedSummary
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantallas A (reposo / escuchando) y B (sonido detectado), a partir del estado real
 * del pipeline. Tocar la oreja activa o pausa la escucha; el chip abre el contexto.
 */
@Composable
fun HomeScreen(onOpenContext: () -> Unit, onOpenDiagnostic: (() -> Unit)?) {
    val status by PipelineStatus.snapshot.collectAsStateWithLifecycle()
    val currentContext by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    // B se recalcula 4 veces por segundo: el pipeline produce una inferencia cada 500 ms.
    val detection by produceState<DetectionDisplay?>(null) {
        val tracker = DetectionTracker()
        while (true) {
            value = tracker.update(PipelineStatus.snapshot.value, System.currentTimeMillis())
            delay(250)
        }
    }

    WatchScreen { scale ->
        val shown = detection
        if (shown != null) {
            DetectingContent(shown, currentContext, status.dbfs, scale)
        } else {
            ListeningContent(status, currentContext, onOpenContext, onOpenDiagnostic, scale)
        }
    }
}

/** A · Reposo: casi vacía. Hora, contexto, oreja y los sonidos que se vigilan. */
@Composable
private fun BoxScope.ListeningContent(
    status: PipelineStatus.Snapshot,
    context: SoundAlertContext,
    onOpenContext: () -> Unit,
    onOpenDiagnostic: (() -> Unit)?,
    scale: WatchScale,
) = with(scale) {
    val android = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionDenied = result[Manifest.permission.RECORD_AUDIO] != true
        if (!permissionDenied) ListeningService.start(android)
    }
    val running = status.phase in setOf(Phase.STARTING, Phase.LISTENING, Phase.SILENCED)
    val toggle: () -> Unit = {
        when {
            running -> ListeningService.stop(android)
            permissions.all { android.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } -> ListeningService.start(android)
            else -> launcher.launch(permissions)
        }
    }
    val summary = remember(context) { watchedSummary(context, SoundAlertRuntime.ruleEngine, SoundUi::summaryName) }

    val (title, subtitle) = when {
        permissionDenied -> "Sin micrófono" to "Concede el permiso para escuchar"
        status.phase == Phase.LISTENING -> "Escuchando" to summary.joinToString(" · ")
        status.phase == Phase.STARTING -> "Iniciando…" to summary.joinToString(" · ")
        status.phase == Phase.SILENCED -> "Micrófono ocupado" to "Otra app está usando el micrófono"
        status.phase == Phase.ERROR -> "Error" to (status.error ?: "La escucha se detuvo")
        else -> "En pausa" to "Toca para escuchar"
    }
    val listening = status.phase == Phase.LISTENING

    Box(Modifier.align(Alignment.TopCenter).offset(y = 10.mdp)) { Clock() }
    ContextChip(context, onOpenContext, scale, Modifier.align(Alignment.TopCenter).offset(y = 27.mdp))

    // Oreja: anillo exterior + círculo. Tocar = activar / pausar.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 58.5.mdp)
            .size(69.mdp)
            .clip(CircleShape)
            .border(1.mdp, if (listening) SaColors.Inactive else SaColors.Surface, CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (running) "Pausar la escucha" else "Activar la escucha",
                onLongClick = onOpenDiagnostic,
                onClick = toggle,
            )
            .semantics { contentDescription = if (running) "Escuchando. Toca para pausar" else "En pausa. Toca para escuchar" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(52.mdp).clip(CircleShape).background(SaColors.Surface), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (running) R.drawable.ic_ear else R.drawable.ic_ear_off),
                contentDescription = null,
                tint = if (running) SaColors.OnSurface else SaColors.Muted,
                modifier = Modifier.size(28.mdp),
            )
        }
    }

    Column(
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 140.mdp).fillMaxWidth().padding(horizontal = 22.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = SaText.title, color = SaColors.OnSurface, maxLines = 1)
        // Más abajo la esfera es más estrecha: el subtítulo va más ajustado.
        Text(
            subtitle,
            style = SaText.body.copy(fontSize = SaText.body.fontSize * 0.92f),
            color = SaColors.Secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.mdp),
        )
    }
}

/**
 * B · Clasificación: lo que la IA está oyendo, con su confianza real. "Analizando"
 * mientras el estabilizador no lo confirma; después se indica si alertará aquí.
 */
@Composable
private fun BoxScope.DetectingContent(detection: DetectionDisplay, context: SoundAlertContext, dbfs: Float, scale: WatchScale) = with(scale) {
    val rule = remember(detection.category, context) { SoundAlertRuntime.ruleEngine.match(detection.category, context) }
    val header = if (detection.confirmed) "IA · CONFIRMADO" else "IA · ANALIZANDO"
    val note = when {
        !detection.confirmed -> null
        !detection.category.alertable -> "Solo registro"
        rule == null -> "Sin alerta en ${context.label}"
        else -> null
    }
    // dBFS del bloque actual → 0..1 para la amplitud de la onda (−60 dB = mínimo).
    val level = if (dbfs.isNaN()) 0.5f else ((dbfs + 60f) / 60f).coerceIn(0f, 1f)

    Box(Modifier.align(Alignment.TopCenter).offset(y = 10.mdp)) { Clock() }
    Text(header, style = SaText.label, color = SaColors.Muted, modifier = Modifier.align(Alignment.TopCenter).offset(y = 27.mdp))
    Waveform(level, scale, Modifier.align(Alignment.TopCenter).offset(y = 59.mdp))
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 132.mdp)
            .fillMaxWidth()
            .padding(horizontal = 20.mdp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sonido detectado", style = SaText.title, color = SaColors.OnSurface, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                SoundUi.possible(detection.category),
                style = SaText.body,
                color = SaColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(5.mdp))
            Text(
                String.format(Locale.US, "%d%%", (detection.confidence * 100).roundToInt()),
                style = SaText.label.copy(letterSpacing = SaText.time.letterSpacing),
                color = SaColors.OnSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(SaColors.Track).padding(horizontal = 5.mdp, vertical = 1.5.mdp),
            )
        }
        if (note != null) Text(note, style = SaText.body, color = SaColors.Muted, maxLines = 1)
    }
}
