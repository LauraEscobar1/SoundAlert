package com.soundalert.wear.ui.components

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.bars
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Los mockups miden 192 dp de diámetro: [u] escala sus medidas a la pantalla real. */
class WatchScale(val u: Float) {
    /** Medida del mockup (en dp sobre 192) en la pantalla real. */
    val Number.mdp: Dp get() = (toFloat() * u).dp
}

/** Pantalla redonda a pantalla completa con la escala del mockup. */
@Composable
fun WatchScreen(
    modifier: Modifier = Modifier,
    background: Color = SaColors.Peach,
    content: @Composable BoxWithConstraintsScope.(WatchScale) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(background)) {
        val scale = WatchScale(minOf(maxWidth.value, maxHeight.value) / MOCKUP_DIAMETER_DP)
        content(scale)
    }
}

private const val MOCKUP_DIAMETER_DP = 192f

/** Hora recta ("10:24"), respetando el formato 12/24 h del reloj. */
@Composable
fun Clock(modifier: Modifier = Modifier, style: TextStyle = SaText.clock, color: Color = SaColors.Ink) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    Text(DateFormat.getTimeFormat(context).format(Date(now)), modifier = modifier, style = style, color = color, maxLines = 1)
}

/**
 * Barras de nivel + etiqueta ("▬▬▬ PELIGRO") sobre el color de la alerta. El número
 * de barras encendidas es el de vibraciones (3 / 2 / 1).
 */
@Composable
fun PriorityHeader(priority: Priority, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.7.mdp)) {
            repeat(3) { index ->
                Box(
                    Modifier.size(width = 10.mdp, height = 4.1.mdp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index < priority.bars) SaColors.Ink else SaColors.Ink.copy(alpha = 0.22f)),
                )
            }
        }
        Spacer(Modifier.height(3.3.mdp))
        Text(priority.label, style = SaText.label, color = SaColors.Ink, maxLines = 1)
    }
}

/** Chip de vidrio del contexto activo ("⌂ CASA"). Al tocarlo se abre la pantalla de contexto. */
@Composable
fun ContextChip(context: SoundAlertContext, onClick: (() -> Unit)?, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    val clickable = if (onClick != null) Modifier.clickable(onClickLabel = "Cambiar contexto", role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .height(16.3.mdp)
            .glass()
            .then(clickable)
            .semantics { contentDescription = "Contexto: ${context.label}" }
            .padding(start = 5.9.mdp, end = 6.3.mdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(context.icon, glyphHeight = 7.2.mdp, tint = SaColors.Ink)
        Spacer(Modifier.width(5.mdp))
        Text(context.label.uppercase(), style = SaText.labelSmall, color = SaColors.Ink, maxLines = 1)
    }
}

/** Vidrio: naranja al 32 % con borde blanco al 45 %. Lo que va encima, en tinta y negrita. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(50)): Modifier =
    clip(shape).background(SaColors.Glass).border(1.dp, SaColors.GlassBorder, shape)

/** true mientras el reloj está en modo ambiente (always-on): sin animaciones y fondo oscuro. */
val LocalAmbient = compositionLocalOf { false }

/**
 * Corona (rotary input): cada [ROTARY_STEP] de giro llama a [onStep] con +1 o −1.
 * Mientras [active] sea true, el elemento pide el foco para recibir los giros.
 */
@Composable
fun Modifier.rotarySteps(active: Boolean = true, onStep: (Int) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    val threshold = with(LocalDensity.current) { ROTARY_STEP.toPx() }
    val step by rememberUpdatedState(onStep)
    val accumulated = remember { floatArrayOf(0f) }
    LaunchedEffect(active) { if (active) focusRequester.requestFocus() }
    return onRotaryScrollEvent { event ->
        accumulated[0] += event.verticalScrollPixels
        while (accumulated[0] >= threshold) {
            accumulated[0] -= threshold
            step(1)
        }
        while (accumulated[0] <= -threshold) {
            accumulated[0] += threshold
            step(-1)
        }
        true
    }.focusRequester(focusRequester).focusable()
}

private val ROTARY_STEP = 36.dp

/** Mensaje breve ("Perro activado ✓"): se muestra [TOAST_MS] y desaparece. */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    internal var serial by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        serial++
    }

    internal fun hide() {
        message = null
    }
}

@Composable
fun rememberToastState() = remember { ToastState() }

/** Toast de vidrio con texto en tinta y negrita. Se anuncia a los lectores de pantalla. */
@Composable
fun GlassToast(state: ToastState, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    LaunchedEffect(state.serial) {
        if (state.message != null) {
            delay(TOAST_MS)
            state.hide()
        }
    }
    val message = state.message ?: return@with
    Box(
        modifier
            .glass()
            .background(SaColors.Peach.copy(alpha = 0.55f))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 9.mdp, vertical = 5.mdp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = SaText.row.copy(fontSize = 9.sp), color = SaColors.Ink, maxLines = 1)
    }
}

const val TOAST_MS = 1_500L

/** Icono de una categoría en un círculo de color (historial: círculo de 18,6 en el mockup). */
@Composable
fun SoundBadge(category: SoundCategory, color: Color, size: Dp, iconTint: Color = Color.Black) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        GlyphIcon(SoundUi.icon(category), glyphHeight = size * 0.48f, tint = iconTint, maxGlyphWidth = size * 0.6f)
    }
}

