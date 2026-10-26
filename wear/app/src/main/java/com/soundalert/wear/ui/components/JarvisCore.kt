package com.soundalert.wear.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Estado del emblema: escuchando, analizando (más rápido, 9 barras) o en pausa (quieto). */
enum class CoreMode { LISTENING, ANALYZING, PAUSED }

/**
 * Emblema "Jarvis" sobre un viewBox de 200 × 200 centrado en (100, 100). Capas base del SVG
 * del diseño (marcas r 94, arco segmentado r 82 con guiones [90, 26, 18, 26, 60, 295],
 * anillo interior r 70 con su punto, halo r 54, núcleo r 40 y barras) y, encima:
 *  - El arco segmentado gira y respira en grosor (de 4,5 a 6,7 unidades del viewBox cada
 *    3 s: 3 → 4,5 dp en un emblema de 134 dp, escalado con el tamaño), con una estela de
 *    barrido que se desvanece detrás de su segmento más largo.
 *  - Un segundo arco fino y corto (vino al 50 %, r 62) gira al revés a otra velocidad.
 *  - Cuatro partículas orbitan entre el anillo interior y el arco, a distintas
 *    velocidades, con una opacidad que sube y baja.
 *  - El halo pulsa (escala 1 → 1,18, opacidad 0,35 → 0,08, 2,4 s, curva senoidal).
 *  - Las barras siguen el nivel real del micrófono ([level], 0–1, suavizado con un resorte)
 *    sobre una oscilación desfasada; sin nivel, solo la oscilación.
 * Al pasar a ANALYZING todo acelera de forma progresiva (0,9 s, FastOutSlowIn): los
 * anillos pasan a 2,2 s y 3,5 s por vuelta, las 5 barras se funden en 9 más rápidas y el
 * halo y el núcleo dan paso al círculo r 54 con trazo de acento y dos puntos en el anillo.
 * En modo ambiente: estático, sin halo, estela, arco fino ni partículas, en gris.
 *
 * Un único reloj de fotogramas integra la velocidad de cada capa (así la aceleración no
 * da saltos) y los valores solo se leen al dibujar: no hay recomposición por fotograma.
 */
@Composable
fun JarvisCore(mode: CoreMode, modifier: Modifier = Modifier, level: Float? = null) {
    val ambient = LocalAmbient.current
    val animated = !ambient && mode != CoreMode.PAUSED
    val analyzing by animateFloatAsState(if (mode == CoreMode.ANALYZING) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "análisis")
    val mic by animateFloatAsState(level?.coerceIn(0f, 1f) ?: 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), label = "nivel")
    val hasLevel = level != null
    val clock = remember { JarvisClock() }
    val speed by rememberUpdatedState(analyzing)
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            val now = withInfiniteAnimationFrameNanos { it }
            clock.advance((now - last) / 1_000_000_000f, speed)
            last = now
        }
    }
    val colors = if (ambient) JarvisColors.Ambient else JarvisColors.Day
    Canvas(modifier) {
        clock.frame // se lee aquí: cada fotograma vuelve a dibujar sin recomponer
        val u = size.minDimension / VIEWBOX
        val a = analyzing
        drawTicks(colors, u)
        if (!ambient) rotate(clock.thinArc) { drawThinArc(u) }
        rotate(clock.arc) {
            if (!ambient) drawTrail(u)
            val breath = if (animated) 0.5f - 0.5f * cos(clock.breath) else 0f
            drawSegmentedArc(colors, u, widthPx = (4.48f + 2.24f * breath) * u)
        }
        rotate(clock.inner) { drawInnerRing(colors, u, secondDot = a) }
        if (!ambient) drawParticles(clock, u, a)
        if (!ambient && a < 1f) drawHalo(if (animated) 0.5f - 0.5f * cos(clock.halo) else 0f, u, alpha = 1f - a)
        if (a < 1f) drawCore(colors, u, alpha = 1f - a)
        if (a > 0f) drawAnalyzingCore(colors, u, alpha = a)
        val micMix = if (hasLevel && animated) mic else -1f
        if (a < 1f) drawBars(colors, u, LISTENING_BARS, clock.bars, animated, micMix, alpha = 1f - a)
        if (a > 0f) drawBars(colors, u, ANALYZING_BARS, clock.bars * 2f, animated, micMix, alpha = a)
    }
}

/**
 * Ángulos y fases de cada capa, integrados fotograma a fotograma. [frame] es lo único
 * observable: sube en cada fotograma para que el Canvas se vuelva a dibujar.
 */
private class JarvisClock {
    private val tick = mutableLongStateOf(0)
    val frame: Long get() = tick.longValue
    var arc = 0f; private set
    var thinArc = 0f; private set
    var inner = 0f; private set
    var breath = 0f; private set
    var halo = 0f; private set
    var bars = 0f; private set
    var time = 0f; private set

