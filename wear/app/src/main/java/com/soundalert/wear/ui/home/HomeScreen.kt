package com.soundalert.wear.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.soundalert.wear.ui.components.JarvisMotion
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.components.SmoothEasing
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.components.pingPong
import com.soundalert.wear.ui.components.rememberJarvisMotion
import com.soundalert.wear.ui.state.DetectionDisplay
import com.soundalert.wear.ui.state.DetectionTracker
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantallas A (escuchando / reposo) y B (analizando), a partir del estado real del
 * pipeline. Mantener presionado el núcleo del emblema activa o pausa la escucha; la
 * píldora del contexto abre la pantalla de contexto.
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
    HomeContent(listeningUi(status, permissionDenied), currentContext, shown, note, toggle, onOpenContext, onOpenDiagnostic)
}

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
fun ListeningContent(ui: ListeningUi, context: SoundAlertContext = SoundAlertContext.CASA, onToggle: () -> Unit = {}, onOpenContext: (() -> Unit)? = {}) =
    HomeContent(ui, context, detection = null, note = null, onToggle = onToggle, onOpenContext = onOpenContext, onOpenDiagnostic = null)

/** B · Analizando (sin estado; también la usan las previews). */
@Composable
fun AnalyzingContent(detection: DetectionDisplay, note: String? = null) =
    HomeContent(ListeningUi("Escuchando", running = true, listening = true), SoundAlertContext.CASA, detection, note, onToggle = {}, onOpenContext = null, onOpenDiagnostic = null)

/**
 * A y B comparten pantalla, en una columna centrada sin nada encimado: hora → píldora del
 * contexto → Jarvis → título. Al detectar algo, el Jarvis pasa de 95 a 88 dp y acelera de
 * forma progresiva, y los textos de abajo cambian con un fundido cruzado ("Escuchando" ↔
 * "Sonido detectado" y "Posible sirena · 94%"); al volver, lo mismo a la inversa. La hora
 * y la píldora no se mueven. Mantener presionado ~1 s el núcleo pausa o reanuda la escucha.
 */
@Composable
fun HomeContent(
    ui: ListeningUi,
    context: SoundAlertContext,
    detection: DetectionDisplay?,
    note: String?,
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
    val motion = rememberJarvisMotion(mode)
    // El último sonido detectado sigue a la vista mientras su texto se desvanece.
    var lastDetection by remember { mutableStateOf(detection) }
    if (detection != null) lastDetection = detection
    WatchScreen(background = if (ambient) SaColors.AmbientBackground else SaColors.Background) { scale ->
        with(scale) {
            Clock(Modifier.centerAt(CLOCK_Y.mdp), style = SaText.clock.copy(fontSize = 9.sp, lineHeight = 10.sp), color = ink)
            if (ambient) {
                Text(context.label.uppercase(), style = SaText.labelSmall, color = SaColors.AmbientDim, maxLines = 1, modifier = Modifier.centerAt(CHIP_Y.mdp))
            } else {
                ContextChip(context, onOpenContext, scale, Modifier.centerAt(CHIP_Y.mdp))
            }

            // Jarvis: el borde de arriba queda fijo, 7 dp bajo la píldora; al analizar se encoge a 88.
            JarvisCore(
                mode,
                Modifier
                    .centerAt(JARVIS_Y.mdp)
                    .size(JARVIS.mdp)
                    .graphicsLayer {
                        val a = motion.analyzing
                        scaleX = lerp(1f, JARVIS_ANALYZING / JARVIS, a)
                        scaleY = scaleX
                        translationY = (lerp(0f, (JARVIS_ANALYZING - JARVIS) / 2f, a)).mdp.toPx()
                    },
                motion,
            )
            Box(
                Modifier
                    .centerAt(JARVIS_Y.mdp)
                    .size(CORE_TOUCH.mdp)
                    .clip(CircleShape)
                    .holdToToggle(onHold = onToggle, onDoubleTap = onOpenDiagnostic)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (ui.running) "Escuchando. Mantén presionado para pausar" else "En pausa. Mantén presionado para escuchar"
                        onClick(label = if (ui.running) "Pausar la escucha" else "Activar la escucha") { onToggle(); true }
                    },
            )

            AnimatedContent(
                analyzing,
                transitionSpec = {
                    fadeIn(tween(TEXT_IN_MS, delayMillis = TEXT_OUT_MS * 3 / 4, easing = SmoothEasing)) togetherWith
                        fadeOut(tween(TEXT_OUT_MS, easing = SmoothEasing))
                },
                label = "textos",
            ) { showDetection ->
                Box(Modifier.fillMaxSize()) {
                    val shown = lastDetection
                    if (showDetection && shown != null) {
                        DetectionTexts(shown, note, motion, ambient, ink, scale)
                    } else {
                        val style = SaText.title.copy(fontSize = 16.sp, lineHeight = 17.sp)
                        if (ui.listening && !ambient) {
                            AnimatedTitle(ui.title, style, motion, TitleRhythm.Listening, live = motion.animated, modifier = Modifier.centerAt(TITLE_Y.mdp))
                        } else {
                            FitText(ui.title, style, ink, Modifier.centerAt(TITLE_Y.mdp).width(110.mdp))
                        }
                    }
                }
            }
        }
    }
}

