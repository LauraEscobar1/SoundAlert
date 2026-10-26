package com.soundalert.wear.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.service.ListeningService
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.components.Clock
import com.soundalert.wear.ui.components.ContextChip
import com.soundalert.wear.ui.components.CoreMode
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.JarvisCore
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.components.PageDots
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.state.DetectionTracker
import com.soundalert.wear.ui.state.watchedSummary
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantallas A (escuchando / reposo) y B (analizando), a partir del estado real del
 * pipeline. Tocar el núcleo activa o pausa la escucha; el chip abre el contexto.
 */
@Composable
fun HomeScreen(onOpenContext: () -> Unit, onOpenDiagnostic: (() -> Unit)?, pageDots: Pair<Int, Int>? = null) {
    val android = LocalContext.current
    val status by PipelineStatus.snapshot.collectAsStateWithLifecycle()
    val currentContext by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val toggles by SoundAlertRuntime.soundToggles.disabled.collectAsStateWithLifecycle()
    // B se recalcula 4 veces por segundo: el pipeline produce una inferencia cada 500 ms.
    val detection by produceState<DetectionDisplay?>(null) {
        val tracker = DetectionTracker()
        while (true) {
            value = tracker.update(PipelineStatus.snapshot.value, System.currentTimeMillis())
            delay(250)
        }
    }

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

    val shown = detection
    if (shown != null) {
        val rule = remember(shown.category, currentContext, toggles) { SoundAlertRuntime.ruleEngine.match(shown.category, currentContext) }
        val note = when {
            !shown.confirmed -> null
            !shown.category.alertable -> "Solo registro"
            rule == null -> "Sin alerta en ${currentContext.label}"
            else -> null
        }
        AnalyzingContent(shown, note, pageDots)
    } else {
        val summary = remember(currentContext, toggles) { watchedSummary(currentContext, SoundAlertRuntime.ruleEngine, SoundUi::summaryName) }
        ListeningContent(listeningUi(status, permissionDenied, summary), currentContext, toggle, onOpenContext, onOpenDiagnostic, pageDots)
    }
}

/** Lo que muestra A según la fase del pipeline. */
data class ListeningUi(val title: String, val subtitle: String, val running: Boolean, val listening: Boolean)

fun listeningUi(status: PipelineStatus.Snapshot, permissionDenied: Boolean, summary: List<String>): ListeningUi {
    val running = status.phase in setOf(Phase.STARTING, Phase.LISTENING, Phase.SILENCED)
    val watched = summary.joinToString(" · ").ifEmpty { "Ningún sonido activo aquí" }
    val (title, subtitle) = when {
        permissionDenied -> "Sin micrófono" to "Concede el permiso para escuchar"
        status.phase == Phase.LISTENING -> "Escuchando" to watched
        status.phase == Phase.STARTING -> "Iniciando…" to watched
        status.phase == Phase.SILENCED -> "Micrófono ocupado" to "Otra app está usando el micrófono"
        status.phase == Phase.ERROR -> "Error" to (status.error ?: "La escucha se detuvo")
        else -> "En pausa" to "Toca el núcleo para escuchar"
    }
    return ListeningUi(title, subtitle, running, listening = status.phase == Phase.LISTENING)
}

/**
 * A · Escuchando. Un único bloque centrado en la pantalla, como en el mockup: hora →
 * chip del contexto → emblema Jarvis (88 dp, 46 % del ancho) → "Escuchando" →
 * sonidos vigilados → puntitos de página. Separaciones de 3 a 6 dp.
 */