    /** [analyzing] 0 → 1: la velocidad de cada capa se interpola entre escucha y análisis. */
    fun advance(dt: Float, analyzing: Float) {
        fun lerp(a: Float, b: Float) = a + (b - a) * analyzing
        arc = (arc + dt * lerp(360f / 9f, 360f / 2.2f)) % 360f
        thinArc = (thinArc - dt * lerp(360f / 6f, 360f / 1.8f)) % 360f
        inner = (inner - dt * lerp(360f / 14f, 360f / 3.5f)) % 360f
        breath = (breath + dt * TWO_PI / 3f) % TWO_PI
        halo = (halo + dt * TWO_PI / 4.8f) % TWO_PI // 2,4 s de ida y 2,4 de vuelta
        // Barras: 1,1 s de subida y 1,1 de bajada al escuchar (el doble de rápido al analizar).
        bars = (bars + dt * TWO_PI / lerp(2.2f, 1.1f)) % (TWO_PI * 1000f)
        time += dt * lerp(1f, 2.6f)
        tick.longValue++
    }
}

private class JarvisColors(
    val ticks: Color,
    val arc: Color,
    val ring: Color,
    val dot: Color,
    val coreFill: Color,
    val coreBorder: Color,
    val analyzingFill: Color,
    val analyzingBorder: Color,
    val bars: Color,
) {
    companion object {
        val Day = JarvisColors(
            ticks = SaColors.Wine.copy(alpha = 0.35f),
            arc = SaColors.Accent,
            ring = SaColors.Wine.copy(alpha = 0.55f),
            dot = SaColors.Wine,
            coreFill = SaColors.Wine.copy(alpha = 0.12f),
            coreBorder = SaColors.Wine.copy(alpha = 0.8f),
            analyzingFill = SaColors.Wine.copy(alpha = 0.08f),
            analyzingBorder = SaColors.Accent,
            bars = SaColors.Wine,
        )
        val Ambient = JarvisColors(
            ticks = SaColors.AmbientDim,
            arc = SaColors.AmbientDim,
            ring = SaColors.AmbientDim,
            dot = SaColors.AmbientContent,
            coreFill = Color.Transparent,
            coreBorder = SaColors.AmbientDim,
            analyzingFill = Color.Transparent,
            analyzingBorder = SaColors.AmbientDim,
            bars = SaColors.AmbientContent,
        )
    }
}

/** Barra del SVG: centro en x, alto máximo y desfase (s, negativo: ya empezada). */
private class Bar(val x: Float, val height: Float, val delay: Float)

private val LISTENING_BARS = listOf(Bar(84f, 18f, -0.4f), Bar(92f, 30f, -0.2f), Bar(100f, 40f, 0f), Bar(108f, 30f, -0.6f), Bar(116f, 18f, -0.8f))

/** Analizando: x = 68…132 cada 8, con los desfases repartidos en los 0,55 s. */
private val ANALYZING_BARS = listOf(12f, 24f, 36f, 48f, 56f, 44f, 30f, 20f, 12f)
    .mapIndexed { i, h -> Bar(68f + 8f * i, h, -0.55f * i / 9f) }

private const val BAR_MIN = 0.35f
private const val VIEWBOX = 200f
private const val TWO_PI = (2 * PI).toFloat()

private fun DrawScope.drawTicks(colors: JarvisColors, u: Float) {
    val ticks = 80
    for (i in 0 until ticks) {
        val deg = i * 360f / ticks
        val rad = Math.toRadians((deg - 90f).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        drawLine(
            colors.ticks,
            Offset(center.x + 91.5f * u * c, center.y + 91.5f * u * s),
            Offset(center.x + 96.5f * u * c, center.y + 96.5f * u * s),
            strokeWidth = 1.2f * u,
        )
    }
}

private fun DrawScope.drawSegmentedArc(colors: JarvisColors, u: Float, widthPx: Float) {
    val pattern = floatArrayOf(90f, 26f, 18f, 26f, 60f, 295f).map { it * u }.toFloatArray()
    drawCircle(colors.arc, radius = 82f * u, style = Stroke(width = widthPx, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(pattern)))
}

/**
 * Estela del segmento largo: el guion de 90 empieza en 0° y avanza en el sentido de giro,
 * así que la estela ocupa los 70° anteriores con un barrido que se desvanece hacia atrás.
 */
private fun DrawScope.drawTrail(u: Float) {
    val r = 82f * u
    val brush = Brush.sweepGradient(
        0f to Color.Transparent,
        (290f / 360f) to Color.Transparent,
        1f to SaColors.Accent.copy(alpha = 0.45f),
        center = center,
    )
    drawArc(brush, startAngle = -70f, sweepAngle = 70f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(width = 7f * u))
}

/** Segundo arco: fino, corto (≈ 40°) y en vino al 50 %, en r 62. */
private fun DrawScope.drawThinArc(u: Float) {
    val r = 62f * u
    drawArc(SaColors.Wine.copy(alpha = 0.5f), startAngle = 0f, sweepAngle = 40f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(width = 1.4f * u, cap = StrokeCap.Round))
}

private fun DrawScope.drawInnerRing(colors: JarvisColors, u: Float, secondDot: Float) {
    drawCircle(colors.ring, radius = 70f * u, style = Stroke(width = 1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 5f * u))))
    // Punto en (100, 30); al analizar aparece otro en (100, 170).
    drawCircle(colors.dot, radius = 3f * u, center = Offset(center.x, center.y - 70f * u))
    if (secondDot > 0f) drawCircle(colors.dot.copy(alpha = colors.dot.alpha * secondDot), radius = 3f * u, center = Offset(center.x, center.y + 70f * u))
}

