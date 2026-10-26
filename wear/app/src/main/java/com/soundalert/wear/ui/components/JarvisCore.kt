package com.soundalert.wear.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.soundalert.wear.ui.theme.SaColors
import kotlin.math.cos
import kotlin.math.sin

/** Estado del núcleo: escuchando (lento, 5 barras), analizando (rápido, 9 barras) o en pausa (quieto). */
enum class CoreMode { LISTENING, ANALYZING, PAUSED }

/**
 * Emblema "Jarvis" de las pantallas A y B: un cuadrado compacto (≈ 46 % del ancho de
 * la pantalla) dibujado sobre un viewBox de 200 × 200, centrado en (100, 100):
 *  1. Marcas finas: r = 94, trazo 5 punteado [1,2 · 6,2], café al 35 %.
 *  2. Arco en óxido: r = 82, trazo 3 redondeado [90, 26, 18, 26, 60, 295]; una vuelta
 *     en 9 s (2,2 s analizando).
 *  3. Anillo interior punteado: r = 70, con un punto (dos al analizar); gira al revés
 *     en 14 s (3,5 s).
 *  4. Halo que respira bajo el disco: r 46 → 54 (escala 1 → 1,18), opacidad 0,30 → 0,08, 2,4 s.
 *  5. Disco central (r = 40; r = 54 al analizar) con barras en tinta: 5 a 1,1 s; 9 a 0,55 s.
 * En modo ambiente no hay animación ni halo, y todo se dibuja en gris sobre negro.
 *
 * Los valores animados se leen solo al dibujar: el emblema no recompone en cada frame.
 */
@Composable
fun JarvisCore(mode: CoreMode, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    val animated = !ambient && mode != CoreMode.PAUSED
    val analyzing = mode == CoreMode.ANALYZING
    val bars = if (analyzing) 9 else 5
    // Al cambiar de modo se reinician las animaciones con sus nuevos tiempos.
    key(animated, analyzing) {
        val transition = rememberInfiniteTransition(label = "núcleo")
        val arc = transition.spin(animated, if (analyzing) 2_200 else 9_000, clockwise = true)
        val inner = transition.spin(animated, if (analyzing) 3_500 else 14_000, clockwise = false)
        val halo = transition.loop(animated && !analyzing, durationMs = 2_400)
        val heights = List(bars) { i -> transition.bar(animated, if (analyzing) 550 else 1_100, i, bars) }
        val colors = if (ambient) CoreColors.Ambient else CoreColors.Day
        Canvas(modifier) {
            val u = size.minDimension / VIEWBOX
            drawTicks(colors, u)
            rotate(arc.value) { drawSegmentedArc(colors, u) }
            rotate(inner.value) { drawInnerRing(colors, u, dots = if (analyzing) 2 else 1) }
            if (!ambient && !analyzing) drawHalo(halo.value, u)
            drawDisc(colors, u, radius = if (analyzing) DISC_ANALYZING else DISC)
            drawBars(colors, u, heights, analyzing)
        }
    }
}

private class CoreColors(
    val ticks: Color,
    val arc: Color,
    val ring: Color,
    val dot: Color,
    val disc: Color,
    val discAnalyzing: Color,
    val discBorder: Color,
    val discBorderAnalyzing: Color,
    val bars: Color,
) {
    companion object {
        /** Colores medidos sobre el mockup final. */
        val Day = CoreColors(
            ticks = SaColors.Brown.copy(alpha = 0.35f),
            arc = SaColors.Rust,
            ring = SaColors.Brown.copy(alpha = 0.55f),
            dot = SaColors.Brown,
            disc = Color(0xFFCE7956), // óxido al ~40 % sobre el durazno
            discAnalyzing = Color(0xFFE69571), // óxido al ~12 % sobre el durazno
            discBorder = SaColors.Brown.copy(alpha = 0.75f),
            discBorderAnalyzing = SaColors.Rust,
            bars = SaColors.Ink,
        )
        val Ambient = CoreColors(
            ticks = SaColors.AmbientDim,
            arc = SaColors.AmbientDim,
            ring = SaColors.AmbientDim,
            dot = SaColors.AmbientContent,
            disc = SaColors.AmbientBackground,
            discAnalyzing = SaColors.AmbientBackground,
            discBorder = SaColors.AmbientDim,
            discBorderAnalyzing = SaColors.AmbientDim,
            bars = SaColors.AmbientContent,
        )
    }
}

