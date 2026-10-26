package com.soundalert.wear.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.pipeline.PipelineStatus.Phase
import com.soundalert.wear.service.ListeningService
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.components.Clock
import com.soundalert.wear.ui.components.CoreMode
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.JarvisCore
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.state.DetectionTracker
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantallas A (escuchando / reposo) y B (analizando), a partir del estado real del
 * pipeline. Tocar el emblema activa o pausa la escucha. El contexto se abre desde el
 * encabezado de la pantalla de sonidos.
 */
@Composable
fun HomeScreen(onOpenDiagnostic: (() -> Unit)?) {
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
    val note = shown?.let {
        val rule = remember(it.category, currentContext, toggles) { SoundAlertRuntime.ruleEngine.match(it.category, currentContext) }
        when {
            !it.confirmed -> null
            !it.category.alertable -> "Solo registro"
            rule == null -> "Sin alerta en ${currentContext.label}"
            else -> null
        }
    }
    HomeContent(listeningUi(status, permissionDenied), shown, note, micLevel(status), toggle, onOpenDiagnostic)
}

/** Nivel del bloque de audio actual (dBFS del pipeline) en 0–1, o null si no se está escuchando. */
private fun micLevel(status: PipelineStatus.Snapshot): Float? =
    if (status.phase == Phase.LISTENING && !status.dbfs.isNaN()) ((status.dbfs + 60f) / 45f).coerceIn(0f, 1f) else null

/** Lo que muestra A según la fase del pipeline. */
data class ListeningUi(val title: String, val running: Boolean, val listening: Boolean)

fun listeningUi(status: PipelineStatus.Snapshot, permissionDenied: Boolean): ListeningUi {
    val title = when {
        permissionDenied -> "Sin micrófono"
        status.phase == Phase.LISTENING -> "Escuchando"
        status.phase == Phase.STARTING -> "Iniciando…"
        status.phase == Phase.SILENCED -> "Micrófono ocupado"
        status.phase == Phase.ERROR -> "Error"
        else -> "En pausa"
    }
    return ListeningUi(title, running = status.phase in setOf(Phase.STARTING, Phase.LISTENING, Phase.SILENCED), listening = status.phase == Phase.LISTENING)
}

/** A · Escuchando (sin estado; también la usan las previews). */
@Composable
fun ListeningContent(ui: ListeningUi, onToggle: () -> Unit = {}, onOpenDiagnostic: (() -> Unit)? = null, level: Float? = null) =
    HomeContent(ui, detection = null, note = null, level = level, onToggle = onToggle, onOpenDiagnostic = onOpenDiagnostic)

/** B · Analizando (sin estado; también la usan las previews). */
@Composable
fun AnalyzingContent(detection: DetectionDisplay, note: String? = null) =
    HomeContent(ListeningUi("Escuchando", running = true, listening = true), detection, note, level = null, onToggle = {}, onOpenDiagnostic = null)

/**
 * A y B comparten pantalla y emblema: al detectar algo, el Jarvis acelera de forma
 * progresiva y los textos se funden. Medidas sobre 192 (todo dentro del margen de
 * seguridad): arriba solo la hora (A) o "IA · ANALIZANDO" (B), en y≈28; el emblema de 134
 * (70 % del ancho) centrado en y = 94; debajo solo "Escuchando" (A) o "Sonido detectado"
 * con el chip del sonido y su confianza (B).
 */
@Composable
fun HomeContent(
    ui: ListeningUi,
    detection: DetectionDisplay?,
    note: String?,
    level: Float?,
    onToggle: () -> Unit,
    onOpenDiagnostic: (() -> Unit)?,
) {
    val ambient = LocalAmbient.current
    val ink = if (ambient) SaColors.AmbientContent else SaColors.Wine
    val analyzing = detection != null
    val mode = when {
        analyzing -> CoreMode.ANALYZING
        ui.listening -> CoreMode.LISTENING
        else -> CoreMode.PAUSED
    }
    WatchScreen(background = if (ambient) SaColors.AmbientBackground else SaColors.Background) { scale ->
        with(scale) {
            // El emblema entero es la zona táctil: activa o pausa la escucha.
            JarvisCore(
                mode,
                Modifier
                    .centerAt(EMBLEM_Y.mdp)
                    .size(EMBLEM.mdp)
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = if (ui.running) "Pausar la escucha" else "Activar la escucha",
                        onLongClick = onOpenDiagnostic,
                        onClick = onToggle,
                    )
                    .semantics { contentDescription = if (ui.running) "Escuchando. Toca para pausar" else "En pausa. Toca para escuchar" },
                level = level,
            )
            Crossfade(analyzing, animationSpec = tween(450), label = "textos") { showDetection ->
                Box(Modifier.fillMaxSize()) {
                    if (showDetection && detection != null) {
                        Text(
                            if (detection.confirmed) "IA · CONFIRMADO" else "IA · ANALIZANDO",
                            style = SaText.labelSmall,
                            color = ink,
                            maxLines = 1,
                            modifier = Modifier.centerAt(28.mdp),
                        )
                        val percent = String.format(Locale.US, "%d%%", (detection.confidence * 100).roundToInt())
                        Box(
                            Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                                contentDescription = listOfNotNull("Sonido detectado", SoundUi.possible(detection.category), percent, note).joinToString(", ")
                            },
                        ) {
                            FitText("Sonido detectado", SaText.title.copy(fontSize = 11.sp, lineHeight = 12.sp), ink, Modifier.centerAt(156.mdp).width(80.mdp))
                            // Chip de vino con el sonido y la confianza en rosa (7,1:1).
                            Text(
                                "${SoundUi.shortName(detection.category)} · $percent",
                                style = SaText.labelSmall.copy(letterSpacing = 0.04.em),
                                color = if (ambient) SaColors.AmbientBackground else SaColors.Background,
                                maxLines = 1,
                                modifier = Modifier
                                    .centerAt(165.5.mdp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ink)
                                    .padding(horizontal = 3.mdp, vertical = 1.mdp),
                            )
                        }
                    } else {
                        Clock(Modifier.centerAt(28.mdp), color = ink)
                        FitText(ui.title, SaText.title.copy(fontSize = 13.sp, lineHeight = 14.sp), ink, Modifier.centerAt(157.5.mdp).width(90.mdp))
                    }
                }
            }
        }
    }
}

/** Emblema Jarvis: 134 sobre 192 (≈ 70 % del ancho), centrado en y = 94. */
private const val EMBLEM = 134f
private const val EMBLEM_Y = 94f
