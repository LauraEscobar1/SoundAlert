package com.soundalert.wear.ui.sounds

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ripple
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.TOAST_MS
import com.soundalert.wear.ui.components.ToastState
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.components.rememberToastState
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.SoundCell
import com.soundalert.wear.ui.state.soundCells
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * G · Sonidos del contexto activo en una "rueda": un sonido protagonista, grande, sobre una
 * curva punteada que baja por la izquierda, con el anterior y el siguiente asomando arriba y
 * abajo. A la derecha, su nombre, su nivel y su interruptor. La corona o deslizar en
 * vertical hacen girar la rueda, con inercia y un encaje suave (y una vibración corta) en
 * cada sonido.
 *
 * Son las reglas reales del reloj para ese contexto. Tocar el círculo grande o el
 * interruptor lo enciende o lo apaga (SoundToggles, guardado por contexto) y el
 * RuleEngine lo aplica a la siguiente detección. Los de peligro llevan un candado: siempre
 * están activos y tocarlos solo lo recuerda. El encabezado abre la pantalla de contexto.
 */
@Composable
fun SoundsScreen(visible: Boolean, onOpenContext: (() -> Unit)? = null) {
    val context by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val disabled by SoundAlertRuntime.soundToggles.disabled.collectAsStateWithLifecycle()
    val cells = remember(context, disabled) { soundCells(context, SoundAlertRuntime.ruleEngine) }
    val toast = rememberToastState()
    SoundsContent(
        context = context,
        cells = cells,
        onToggle = { cell ->
            when {
                cell.locked -> toast.show(LOCKED_TOAST)
                SoundAlertRuntime.soundToggles.setEnabled(context, cell.category, !cell.enabled) ->
                    toast.show(if (cell.enabled) "Desactivado" else "Activado", check = true)
            }
        },
        toast = toast,
        visible = visible,
        onOpenContext = onOpenContext,
    )
}

/**
 * La rueda (sin estado propio salvo el sonido actual; también la usan las previews y los
 * tests). Medidas sobre 192, tomadas de la referencia: encabezado en y≈21; la curva es la
 * circunferencia de centro ([WHEEL_CX], [WHEEL_CY]) y radio [WHEEL_R]; el sonido actual,
 * de 54 (28 % del diámetro), con su centro en x≈71 (37 %); el anterior y el siguiente, de
 * 25 (13 %) y al 50 %, en y≈42 y y≈150; la columna de información desde x≈105 (55 %).
 */
