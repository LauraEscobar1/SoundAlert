package com.soundalert.wear.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
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

/** Estado del núcleo: escuchando (lento, 5 barras), analizando (rápido, 9 barras) o en pausa (quieto). */
enum class CoreMode { LISTENING, ANALYZING, PAUSED }

/**
 * Emblema "Jarvis" de las pantallas A y B: un cuadrado compacto (≈ 46 % del ancho de
 * la pantalla) que porta el SVG del diseño, viewBox 200 × 200 centrado en (100, 100):
 *  1. Marcas finas: r = 94, trazo café al 35 %, grosor 5, guiones [1,2 · 6,2].
 *  2. Arco en óxido: r = 82, grosor 3, extremos redondos, guiones [90, 26, 18, 26, 60, 295];
 *     una vuelta en 9 s (2,2 s analizando), lineal.
 *  3. Anillo interior: r = 70, café al 55 %, grosor 1,5, guiones [3, 5], con un punto café
 *     de r = 3 en (100, 30); gira al revés, una vuelta en 14 s (3,5 s analizando).
 *  4. Halo: r = 54 relleno de óxido; escala 1 → 1,18 y opacidad 0,35 → 0,08, 2,4 s ida y vuelta.
 *  5. Núcleo: r = 40, relleno café al 12 % (translúcido), trazo café al 80 %, grosor 1,5.
 *  6. Barras negras de ancho 4 y radio 2 centradas en y = 100. Escuchando, 5 en
 *     x = 84…116 de 18, 30, 40, 30, 18 de alto (scaleY 0,35 → 1, 1,1 s ida y vuelta,
 *     desfases −0,4 −0,2 0 −0,6 −0,8 s). Analizando, 9 en x = 68…132 de 12, 24, 36, 48,
 *     56, 44, 30, 20, 12, en 0,55 s.
 * En modo ambiente no hay animación ni halo, y todo se dibuja en gris sobre negro.
 *
 * Los valores animados se leen solo al dibujar: el emblema no recompone en cada frame.
 */
@Composable
fun JarvisCore(mode: CoreMode, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    val animated = !ambient && mode != CoreMode.PAUSED
    val analyzing = mode == CoreMode.ANALYZING
    val bars = if (analyzing) ANALYZING_BARS else LISTENING_BARS
    // Al cambiar de modo se reinician las animaciones con sus nuevos tiempos.
    key(animated, analyzing) {
        val transition = rememberInfiniteTransition(label = "núcleo")
        val arc = transition.spin(animated, if (analyzing) 2_200 else 9_000, clockwise = true)
        val inner = transition.spin(animated, if (analyzing) 3_500 else 14_000, clockwise = false)
        val halo = transition.breathe(animated, durationMs = 2_400)
        val heights = bars.map { transition.bar(animated, if (analyzing) 550 else 1_100, it.delayMs) }
        val colors = if (ambient) CoreColors.Ambient else CoreColors.Day
        Canvas(modifier) {
            val u = size.minDimension / VIEWBOX
            drawTicks(colors, u)
            rotate(arc.value) { drawSegmentedArc(colors, u) }
            rotate(inner.value) { drawInnerRing(colors, u) }
            if (!ambient) drawHalo(halo.value, u)
            drawDisc(colors, u)
            drawBars(colors, u, bars, heights)
        }
    }
}

/** Barra del SVG: centro en x, alto máximo y desfase de su animación (negativo: ya empezada). */
private class Bar(val x: Float, val height: Float, val delayMs: Int)

private val LISTENING_BARS = listOf(Bar(84f, 18f, -400), Bar(92f, 30f, -200), Bar(100f, 40f, 0), Bar(108f, 30f, -600), Bar(116f, 18f, -800))

/** Analizando: x = 68…132 cada 8; los desfases se reparten a lo largo de los 0,55 s. */
private val ANALYZING_BARS = listOf(12f, 24f, 36f, 48f, 56f, 44f, 30f, 20f, 12f)
    .mapIndexed { i, h -> Bar(68f + 8f * i, h, -(i * 550 / 9)) }

private class CoreColors(
    val ticks: Color,
    val arc: Color,
    val ring: Color,
    val dot: Color,
    val discFill: Color,
    val discBorder: Color,
    val bars: Color,
) {
    companion object {
        val Day = CoreColors(
            ticks = SaColors.Brown.copy(alpha = 0.35f),
            arc = SaColors.Rust,
            ring = SaColors.Brown.copy(alpha = 0.55f),
            dot = SaColors.Brown,
            discFill = SaColors.Brown.copy(alpha = 0.12f),
            discBorder = SaColors.Brown.copy(alpha = 0.8f),
            bars = SaColors.Ink,
        )
        val Ambient = CoreColors(
            ticks = SaColors.AmbientDim,
            arc = SaColors.AmbientDim,
            ring = SaColors.AmbientDim,
            dot = SaColors.AmbientContent,
            discFill = Color.Transparent,
            discBorder = SaColors.AmbientDim,
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

/** Respiración del halo: 0 → 1 en [durationMs] y vuelta. */
@Composable
private fun InfiniteTransition.breathe(animated: Boolean, durationMs: Int): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0f) }
    return animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo")
}

/** scaleY de una barra: 0,35 → 1 en [durationMs] y vuelta, adelantada [delayMs] (negativo). */
@Composable
private fun InfiniteTransition.bar(animated: Boolean, durationMs: Int, delayMs: Int): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(1f) }
    return animateFloat(
        initialValue = BAR_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(-delayMs, StartOffsetType.FastForward),
        ),
        label = "barra",
    )
}

private const val BAR_MIN = 0.35f
private const val VIEWBOX = 200f

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

private fun DrawScope.drawInnerRing(colors: CoreColors, u: Float) {
    drawCircle(
        colors.ring,
        radius = 70f * u,
        style = Stroke(width = 1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 5f * u))),
    )
    // Punto en (100, 30): arriba, sobre el anillo.
    drawCircle(colors.dot, radius = 3f * u, center = Offset(center.x, center.y - 70f * u))
}

private fun DrawScope.drawHalo(progress: Float, u: Float) {
    val alpha = 0.35f + (0.08f - 0.35f) * progress
    drawCircle(SaColors.Rust.copy(alpha = alpha), radius = 54f * u * (1f + 0.18f * progress))
}

private fun DrawScope.drawDisc(colors: CoreColors, u: Float) {
    drawCircle(colors.discFill, radius = 40f * u)
    drawCircle(colors.discBorder, radius = 40f * u, style = Stroke(width = 1.5f * u))
}

private fun DrawScope.drawBars(colors: CoreColors, u: Float, bars: List<Bar>, scales: List<State<Float>>) {
    val width = 4f * u
    bars.forEachIndexed { i, bar ->
        val h = bar.height * u * scales[i].value
        // El SVG se dibuja en coordenadas del viewBox: (100, 100) es el centro del emblema.
        val x = center.x + (bar.x - 100f) * u
        drawRoundRect(colors.bars, topLeft = Offset(x - width / 2, center.y - h / 2), size = Size(width, h), cornerRadius = CornerRadius(2f * u))
    }
}
