package com.soundalert.wear.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

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

/** Hora recta como en los mockups ("10:24"), respetando el formato 12/24 h del reloj. */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    Text(DateFormat.getTimeFormat(context).format(Date(now)), modifier = modifier, style = SaText.clock, color = SaColors.OnSurface)
}

/** Barras de prioridad + etiqueta ("▬▬▬ PELIGRO"). El número de barras es el de vibraciones. */
@Composable
fun PriorityHeader(priority: Priority, onColor: Color, inactive: Color, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.mdp)) {
            repeat(3) { index ->
                Box(
                    Modifier.size(width = 11.mdp, height = 3.5.mdp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index < priority.bars) onColor else inactive),
                )
            }
        }
        Spacer(Modifier.height(4.mdp))
        Text(priority.label, style = SaText.label, color = onColor)
    }
}

/** Chip del contexto activo ("⌂ CASA"). Al tocarlo se abre la pantalla de contexto. */
@Composable
fun ContextChip(context: SoundAlertContext, onClick: () -> Unit, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(SaColors.Surface)
            .clickable(onClickLabel = "Cambiar contexto", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Contexto: ${context.label}" }
            .padding(horizontal = 7.mdp, vertical = 3.mdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(context.icon), contentDescription = null, tint = SaColors.OnSurface, modifier = Modifier.size(9.mdp))
        Spacer(Modifier.width(3.mdp))
        Text(context.label.uppercase(), style = SaText.label, color = SaColors.OnSurface)
    }
}

/** Icono de una categoría en un círculo de color (historial). */
@Composable
fun SoundBadge(category: SoundCategory, color: Color, size: Dp, iconTint: Color = Color.Black) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(painterResource(SoundUi.icon(category)), contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.58f))
    }
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
    // Barras de 4 y huecos de 4,5 (medidas del mockup): 72 × 54. Se encoge si no cabe.
    Canvas(modifier.heightIn(max = 54.mdp).fillMaxHeight().aspectRatio(WAVE_WIDTH / WAVE_HEIGHT, matchHeightConstraintsFirst = true)) {
        val w = size.width * BAR_WIDTH / WAVE_WIDTH
        val step = size.width * (BAR_WIDTH + BAR_GAP) / WAVE_WIDTH
        val amplitude = 0.55f + 0.45f * level.coerceIn(0f, 1f)
        BARS.forEachIndexed { i, (base, bright) ->
            val wobble = 0.82f + 0.18f * sin(phase + i * 0.9f)
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

private const val BAR_WIDTH = 4f
private const val BAR_GAP = 4.5f
private const val WAVE_WIDTH = 9 * BAR_WIDTH + 8 * BAR_GAP
private const val WAVE_HEIGHT = 54f

/** Altura relativa y si es blanca (central) o gris (lateral), medidas del mockup. */
private val BARS = listOf(
    0.22f to false,
    0.42f to false,
    0.74f to true,
    1.0f to true,
    0.62f to true,
    0.92f to true,
    0.5f to false,
    0.32f to false,
    0.18f to false,
)

/**
 * Texto que, si no cabe, reduce su tamaño (hasta [minSize]) antes de cortarse: así
 * se lee completo aunque el reloj tenga la letra grande. Los "…" quedan como último recurso.
 * Necesita un ancho acotado (width, weight o fillMaxWidth).
 *
 * Con [preferOneLine] y [maxLines] > 1, primero intenta una sola línea (como en los
 * mockups) y solo pasa a varias si ni con [minSize] cabe en una.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minSize: TextUnit = style.fontSize * 0.72f,
    preferOneLine: Boolean = false,
) {
    val autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.5.sp)
    if (!preferOneLine || maxLines == 1) {
        BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis, autoSize = autoSize)
        return
    }
    val centered = style.textAlign == TextAlign.Center
    BoxWithConstraints(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        val measurer = rememberTextMeasurer()
        val oneLineWidth = measurer.measure(text, style.copy(fontSize = minSize), maxLines = 1, softWrap = false).size.width
        val lines = if (oneLineWidth <= constraints.maxWidth) 1 else maxLines
        BasicText(text, Modifier, style.copy(color = color), maxLines = lines, overflow = TextOverflow.Ellipsis, autoSize = autoSize)
    }
}

/** Elemento central cuadrado (oreja, icono): hasta [max], o menos si los textos necesitan el sitio. */
fun Modifier.fitSquare(max: Dp): Modifier =
    heightIn(max = max).fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