/** Radio (viewBox), velocidad (°/s al escuchar) y desfase (rad) de cada partícula. */
private val PARTICLES = listOf(
    Triple(74f, 38f, 0f),
    Triple(77f, -52f, 1.7f),
    Triple(79.5f, 27f, 3.1f),
    Triple(75.5f, -33f, 4.6f),
)

private fun DrawScope.drawParticles(clock: JarvisClock, u: Float, analyzing: Float) {
    PARTICLES.forEachIndexed { i, (radius, speed, phase) ->
        val deg = (clock.time * speed + phase * 57.3f) % 360f
        val rad = Math.toRadians(deg.toDouble())
        val alpha = 0.25f + 0.65f * (0.5f + 0.5f * sin(clock.time * (1.3f + 0.4f * i) + phase))
        val color = if (i % 2 == 0) SaColors.Accent else SaColors.Wine
        drawCircle(
            color.copy(alpha = alpha),
            radius = (1.6f + 0.5f * (i % 2) + 0.4f * analyzing) * u,
            center = Offset(center.x + radius * u * cos(rad).toFloat(), center.y + radius * u * sin(rad).toFloat()),
        )
    }
}

private fun DrawScope.drawHalo(progress: Float, u: Float, alpha: Float) {
    val haloAlpha = (0.35f + (0.08f - 0.35f) * progress) * alpha
    drawCircle(SaColors.Accent.copy(alpha = haloAlpha), radius = 54f * u * (1f + 0.18f * progress))
}

private fun DrawScope.drawCore(colors: JarvisColors, u: Float, alpha: Float) {
    drawCircle(colors.coreFill.copy(alpha = colors.coreFill.alpha * alpha), radius = 40f * u)
    drawCircle(colors.coreBorder.copy(alpha = colors.coreBorder.alpha * alpha), radius = 40f * u, style = Stroke(width = 1.5f * u))
}

private fun DrawScope.drawAnalyzingCore(colors: JarvisColors, u: Float, alpha: Float) {
    drawCircle(colors.analyzingFill.copy(alpha = colors.analyzingFill.alpha * alpha), radius = 54f * u)
    drawCircle(colors.analyzingBorder.copy(alpha = colors.analyzingBorder.alpha * alpha), radius = 54f * u, style = Stroke(width = 1.5f * u))
}

/**
 * Barras de ancho 4 y radio 2 centradas en y = 100. Cada una oscila con una curva
 * senoidal (suave en los extremos) desfasada; con nivel de micrófono ([mic] ≥ 0), la
 * altura mezcla la oscilación con ese nivel.
 */
private fun DrawScope.drawBars(colors: JarvisColors, u: Float, bars: List<Bar>, phase: Float, animated: Boolean, mic: Float, alpha: Float) {
    val width = 4f * u
    val color = colors.bars.copy(alpha = colors.bars.alpha * alpha)
    val period = if (bars.size > 5) 1.1f else 2.2f
    for (bar in bars) {
        val osc = if (animated) 0.5f - 0.5f * cos(phase - TWO_PI * bar.delay / period) else 1f
        val amount = if (mic >= 0f) 0.45f * osc + 0.55f * mic * (0.6f + 0.4f * osc) else osc
        val scale = if (animated) BAR_MIN + (1f - BAR_MIN) * amount else 1f
        val h = bar.height * u * scale
        val x = center.x + (bar.x - 100f) * u
        drawRoundRect(color, topLeft = Offset(x - width / 2, center.y - h / 2), size = Size(width, h), cornerRadius = CornerRadius(2f * u))
    }
}