@Composable
fun SoundsContent(
    context: SoundAlertContext,
    cells: List<SoundCell>,
    onToggle: (SoundCell) -> Unit,
    toast: ToastState,
    visible: Boolean = true,
    onOpenContext: (() -> Unit)? = null,
    initialIndex: Int = 0,
) {
    val last = (cells.size - 1).coerceAtLeast(0)
    var current by rememberSaveable(context) { mutableIntStateOf(initialIndex) }
    current = current.coerceIn(0, last)
    // Posición continua de la rueda (en sonidos): la mueven el dedo, la inercia y el encaje.
    val position = remember(context) { Animatable(current.toFloat()) }
    var direction by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    /** Lleva la rueda a [target] con un resorte suave; al encajar en otro sonido, vibra. */
    fun settle(target: Int, velocity: Float = 0f) {
        val clamped = target.coerceIn(0, last)
        if (clamped != current) {
            direction = if (clamped > current) 1 else -1
            current = clamped
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        scope.launch { position.animateTo(clamped.toFloat(), spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow), initialVelocity = velocity) }
    }

    LaunchedEffect(toast.serial) {
        if (toast.message != null) {
            delay(TOAST_MS)
            toast.hide()
        }
    }

    WatchScreen(
        Modifier
            .rotarySteps(active = visible) { settle(current + it) }
            .pointerInput(cells.size) {
                val perItem = ITEM_DRAG.dp.toPx()
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        // Inercia: la velocidad del dedo empuja la rueda unos sonidos más.
                        val velocity = -tracker.calculateVelocity().y / perItem
                        settle(wheelTarget(position.value, velocity, cells.size), velocity)
                    },
                    onDragCancel = { settle(position.value.roundToInt()) },
                ) { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val next = (position.value - dy / perItem).coerceIn(-0.4f, last + 0.4f)
                    scope.launch { position.snapTo(next) }
                }
            },
    ) { scale ->
        with(scale) {
            val unit = with(LocalDensity.current) { 1.mdp.toPx() }
            // La curva: el borde de la rueda, punteado y muy tenue.
            Canvas(Modifier.fillMaxSize()) { drawWheelCurve(unit) }

            // Sonidos sobre la curva: el actual grande y los vecinos pequeños, según la posición.
            val p = position.value
            val first = (p - 2).roundToInt().coerceAtLeast(0)
            val end = (p + 2).roundToInt().coerceAtMost(last)
            for (i in first..end) {
                val slot = wheelSlot(i - p)
                if (slot.alpha <= 0.01f) continue
                WheelItem(
                    cell = cells[i],
                    slot = slot,
                    isCurrent = i == current,
                    onToggle = { onToggle(cells[i]) },
                    scale = scale,
                )
            }
            // Al final de la lista, en lugar del siguiente sonido.
            val endSlot = wheelSlot(last + 1 - p)
            if (cells.isNotEmpty() && endSlot.alpha > 0.01f) {
                Text(
                    "FIN DE LA LISTA",
                    style = SaText.tag.copy(fontSize = 4.8.sp, letterSpacing = 0.02.em),
                    color = SaColors.Wine.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier
                        .centerAt(endSlot.y.mdp, x = (endSlot.x + 10f - 96f).mdp)
                        .graphicsLayer { alpha = (endSlot.alpha * 2f).coerceAtMost(1f) },
                )
            }

            cells.getOrNull(current)?.let { cell ->
                InfoColumn(cell, direction, toast, onToggle = { onToggle(cell) }, scale = scale)
            }

            // Encabezado fijo: icono y nombre del contexto, que abre la pantalla de contexto.
            val open = if (onOpenContext != null) Modifier.clickable(onClickLabel = "Cambiar contexto", role = Role.Button, onClick = onOpenContext) else Modifier
            Row(
                Modifier
                    .centerAt(HEADER_Y.mdp)
                    .clip(RoundedCornerShape(50))
                    .then(open)
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 4.mdp, vertical = 1.mdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(context.icon, glyphHeight = 6.mdp, tint = SaColors.Wine, contentDescription = "Contexto")
                Spacer(Modifier.width(3.mdp))
                Text(context.label, style = SaText.screenTitle.copy(fontSize = 9.sp), color = SaColors.Wine, maxLines = 1)
            }
        }
    }
}

/**
 * Un sonido sobre la curva. Activo: vidrio con el icono en vino. Apagado: contorno
 * punteado, icono atenuado y una línea que lo tacha (se dibuja de una esquina a la otra).
 * Al activarse, el círculo rebota (1 → 1,08 → 1) y suelta una onda que se expande. El punto
 * de arriba a la derecha es su nivel; los de peligro llevan además un candado.
 */
