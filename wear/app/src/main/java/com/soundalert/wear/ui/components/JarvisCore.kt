package com.soundalert.wear.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Estado del núcleo: escuchando (lento, 5 barras), analizando (rápido, 9 barras) o en pausa (quieto). */
enum class CoreMode { LISTENING, ANALYZING, PAUSED }

/**
 * Núcleo animado de las pantallas A y B, concéntrico con la esfera. Capas, de fuera
 * a dentro (medidas sobre 192):
 *  1. Marcas finas en el borde (trazo punteado, café al 35 %).
 *  2. Arco segmentado en óxido que gira (9 s por vuelta; 2,2 s analizando).
 *  3. Anillo interior punteado con un punto, que gira al revés (14 s; 3,5 s).
 *  4. Halo que respira: escala 1 → 1,18 y opacidad 0,35 → 0,08 en 2,4 s.
 *  5. Círculo central de vidrio con barras de audio (5 a 1,1 s; 9 a 0,55 s).
 * En modo ambiente no hay animación ni halo, y todo se dibuja en gris sobre negro.
 *
 * Los valores animados se leen solo al dibujar: el núcleo no recompone en cada frame.
 */
@Composable
fun JarvisCore(mode: CoreMode, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    val animated = !ambient && mode != CoreMode.PAUSED
    val fast = mode == CoreMode.ANALYZING
    val bars = if (fast) 9 else 5
    // Al cambiar de modo se reinician las animaciones con sus nuevos tiempos.
    key(animated, fast) {
        val transition = rememberInfiniteTransition(label = "núcleo")
        val arc = transition.spin(animated, if (fast) 2_200 else 9_000, clockwise = true)
        val inner = transition.spin(animated, if (fast) 3_500 else 14_000, clockwise = false)
        val halo = transition.loop(animated, durationMs = 2_400, label = "halo")
        val heights = List(bars) { i -> transition.bar(animated, if (fast) 550 else 1_100, i, bars) }
        val colors = if (ambient) CoreColors.Ambient else CoreColors.Day
        Canvas(modifier) {
            val u = size.minDimension / MOCKUP_DIAMETER
            drawOuterTicks(colors, u)
            rotate(arc.value) { drawSegmentedArc(colors, u) }
            if (!ambient) drawHalo(halo.value, u)
            rotate(inner.value) { drawInnerRing(colors, u) }
            drawDisc(colors, u)
            drawBars(colors, u, heights)
        }
    }
}

/** [discBase]: fondo opaco bajo el vidrio del disco, para que el halo no se vea a través. */
private class CoreColors(
    val ticks: Color,
    val arc: Color,
    val ring: Color,
    val dot: Color,
    val discBase: Color,
    val discFill: Color,
    val discBorder: Color,
    val bars: Color,
) {
    companion object {
        val Day = CoreColors(
            ticks = SaColors.Brown.copy(alpha = 0.35f),
            arc = SaColors.Rust,
            ring = SaColors.Brown.copy(alpha = 0.5f),
            dot = SaColors.Rust,
            discBase = SaColors.Peach,
            discFill = SaColors.Glass,
            discBorder = SaColors.GlassBorder,
            bars = SaColors.Ink,
        )
        val Ambient = CoreColors(
            ticks = SaColors.AmbientDim,
            arc = SaColors.AmbientDim,
            ring = SaColors.AmbientDim,
            dot = SaColors.AmbientContent,
            discBase = SaColors.AmbientBackground,
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

/** Progreso 0 → 1 que vuelve a empezar cada [durationMs]. */
@Composable
private fun InfiniteTransition.loop(animated: Boolean, durationMs: Int, label: String): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0f) }
    return animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = label)
}

/** Altura de la barra [index]: sube y baja entre 0,35 y 1 en [periodMs], desfasada de sus vecinas. */
@Composable
private fun InfiniteTransition.bar(animated: Boolean, periodMs: Int, index: Int, count: Int): State<Float> {
    if (!animated) return remember(index, count) { mutableFloatStateOf(STATIC_BARS[index * STATIC_BARS.size / count]) }
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

/** Alturas fijas en pausa y en modo ambiente (perfil de onda simétrico). */
private val STATIC_BARS = listOf(0.4f, 0.55f, 0.75f, 0.9f, 1f, 0.9f, 0.75f, 0.55f, 0.4f)

private const val MOCKUP_DIAMETER = 192f

/** Radios sobre 192: el núcleo ocupa 176 de diámetro. */
private const val TICKS_RADIUS = 86f
private const val ARC_RADIUS = 80.5f
private const val INNER_RADIUS = 34f
private const val DISC_RADIUS = 24f

private fun DrawScope.drawOuterTicks(colors: CoreColors, u: Float) {
    val radius = TICKS_RADIUS * u
    val ticks = 72
    val interval = (2 * PI * radius / ticks).toFloat()
    val tick = 1.1f * u
    drawCircle(
        color = colors.ticks,
        radius = radius,
        style = Stroke(width = 4f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tick, interval - tick))),
    )
}

/** Tres tramos de 84°, cada uno de seis segmentos de 11° separados por 3,6°. */
private fun DrawScope.drawSegmentedArc(colors: CoreColors, u: Float) {
    val radius = ARC_RADIUS * u
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val stroke = Stroke(width = 2.6f * u, cap = StrokeCap.Butt)
    for (group in 0 until 3) {
        for (segment in 0 until 6) {
            drawArc(colors.arc, startAngle = group * 120f + segment * 14.6f, sweepAngle = 11f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        }
    }
}

private fun DrawScope.drawInnerRing(colors: CoreColors, u: Float) {
    val radius = INNER_RADIUS * u
    val dots = 48
    val interval = (2 * PI * radius / dots).toFloat()
    drawCircle(
        color = colors.ring,
        radius = radius,
        style = Stroke(width = 1.4f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, interval))),
    )
    // El punto que delata el giro, arriba a la derecha.
    val angle = (-PI / 4).toFloat()
    drawCircle(colors.dot, radius = 2.6f * u, center = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle)))
}

private fun DrawScope.drawHalo(progress: Float, u: Float) {
    val radius = DISC_RADIUS * u * (1f + 0.18f * progress)
    val alpha = 0.35f + (0.08f - 0.35f) * progress
    drawCircle(SaColors.Rust.copy(alpha = alpha), radius = radius)
}

private fun DrawScope.drawDisc(colors: CoreColors, u: Float) {
    val radius = DISC_RADIUS * u
    drawCircle(colors.discBase, radius = radius)
    drawCircle(colors.discFill, radius = radius)
    drawCircle(colors.discBorder, radius = radius, style = Stroke(width = 1f * u))
}

/** Barras centradas en el disco: 5 de 3 × 22 con huecos de 2,2, o 9 más finas. */
private fun DrawScope.drawBars(colors: CoreColors, u: Float, heights: List<State<Float>>) {
    val count = heights.size
    val width = (if (count > 5) 2.2f else 3f) * u
    val gap = (if (count > 5) 1.6f else 2.2f) * u
    val maxHeight = 22f * u
    val total = count * width + (count - 1) * gap
    var x = center.x - total / 2
    for (state in heights) {
        val h = maxHeight * state.value
        drawRoundRect(colors.bars, topLeft = Offset(x, center.y - h / 2), size = Size(width, h), cornerRadius = CornerRadius(width / 2))
        x += width + gap
    }
}