/**
 * Icono vectorial dimensionado por su DIBUJO y no por su caja de 24 unidades: la
 * sirena, el fuego o el timbre ocupan partes distintas de esa caja, y en el mockup se
 * miden por lo que se ve. [glyphHeight] es el alto del dibujo ([maxGlyphWidth] lo limita
 * a lo ancho) y el dibujo, no la caja, queda centrado en el espacio que ocupa.
 */
@Composable
fun GlyphIcon(
    @DrawableRes res: Int,
    glyphHeight: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    maxGlyphWidth: Dp = Dp.Infinity,
    contentDescription: String? = null,
) {
    val vector = ImageVector.vectorResource(res)
    val bounds = remember(vector) { glyphBounds(vector) }
    val unit = minOf(glyphHeight.value / bounds.height, maxGlyphWidth.value / bounds.width) // dp por unidad del icono
    Box(modifier.size((bounds.width * unit).dp, (bounds.height * unit).dp), contentAlignment = Alignment.Center) {
        Icon(
            vector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .requiredSize((vector.viewportWidth * unit).dp, (vector.viewportHeight * unit).dp)
                .offset(((vector.viewportWidth / 2 - bounds.center.x) * unit).dp, ((vector.viewportHeight / 2 - bounds.center.y) * unit).dp),
        )
    }
}

/** Rectángulo que ocupa el dibujo dentro del viewport del icono (con las escalas de sus grupos). */
private fun glyphBounds(vector: ImageVector): Rect {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    fun visit(group: VectorGroup, map: (Offset) -> Offset) {
        for (node in group) {
            when (node) {
                is VectorPath -> {
                    val b = node.pathData.toPath().getBounds()
                    val a = map(b.topLeft)
                    val c = map(b.bottomRight)
                    left = minOf(left, a.x, c.x)
                    right = maxOf(right, a.x, c.x)
                    top = minOf(top, a.y, c.y)
                    bottom = maxOf(bottom, a.y, c.y)
                }
                is VectorGroup -> visit(node) { p ->
                    map(
                        Offset(
                            (p.x - node.pivotX) * node.scaleX + node.pivotX + node.translationX,
                            (p.y - node.pivotY) * node.scaleY + node.pivotY + node.translationY,
                        ),
                    )
                }
            }
        }
    }
    visit(vector.root) { it }
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else Rect(0f, 0f, vector.viewportWidth, vector.viewportHeight)
}

/**
 * Onda de la pantalla B: 9 barras (las centrales blancas). Se anima mientras la IA
 * analiza; [level] (0–1, del nivel de entrada real) modula la amplitud.
 */
@Composable
fun Waveform(level: Float, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    val transition = rememberInfiniteTransition(label = "onda")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )
    // Mockup: 9 barras de 5,4 con huecos de 3,2 (≈ 74 × 54,7). Se encoge si no cabe.
    Canvas(modifier.heightIn(max = WAVE_HEIGHT.mdp).fillMaxHeight().aspectRatio(WAVE_WIDTH / WAVE_HEIGHT, matchHeightConstraintsFirst = true)) {
        val w = size.width * BAR_WIDTH / WAVE_WIDTH
        val step = size.width * (BAR_WIDTH + BAR_GAP) / WAVE_WIDTH
        // Con sonido fuerte, alturas del mockup; más bajo, la onda se reduce hasta el 70 %.
        val amplitude = 0.7f + 0.3f * level.coerceIn(0f, 1f)
        BARS.forEachIndexed { i, (base, bright) ->
            val wobble = 0.9f + 0.1f * sin(phase + i * 0.9f)
            val h = size.height * base * wobble * amplitude
            drawRoundRect(
                color = if (bright) SaColors.OnSurface else SaColors.Inactive,
                topLeft = Offset(i * step, (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

private const val BAR_WIDTH = 5.4f
private const val BAR_GAP = 3.2f
private const val WAVE_WIDTH = 9 * BAR_WIDTH + 8 * BAR_GAP
private const val WAVE_HEIGHT = 54.7f

/** Altura relativa de cada barra y si es blanca (central) o gris (lateral), medidas del mockup. */
private val BARS = listOf(
    0.22f to false,
    0.45f to false,
    0.74f to true,
    1.0f to true,
    0.63f to true,
    0.89f to true,
    0.52f to false,
    0.31f to false,
    0.17f to false,
)

/**
 * Texto con el tamaño del diseño. Si no cabe, primero usa más líneas (hasta [maxLines]);
 * solo como último recurso reduce la letra, y como mucho a [minSize] (90 %), para que
 * dos textos equivalentes no acaben con tamaños visiblemente distintos. Los "…" quedan
 * para casos extremos. Necesita un ancho acotado (width, weight o fillMaxWidth).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minSize: TextUnit = style.fontSize * 0.9f,
) {
    BasicText(
        text,
        modifier,
        style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.25.sp),
    )
}

/** Elemento central cuadrado (oreja): hasta [max], o menos si los textos necesitan el sitio. */
fun Modifier.fitSquare(max: Dp): Modifier =
    heightIn(max = max).fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