@Composable
private fun BoxScope.WheelItem(cell: SoundCell, slot: WheelSlot, isCurrent: Boolean, onToggle: () -> Unit, scale: WatchScale) = with(scale) {
    val name = SoundUi.shortName(cell.category)
    val fill by animateFloatAsState(if (cell.enabled) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "relleno")
    val strike by animateFloatAsState(if (cell.enabled) 0f else 1f, tween(320, easing = FastOutSlowInEasing), label = "tachado")
    val bounce = remember(cell.category) { Animatable(1f) }
    val ripple = remember(cell.category) { Animatable(1f) }
    var wasEnabled by remember(cell.category) { mutableIntStateOf(if (cell.enabled) 1 else 0) }
    LaunchedEffect(cell.enabled) {
        val now = if (cell.enabled) 1 else 0
        if (now == 1 && wasEnabled == 0) {
            launch { bounce.animateTo(1f, keyframes { durationMillis = 360; 1.08f at 140 }) }
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }
        }
        wasEnabled = now
    }
    val size = slot.size
    val semantics = if (isCurrent) {
        Modifier.semantics {
            contentDescription = "$name, ${cell.priority.label.lowercase()}"
            if (cell.locked) {
                stateDescription = "siempre activo"
            } else {
                role = Role.Switch
                toggleableState = ToggleableState(cell.enabled)
                stateDescription = if (cell.enabled) "activado" else "desactivado"
            }
            onClick(label = if (cell.locked) null else if (cell.enabled) "Desactivar" else "Activar") { onToggle(); true }
        }
    } else {
        Modifier
    }
    // Fondo opaco del color de la pantalla bajo el vidrio: la curva no se transparenta a
    // través del círculo. Va fuera de la capa atenuada, así tapa también en los vecinos.
    Canvas(
        Modifier
            .centerAt(slot.y.mdp, x = (slot.x - 96f).mdp)
            .size(size.mdp)
            .graphicsLayer {
                alpha = (slot.alpha * 2f).coerceAtMost(1f)
                scaleX = bounce.value
                scaleY = bounce.value
            },
    ) { drawCircle(SaColors.Background) }
    Box(
        Modifier
            .centerAt(slot.y.mdp, x = (slot.x - 96f).mdp)
            .size(size.mdp)
            .graphicsLayer {
                alpha = slot.alpha
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .then(if (isCurrent) Modifier.clickable(interactionSource = null, indication = ripple(bounded = false, radius = (size / 2f).mdp), role = Role.Button, onClick = onToggle) else Modifier)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val line = (size / BIG).coerceAtLeast(0.5f)
            // Onda al activarse.
            if (ripple.value < 1f) {
                drawCircle(SaColors.Accent.copy(alpha = 0.35f * (1f - ripple.value)), radius = r * (1f + 0.6f * ripple.value), style = Stroke(1.2.dp.toPx()))
            }
            drawCircle(SaColors.Glass.copy(alpha = SaColors.Glass.alpha * fill), radius = r)
            drawCircle(WHEEL_GLASS_BORDER.copy(alpha = WHEEL_GLASS_BORDER.alpha * fill), radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            if (fill < 1f) {
                val dash = line * 3.2.dp.toPx()
                drawCircle(
                    SaColors.Wine.copy(alpha = 0.45f * (1f - fill)),
                    radius = r - 0.7.dp.toPx(),
                    style = Stroke(line * 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))),
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            GlyphIcon(
                SoundUi.icon(cell.category),
                glyphHeight = (size * 0.34f).mdp,
                maxGlyphWidth = (size * 0.42f).mdp,
                tint = SaColors.Wine.copy(alpha = 1f - 0.55f * strike),
            )
            if (strike > 0f) {
                // Tachado que se dibuja de la esquina inferior izquierda a la superior derecha.
                Canvas(Modifier.size((size * 0.46f).mdp)) {
                    drawLine(
                        SaColors.Wine,
                        Offset(0f, this.size.height),
                        Offset(this.size.width * strike, this.size.height * (1f - strike)),
                        strokeWidth = (size / BIG).coerceAtLeast(0.5f) * 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Punto del nivel arriba a la derecha y candado arriba a la izquierda (peligro).
        val corner = size * 0.36f
        Box(
            Modifier
                .offset(x = corner.mdp, y = (-corner).mdp)
                .size((size * 0.1f).coerceAtLeast(3.4f).mdp)
                .clip(CircleShape)
                .background(cell.priority.color.copy(alpha = if (cell.enabled) 1f else 0.55f)),
        )
        if (cell.locked) {
            GlyphIcon(
                R.drawable.ic_lock,
                glyphHeight = (size * 0.15f).coerceAtLeast(3.2f).mdp,
                tint = SaColors.Wine,
                modifier = Modifier.offset(x = (-corner).mdp, y = (-corner).mdp),
            )
        }
    }
    // Nombre a la derecha de los vecinos (en el actual va en la columna de información).
    val labelAlpha = ((abs(slot.offset) - 0.35f) / 0.5f).coerceIn(0f, 1f) * (slot.alpha * 2f).coerceAtMost(1f)
    if (labelAlpha > 0.01f) {
        Text(
            name,
            style = SaText.body.copy(fontSize = 6.sp, fontWeight = FontWeight.SemiBold),
            color = SaColors.Wine.copy(alpha = 0.8f),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (slot.x + size / 2f + 5f).mdp, y = (slot.y - 4f).mdp)
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}

/**
 * Columna de información del sonido actual (desde x≈105): nombre, nivel, interruptor y,
 * tras un cambio, una píldora de vidrio que se va sola. Cambia con un fundido cruzado y un
 * leve deslizamiento en la dirección del giro.
 */
@Composable
private fun BoxScope.InfoColumn(cell: SoundCell, direction: Int, toast: ToastState, onToggle: () -> Unit, scale: WatchScale) = with(scale) {
    AnimatedContent(
        cell,
        transitionSpec = {
            val shift = 18 * direction
            (fadeIn(tween(240, delayMillis = 110)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { shift }) togetherWith
                (fadeOut(tween(110)) + slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -shift })
        },
        contentKey = { it.category },
        label = "información",
        modifier = Modifier.align(Alignment.TopStart).offset(x = INFO_X.mdp, y = 74.mdp),
    ) { shown ->
        Column(Modifier.width(INFO_WIDTH.mdp)) {
            val title = SoundUi.shortName(shown.category)
            val long = title.length > LONG_NAME
            FitText(
                title,
                SaText.title.copy(fontSize = if (long) 10.sp else 14.sp, lineHeight = if (long) 10.5.sp else 15.sp, textAlign = TextAlign.Start),
                SaColors.Wine,
                Modifier.width(INFO_WIDTH.mdp),
                maxLines = if (long) 2 else 1,
            )
            Spacer(Modifier.height(2.5.mdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.6.mdp).clip(CircleShape).background(shown.priority.color))
                Spacer(Modifier.width(2.5.mdp))
                Text(shown.priority.label, style = SaText.tag.copy(fontSize = 5.sp), color = SaColors.Wine, maxLines = 1)
            }
            Spacer(Modifier.height(3.mdp))
            WheelSwitch(shown, onToggle, scale)
        }
    }
    // Confirmación justo debajo del interruptor (y≈127), con un fundido; se va sola.
    AnimatedVisibility(
        visible = toast.message != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(260)),
        modifier = Modifier.align(Alignment.TopStart).offset(x = INFO_X.mdp, y = 120.5.mdp),
    ) {
        val message = toast.message ?: ""
        Row(
            Modifier
                .heightIn(min = 13.mdp)
                .clip(RoundedCornerShape(50))
                .background(SaColors.Glass)
                .padding(horizontal = 6.mdp, vertical = 2.mdp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FitText(message, SaText.row.copy(fontSize = 5.8.sp, lineHeight = 6.6.sp, fontWeight = FontWeight.ExtraBold), SaColors.Wine, Modifier.widthIn(max = 54.mdp), maxLines = 2)
            if (toast.check) {
                Spacer(Modifier.width(2.5.mdp))
                GlyphIcon(R.drawable.ic_check, glyphHeight = 3.2.mdp, tint = SaColors.Wine, contentDescription = "Hecho")
            }
        }
    }
}

/** Interruptor + estado. En peligro queda fijo encendido y dice "Siempre activo". */
@Composable
private fun WheelSwitch(cell: SoundCell, onToggle: () -> Unit, scale: WatchScale) = with(scale) {
    val on = cell.enabled || cell.locked
    val thumb by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "interruptor")
    val track by animateColorAsState(if (on) SaColors.Wine else SaColors.Wine.copy(alpha = 0.25f), tween(220), label = "pista")
    val status = when {
        cell.locked -> "Siempre activo"
        cell.enabled -> "Activo"
        else -> "Apagado"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Switch, onClickLabel = if (cell.locked) null else if (cell.enabled) "Desactivar" else "Activar", onClick = onToggle)
            .semantics { stateDescription = status },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 16.8.mdp, height = 8.mdp).clip(RoundedCornerShape(50)).background(track)) {
            Box(
                Modifier
                    .padding(1.2.mdp)
                    .offset(x = (8.8f * thumb).mdp)
                    .size(5.6.mdp)
                    .clip(CircleShape)
                    .background(if (on) SaColors.Background else Color.White.copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.width(3.5.mdp))
        Text(status, style = SaText.row.copy(fontSize = 6.sp, fontWeight = FontWeight.ExtraBold), color = SaColors.Wine, maxLines = 1)
    }
}

/** La curva de la rueda: arco punteado de ±47° (de casi el borde superior al inferior), vino al 18 %. */
private fun DrawScope.drawWheelCurve(unit: Float) {
    val steps = 32
    for (k in 0..steps) {
        val angle = -CURVE_DEG + 2f * CURVE_DEG * k / steps
        val (x, y) = wheelPoint(angle)
        drawCircle(SaColors.Wine.copy(alpha = 0.18f), radius = 0.75f * unit, center = Offset(x * unit, y * unit))
    }
}

/** Posición y aspecto de un sonido a [offset] sonidos del actual (0 = el actual, ±1 = vecinos). */
internal data class WheelSlot(val offset: Float, val x: Float, val y: Float, val size: Float, val alpha: Float)

/**
 * Geometría de la rueda (dp sobre 192): cada sonido está [STEP_DEG] grados más allá en la
 * circunferencia. El actual mide 54 y es opaco; a un sonido de distancia, 25 y al 50 %; a
 * partir de ahí se encoge y se desvanece hasta desaparecer a 1,6.
 */
internal fun wheelSlot(offset: Float): WheelSlot {
    val (x, y) = wheelPoint(offset * STEP_DEG)
    val d = abs(offset)
    val size = when {
        d <= 1f -> BIG + (SMALL - BIG) * d
        else -> SMALL - 8f * (d - 1f).coerceAtMost(1f)
    }
    val alpha = when {
        d <= 1f -> 1f - 0.5f * d
        else -> (0.5f * (1f - (d - 1f) / 0.6f)).coerceAtLeast(0f)
    }
    return WheelSlot(offset, x, y, size, alpha)
}

/** Punto de la circunferencia de la rueda a [degrees] desde su punto más a la izquierda (positivo hacia abajo). */
internal fun wheelPoint(degrees: Float): Pair<Float, Float> {
    val rad = Math.toRadians(degrees.toDouble())
    return (WHEEL_CX - WHEEL_R * cos(rad).toFloat()) to (WHEEL_CY + WHEEL_R * sin(rad).toFloat())
}

/**
 * Dónde encaja la rueda al soltarla: la posición actual más lo que la empuja la
 * [velocity] (sonidos por segundo), redondeado al sonido más cercano.
 */
internal fun wheelTarget(position: Float, velocity: Float, count: Int): Int =
    (position + velocity * INERTIA_S).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))