@Composable
fun ListeningContent(
    ui: ListeningUi,
    context: SoundAlertContext,
    onToggle: () -> Unit,
    onOpenContext: (() -> Unit)?,
    onOpenDiagnostic: (() -> Unit)?,
    pageDots: Pair<Int, Int>? = null,
) {
    val ambient = LocalAmbient.current
    val ink = if (ambient) SaColors.AmbientContent else SaColors.Ink
    val secondary = if (ambient) SaColors.AmbientDim else SaColors.Brown
    WatchScreen(background = if (ambient) SaColors.AmbientBackground else SaColors.Peach) { scale ->
        with(scale) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Clock(color = secondary)
                Spacer(Modifier.height(3.5.mdp))
                if (ambient) {
                    Text(context.label.uppercase(), style = SaText.labelSmall, color = secondary, maxLines = 1)
                } else {
                    ContextChip(context, onOpenContext, scale)
                }
                Spacer(Modifier.height(4.5.mdp))
                // El emblema entero (88 dp) es la zona táctil: activa o pausa la escucha.
                JarvisCore(
                    if (ui.listening) CoreMode.LISTENING else CoreMode.PAUSED,
                    Modifier
                        .size(EMBLEM.mdp)
                        .clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = if (ui.running) "Pausar la escucha" else "Activar la escucha",
                            onLongClick = onOpenDiagnostic,
                            onClick = onToggle,
                        )
                        .semantics { contentDescription = if (ui.running) "Escuchando. Toca para pausar" else "En pausa. Toca para escuchar" },
                )
                Spacer(Modifier.height(4.mdp))
                FitText(ui.title, SaText.title, ink, Modifier.width(120.mdp))
                Spacer(Modifier.height(1.mdp))
                FitText(ui.subtitle, SaText.body, secondary, Modifier.width(100.mdp), maxLines = 2)
                if (pageDots != null && !ambient) {
                    Spacer(Modifier.height(5.mdp))
                    PageDots(pageDots.first, pageDots.second, scale)
                }
            }
        }
    }
}

/**
 * B · Analizando: el mismo bloque, con "IA · ANALIZANDO" en lugar del chip y el emblema
 * más rápido (9 barras). Lo que la IA está oyendo con su confianza real; "IA ·
 * CONFIRMADO" cuando el estabilizador ya lo confirmó. Por debajo del umbral de su
 * categoría no se dispara ninguna alerta (lo decide el estabilizador).
 */
@Composable
fun AnalyzingContent(detection: DetectionDisplay, note: String?, pageDots: Pair<Int, Int>? = null) {
    val ambient = LocalAmbient.current
    val ink = if (ambient) SaColors.AmbientContent else SaColors.Ink
    val secondary = if (ambient) SaColors.AmbientDim else SaColors.Brown
    WatchScreen(background = if (ambient) SaColors.AmbientBackground else SaColors.Peach) { scale ->
        with(scale) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Clock(color = secondary)
                Spacer(Modifier.height(3.5.mdp))
                Box(Modifier.height(8.8.mdp), contentAlignment = Alignment.Center) {
                    Text(if (detection.confirmed) "IA · CONFIRMADO" else "IA · ANALIZANDO", style = SaText.labelSmall, color = secondary, maxLines = 1)
                }
                Spacer(Modifier.height(4.5.mdp))
                JarvisCore(CoreMode.ANALYZING, Modifier.size(EMBLEM.mdp))
                Spacer(Modifier.height(4.mdp))
                Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    FitText("Sonido detectado", SaText.title, ink, Modifier.width(130.mdp))
                    Spacer(Modifier.height(1.mdp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitText(SoundUi.possible(detection.category), SaText.body, secondary, Modifier.widthIn(max = 80.mdp))
                        Spacer(Modifier.width(3.mdp))
                        // Chip de tinta con la confianza en durazno (8,9:1).
                        Text(
                            String.format(Locale.US, "%d%%", (detection.confidence * 100).roundToInt()),
                            style = SaText.labelSmall.copy(letterSpacing = 0.04.em),
                            color = if (ambient) SaColors.AmbientBackground else SaColors.Peach,
                            maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(ink).padding(horizontal = 2.8.mdp, vertical = 1.mdp),
                        )
                    }
                    if (note != null) FitText(note, SaText.body, secondary, Modifier.width(100.mdp))
                }
                if (pageDots != null && !ambient) {
                    Spacer(Modifier.height(5.mdp))
                    PageDots(pageDots.first, pageDots.second, scale)
                }
            }
        }
    }
}

/** Lado del emblema Jarvis sobre 192 (46 % del ancho). */
private const val EMBLEM = 88f