/** Una vuelta completa en [periodMs]; quieto si no está [animated]. */
@Composable
private fun InfiniteTransition.spin(animated: Boolean, periodMs: Int, clockwise: Boolean): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0f) }
    return animateFloat(
        initialValue = 0f,
        targetValue = if (clockwise) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "giro",
    )
}

/** Progreso 0 → 1 que vuelve a empezar cada [durationMs]. */
@Composable
private fun InfiniteTransition.loop(animated: Boolean, durationMs: Int): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0.5f) }
    return animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = "halo")
}

/** Altura de la barra [index]: sube y baja entre 0,35 y 1 en [periodMs], desfasada de sus vecinas. */
@Composable
private fun InfiniteTransition.bar(animated: Boolean, periodMs: Int, index: Int, count: Int): State<Float> {
    val profile = if (count > 5) STATIC_BARS_9 else STATIC_BARS_5
    if (!animated) return remember(index, count) { mutableFloatStateOf(profile[index]) }
    return animateFloat(
        initialValue = BAR_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMs / 2, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(periodMs * index / count),
        ),
        label = "barra",
    )
}

private const val BAR_MIN = 0.35f

/** Alturas en pausa y en modo ambiente: el perfil del mockup. */
private val STATIC_BARS_5 = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
private val STATIC_BARS_9 = listOf(0.3f, 0.5f, 0.7f, 0.9f, 1f, 0.9f, 0.7f, 0.5f, 0.3f)

private const val VIEWBOX = 200f
private const val DISC = 40f
private const val DISC_ANALYZING = 54f

private fun DrawScope.drawTicks(colors: CoreColors, u: Float) {
    drawCircle(
        colors.ticks,
        radius = 94f * u,
        style = Stroke(width = 5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * u, 6.2f * u))),
    )
}

private fun DrawScope.drawSegmentedArc(colors: CoreColors, u: Float) {
    val pattern = floatArrayOf(90f, 26f, 18f, 26f, 60f, 295f).map { it * u }.toFloatArray()
    drawCircle(
        colors.arc,
        radius = 82f * u,
        style = Stroke(width = 3f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(pattern)),
    )
}

private fun DrawScope.drawInnerRing(colors: CoreColors, u: Float, dots: Int) {
    val radius = 70f * u
    drawCircle(
        colors.ring,
        radius = radius,
        style = Stroke(width = 1.4f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.5f * u))),
    )
    // Un punto arriba a la derecha (-30°); al analizar, dos opuestos (arriba y abajo).
    val angles = if (dots == 1) listOf(-30.0) else listOf(-90.0, 90.0)
    for (deg in angles) {
        val rad = Math.toRadians(deg)
        drawCircle(colors.dot, radius = 3.4f * u, center = Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat()))
    }
}

private fun DrawScope.drawHalo(progress: Float, u: Float) {
    val radius = 46f * u * (1f + 0.18f * progress)
    val alpha = 0.30f + (0.08f - 0.30f) * progress
    drawCircle(SaColors.Rust.copy(alpha = alpha), radius = radius)
}

private fun DrawScope.drawDisc(colors: CoreColors, u: Float, radius: Float) {
    val analyzing = radius > DISC
    drawCircle(if (analyzing) colors.discAnalyzing else colors.disc, radius = radius * u)
    drawCircle(if (analyzing) colors.discBorderAnalyzing else colors.discBorder, radius = radius * u, style = Stroke(width = 2f * u))
}

/** Barras centradas en el disco: 5 de 4 × 39 cada 8; analizando, 9 de 3 × 55 cada 7,6. */
private fun DrawScope.drawBars(colors: CoreColors, u: Float, heights: List<State<Float>>, analyzing: Boolean) {
    val width = (if (analyzing) 3f else 4f) * u
    val step = (if (analyzing) 7.6f else 8f) * u
    val maxHeight = (if (analyzing) 55f else 39f) * u
    val first = center.x - step * (heights.size - 1) / 2
    heights.forEachIndexed { i, state ->
        val h = maxHeight * state.value
        val x = first + step * i
        drawRoundRect(colors.bars, topLeft = Offset(x - width / 2, center.y - h / 2), size = Size(width, h), cornerRadius = CornerRadius(width / 2))
    }
}
