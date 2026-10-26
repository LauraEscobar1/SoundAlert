package com.soundalert.wear.ui.home

import com.soundalert.wear.ui.components.rememberJarvisMotion
import androidx.compose.foundation.Canvas
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
 * pipeline. Tocar el núcleo del emblema activa o pausa la escucha; el botón del contexto
 * abre la pantalla de contexto.
 */
@Composable
fun HomeScreen(onOpenContext: (() -> Unit)?, onOpenDiagnostic: (() -> Unit)?) {
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
    HomeContent(listeningUi(status, permissionDenied), currentContext, shown, note, micLevel(status), toggle, onOpenContext, onOpenDiagnostic)
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
fun ListeningContent(ui: ListeningUi, context: SoundAlertContext = SoundAlertContext.CASA, onToggle: () -> Unit = {}, onOpenContext: (() -> Unit)? = {}, level: Float? = null) =
    HomeContent(ui, context, detection = null, note = null, level = level, onToggle = onToggle, onOpenContext = onOpenContext, onOpenDiagnostic = null)

/** B · Analizando (sin estado; también la usan las previews). */
@Composable
fun AnalyzingContent(detection: DetectionDisplay, note: String? = null) =
    HomeContent(ListeningUi("Escuchando", running = true, listening = true), SoundAlertContext.CASA, detection, note, level = null, onToggle = {}, onOpenContext = null, onOpenDiagnostic = null)

/**
 * A y B comparten pantalla y emblema: al detectar algo, el Jarvis acelera de forma
 * progresiva, las ondas y el medidor del borde también, y los textos cambian con un
 * fundido y un leve deslizamiento hacia arriba. Centros sobre 192, todo dentro del margen
 * de seguridad:
 *  - A: hora (y≈23,6), botón del contexto (32,8), Jarvis de 119 dp (62 % del ancho,
 *    centro 96,2) y "Escuchando" con su onda, su brillo y sus tres puntos (159,5).
 *  - B: "IA · ANALIZANDO", "Sonido detectado", el mismo Jarvis y el chip del sonido con su
 *    confianza, en esas mismas alturas.
 * Detrás, las ondas que salen del Jarvis y el medidor circular del borde.
 */
@Composable
fun HomeContent(
    ui: ListeningUi,
    context: SoundAlertContext,
    detection: DetectionDisplay?,
    note: String?,
    level: Float?,
    onToggle: () -> Unit,
    onOpenContext: (() -> Unit)?,
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
    val motion = rememberJarvisMotion(mode, level)
    WatchScreen(background = if (ambient) SaColors.AmbientBackground else SaColors.Background) { scale ->
        with(scale) {
            if (!ambient) {
                Canvas(Modifier.fillMaxSize()) {
                    motion.clock.frame
                    val u = size.minDimension / 192f
                    drawEdgeMeter(motion, u, live = motion.animated)
                    if (motion.animated) drawListeningWaves(motion, u, centerY = EMBLEM_Y, fromRadius = EMBLEM * 0.965f / 2)
                }
            }
            JarvisCore(mode, Modifier.centerAt(EMBLEM_Y.mdp).size(EMBLEM.mdp), motion)
            // Zona táctil del emblema: su núcleo (64 dp), para no pisar la del botón del contexto.
            Box(
                Modifier
                    .centerAt(EMBLEM_Y.mdp)
                    .size(EMBLEM_TOUCH.mdp)
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = if (ui.running) "Pausar la escucha" else "Activar la escucha",
                        onLongClick = onOpenDiagnostic,
                        onClick = onToggle,
                    )
                    .semantics { contentDescription = if (ui.running) "Escuchando. Toca para pausar" else "En pausa. Toca para escuchar" },
            )
            AnimatedContent(
                analyzing,
                transitionSpec = {
                    (fadeIn(tween(420, delayMillis = 90)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 24 }) togetherWith
                        (fadeOut(tween(250)) + slideOutVertically(tween(420, easing = FastOutSlowInEasing)) { -it / 24 })
                },
                label = "textos",
            ) { showDetection ->
                Box(Modifier.fillMaxSize()) {
                    if (showDetection && detection != null) {
                        Text(
                            if (detection.confirmed) "IA · CONFIRMADO" else "IA · ANALIZANDO",
                            style = SaText.labelSmall,
                            color = ink,
                            maxLines = 1,
                            modifier = Modifier.centerAt(TOP_Y.mdp),
                        )
                        val percent = String.format(Locale.US, "%d%%", (detection.confidence * 100).roundToInt())
                        Box(
                            Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                                contentDescription = listOfNotNull("Sonido detectado", SoundUi.possible(detection.category), percent, note).joinToString(", ")
                            },
                        ) {
                            FitText("Sonido detectado", SaText.title.copy(fontSize = 11.sp, lineHeight = 12.sp), ink, Modifier.centerAt(SECOND_Y.mdp).width(84.mdp))
                            // Chip de vino con el sonido y la confianza en rosa (7,1:1).
                            Text(
                                "${SoundUi.shortName(detection.category)} · $percent",
                                style = SaText.labelSmall.copy(letterSpacing = 0.04.em),
                                color = if (ambient) SaColors.AmbientBackground else SaColors.Background,
                                maxLines = 1,
                                modifier = Modifier
                                    .centerAt(BOTTOM_Y.mdp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ink)
                                    .padding(horizontal = 3.mdp, vertical = 1.mdp),
                            )
                        }
                    } else {
                        Clock(Modifier.centerAt(TOP_Y.mdp), color = ink)
                        if (ambient) {
                            Text(context.label.uppercase(), style = SaText.labelSmall, color = SaColors.AmbientDim, maxLines = 1, modifier = Modifier.centerAt(SECOND_Y.mdp))
                        } else {
                            ContextChip(context, onOpenContext, scale, Modifier.centerAt(SECOND_Y.mdp))
                        }
                        val titleStyle = SaText.title.copy(fontSize = 12.sp, lineHeight = 13.sp)
                        if (ui.listening && !ambient) {
                            LivelyTitle(ui.title, titleStyle, motion, live = motion.animated, scale = scale, modifier = Modifier.centerAt(BOTTOM_Y.mdp))
                        } else {
                            FitText(ui.title, titleStyle, ink, Modifier.centerAt(BOTTOM_Y.mdp).width(90.mdp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Alturas (centros sobre 192) de la hora, el segundo elemento y el texto de abajo: entre
 * los bordes visibles de cada elemento quedan ≈ 1,5 dp, lo que permite el Jarvis al 62 %
 * dentro del margen de seguridad.
 */
private const val TOP_Y = 23.6f
private const val SECOND_Y = 32.8f
private const val BOTTOM_Y = 159.5f

/** Emblema Jarvis: 119 sobre 192 (≈ 62 % del ancho), centrado en y = 96,2; su núcleo tocable mide 64. */
private const val EMBLEM = 119f
private const val EMBLEM_Y = 96.2f
private const val EMBLEM_TOUCH = 64f