/** B: "Sonido detectado" y, debajo, "Posible sirena" con su confianza en un chip que late. */
@Composable
private fun BoxScope.DetectionTexts(detection: DetectionDisplay, note: String?, motion: JarvisMotion, ambient: Boolean, ink: Color, scale: WatchScale) = with(scale) {
    val percent = String.format(Locale.US, "%d%%", (detection.confidence * 100).roundToInt())
    Box(
        Modifier.matchParentSize().semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull("Sonido detectado", SoundUi.possible(detection.category), percent, note).joinToString(", ")
        },
    ) {
        val style = SaText.title.copy(fontSize = 14.sp, lineHeight = 15.sp)
        if (ambient) {
            FitText("Sonido detectado", style, ink, Modifier.centerAt(DETECTED_Y.mdp).width(120.mdp))
        } else {
            AnimatedTitle("Sonido detectado", style, motion, TitleRhythm.Analyzing, live = motion.animated, modifier = Modifier.centerAt(DETECTED_Y.mdp))
        }
        Row(Modifier.centerAt(POSSIBLE_Y.mdp), verticalAlignment = Alignment.CenterVertically) {
            Text(SoundUi.possible(detection.category), style = SaText.row.copy(fontSize = 7.sp, lineHeight = 8.sp), color = ink, maxLines = 1)
            Spacer(Modifier.width(3.mdp))
            // Chip de vino con la confianza en rosa; late suave en opacidad.
            Text(
                percent,
                style = SaText.labelSmall.copy(letterSpacing = 0.04.em),
                color = if (ambient) SaColors.AmbientBackground else SaColors.Background,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer {
                        if (motion.animated) {
                            motion.clock.frame
                            alpha = lerp(1f, CHIP_MIN_ALPHA, pingPong(motion.clock.seconds, CHIP_HALF))
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(ink)
                    .padding(horizontal = 3.mdp, vertical = 1.mdp),
            )
        }
    }
}

/**
 * Mantener presionado [HOLD_MS] llama a [onHold] (con una vibración); soltar antes no hace
 * nada, salvo un doble toque, que llama a [onDoubleTap] si lo hay (diagnóstico, en debug).
 * Si el dedo se desliza (por ejemplo, para cambiar de página), se cancela.
 */
@Composable
private fun Modifier.holdToToggle(onHold: () -> Unit, onDoubleTap: (() -> Unit)?): Modifier {
    val hold by rememberUpdatedState(onHold)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val view = LocalView.current
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            // null: lo mantuvo; true: lo soltó antes (un toque); false: se deslizó o se canceló.
            val tapped = withTimeoutOrNull(HOLD_MS) {
                var tap = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    if (!change.pressed) {
                        tap = true
                        break
                    }
                }
                tap
            }
            when (tapped) {
                null -> {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    hold()
                }
                true -> doubleTap?.let { open ->
                    if (withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() } != null) open()
                }
                false -> Unit
            }
        }
    }
}

/** Tiempo que hay que mantener presionado el núcleo para pausar o reanudar. */
const val HOLD_MS = 1_000L

/**
 * Columna sobre 192 (medidas de lo visible): hora de 9 sp con las cifras de y 22,5 a 28,
 * píldora de 10 de alto 4 dp debajo, Jarvis de 95 (50 % del ancho) 7 dp debajo y el
 * título de 16 sp (mayúsculas de 11) 7 dp debajo, hasta y≈162. Analizando, el Jarvis mide
 * 88 con el mismo borde de arriba y debajo van "Sonido detectado" (14 sp) y "Posible
 * sirena · 94%", hasta y≈167.
 */
internal const val CLOCK_Y = 25.5f
internal const val CHIP_Y = 37.5f
internal const val JARVIS = 95f
internal const val JARVIS_ANALYZING = 88f
internal const val JARVIS_Y = 97f
internal const val TITLE_Y = 157.5f
internal const val DETECTED_Y = 150f
internal const val POSSIBLE_Y = 163.5f

/** Zona de toque del núcleo (64 dp): no pisa la de la píldora (48 dp centrada en y 37,5). */
private const val CORE_TOUCH = 64f

/** Fundido cruzado de los textos: sale en 240 ms y entra en 420, casi al acabar la salida (no se encima). */
private const val TEXT_OUT_MS = 240
private const val TEXT_IN_MS = 420

/** Latido del chip de confianza: opacidad 1 → 0,8 en 1,6 s, ida y vuelta (0,8 mantiene 4,5:1). */
internal const val CHIP_MIN_ALPHA = 0.8f
private const val CHIP_HALF = 1.6f

private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction
