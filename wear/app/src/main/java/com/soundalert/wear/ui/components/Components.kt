package com.soundalert.wear.ui.components

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.bars
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import java.util.Date
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
    background: Color = SaColors.Background,
    content: @Composable BoxWithConstraintsScope.(WatchScale) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(background)) {
        val scale = WatchScale(minOf(maxWidth.value, maxHeight.value) / MOCKUP_DIAMETER_DP)
        content(scale)
    }
}

private const val MOCKUP_DIAMETER_DP = 192f

/**
 * Hora local recta ("10:24"): zona horaria y formato 12/24 h del sistema, leídos en cada
 * actualización (si cambian, la hora se corrige sola).
 */
@Composable
fun Clock(modifier: Modifier = Modifier, style: TextStyle = SaText.clock, color: Color = SaColors.Wine) {
    val context = LocalContext.current
    val now = rememberNow()
    Text(DateFormat.getTimeFormat(context).format(Date(now)), modifier = modifier, style = style, color = color, maxLines = 1)
}

/**
 * Hora actual que se actualiza al empezar cada minuto (o cada [periodMs] si es menor) y
 * al volver a la app (onResume), para que nunca se vea una hora vieja.
 */
@Composable
fun rememberNow(periodMs: Long = 60_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

/**
 * Barras de nivel de las alertas: 3 de 8,4 × 2,6 con 2 de separación; las encendidas
 * (tantas como vibraciones: 3 / 2 / 1) en tinta y el resto en tinta al 18 %.
 */
@Composable
fun PriorityBars(priority: Priority, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.mdp)) {
        repeat(3) { index ->
            Box(
                Modifier.size(width = 8.4.mdp, height = 2.6.mdp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index < priority.bars) SaColors.Ink else SaColors.Ink.copy(alpha = 0.18f)),
            )
        }
    }
}

/**
 * Botón del contexto activo del inicio ("⌂ CASA"): una píldora de vidrio (acento al 25 %
 * con borde blanco al 55 %) de 10 de alto, con el icono de 6 y el nombre en monoespaciada
 * de 5 sp con espaciado 0,1 em, en vino. Al tocarlo se abre la pantalla de contexto; su
 * zona de toque mide al menos 48 × 48 dp aunque la píldora se vea más pequeña.
 */
@Composable
fun ContextChip(context: SoundAlertContext, onClick: (() -> Unit)?, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    val clickable = if (onClick != null) Modifier.clickable(onClickLabel = "Cambiar contexto", role = Role.Button, onClick = onClick) else Modifier
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .defaultMinSize(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
            .then(clickable)
            .semantics(mergeDescendants = true) { contentDescription = "Contexto: ${context.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(CONTEXT_CHIP_HEIGHT.mdp)
                .clip(shape)
                .background(SaColors.Glass)
                .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                .padding(start = 4.5.mdp, end = 5.mdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(context.icon, glyphHeight = 6.mdp, tint = SaColors.Wine)
            Spacer(Modifier.width(2.5.mdp))
            Text(context.label.uppercase(), style = SaText.labelSmall.copy(fontSize = 5.sp, letterSpacing = 0.1.em), color = SaColors.Wine, maxLines = 1)
        }
    }
}

/** Alto de la píldora del contexto del inicio (dp sobre 192). */
const val CONTEXT_CHIP_HEIGHT = 10f

/** Zona de toque mínima de los controles pequeños. */
val MIN_TOUCH = 48.dp

/** Vidrio: acento al 25 % con borde blanco al 45 %. Lo que va encima, en vino y negrita. */
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
        // Al cambiar de sentido se descarta lo acumulado: si no, el primer giro hacia
        // atrás tendría que deshacer antes el sobrante de los giros hacia adelante.
        if (event.verticalScrollPixels * accumulated[0] < 0f) accumulated[0] = 0f
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

/**
 * Coloca el elemento centrado en horizontal y con su centro a [y] del borde superior
 * de la pantalla (medidas del mockup). Va primero en la cadena de modificadores, dentro
 * de un Box que ocupa la pantalla.
 */
fun Modifier.centerAt(y: Dp, x: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((constraints.maxWidth - placeable.width) / 2 + x.roundToPx(), y.roundToPx() - placeable.height / 2)
    }
}

/** Mensaje breve ("Perro activado ✓"): se muestra [TOAST_MS] y desaparece. */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    /** Con un check a la derecha ("Perro activado ✓"). */
    var check by mutableStateOf(false)
        private set
    internal var serial by mutableIntStateOf(0)
        private set

    fun show(text: String, check: Boolean = false) {
        message = text
        this.check = check
        serial++
    }

    internal fun hide() {
        message = null
    }
}

@Composable
fun rememberToastState() = remember { ToastState() }

const val TOAST_MS = 1_500L

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