/** Toast al tocar un sonido de peligro. */
const val LOCKED_TOAST = "Siempre activo por seguridad"

/** Rueda medida en la referencia: centro (187, 96) y radio 116; vecinos a 27,85° (54 dp en vertical). */
internal const val WHEEL_CX = 187f
internal const val WHEEL_CY = 96f
internal const val WHEEL_R = 116f
internal const val STEP_DEG = 27.85f
internal const val BIG = 54f
internal const val SMALL = 25f

/** Encabezado y columna de información (sobre 192). */
private const val HEADER_Y = 21f

/**
 * La curva va de y≈30 (6 por debajo del texto del encabezado, que acaba en y≈24) a su
 * simétrico, y≈162, dentro de la zona segura: ±35° sobre la circunferencia de la rueda.
 * Así nunca cruza el encabezado ni la columna de información (llega como mucho a x≈92).
 */
internal const val CURVE_TOP = 30f
internal val CURVE_DEG = Math.toDegrees(asin((WHEEL_CY - CURVE_TOP) / WHEEL_R).toDouble()).toFloat()
private const val INFO_X = 105.4f
private const val INFO_WIDTH = 64f

/** Arrastre (dp) que mueve la rueda un sonido, y cuánto alarga la inercia el giro. */
private const val ITEM_DRAG = 46f
/** Nombres más largos van en dos líneas más pequeñas para no salirse de la pantalla. */
private const val LONG_NAME = 9
private const val INERTIA_S = 0.18f

/** Borde del vidrio de la rueda: blanco al 55 %. */
private val WHEEL_GLASS_BORDER = Color.White.copy(alpha = 0.55f)
