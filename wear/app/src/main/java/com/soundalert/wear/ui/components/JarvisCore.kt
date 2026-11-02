package com.soundalert.wear.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/** Estado del emblema: escuchando, analizando (más rápido, 9 barras) o en pausa (quieto). */
enum class CoreMode { LISTENING, ANALYZING, PAUSED }

/** Curva de todas las animaciones del inicio: cubic-bezier(0.45, 0, 0.55, 1). */
val SmoothEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/**
 * Emblema "Jarvis" sobre un viewBox de 200 × 200 centrado en (100, 100), capa por capa
 * según el rediseño del inicio:
 *  - Marcas: r 94, vino al 28 %, grosor 5, guiones [1,2, 6,2]. Quietas.
 *  - Arco con estela (r 82, acento, extremos redondeados), todo en un grupo que gira una
 *    vuelta cada 8 s: cabeza, dos estelas detrás cada vez más finas y tenues, y un
 *    destello en el lado opuesto.
 *  - Anillo interior: r 70, vino al 40 %, guiones [2, 5], con un punto en (100, 30). Gira
 *    al revés, una vuelta cada 16 s.
 *  - Halo r 56 en acento: escala 1 → 1,12 y opacidad 0,26 → 0,07, 3,2 s, ida y vuelta.
 *  - Núcleo r 40: vino al 10 % con borde vino al 70 %.
 *  - 5 barras que laten (scaleY 0,35 → 1, 1,2 s, ida y vuelta) con desfases.
 * Al analizar, todo pasa de forma progresiva a: arco en 2,6 s con cabeza y estelas más
 * largas y sin destello, anillo en 4 s con un segundo punto en (100, 170), un círculo r 54
 * (vino al 8 %, borde de acento) en lugar del halo y el núcleo, y 9 barras en 0,6 s.
 * En modo ambiente: estático y simplificado (marcas, cabeza del arco, anillo, núcleo y
 * barras quietas, en gris).
 *
 * Un único reloj de fotogramas integra la velocidad de cada capa (así el cambio de
 * velocidad no da saltos) y los valores solo se leen al dibujar: no hay recomposición
 * por fotograma.
 */
@Composable
fun JarvisCore(mode: CoreMode, modifier: Modifier = Modifier, motion: JarvisMotion = rememberJarvisMotion(mode)) {
    val ambient = LocalAmbient.current
    val clock = motion.clock
    Canvas(modifier) {
        clock.frame // se lee aquí: cada fotograma vuelve a dibujar sin recomponer
        val u = size.minDimension / VIEWBOX
        if (ambient) {
            drawAmbient(u)
            return@Canvas
        }
        val a = motion.analyzing
        val t = clock.seconds
        drawTicks(SaColors.Wine.copy(alpha = 0.28f), u)
        rotate(clock.arc) { drawArcWithTrail(u, a) }
        rotate(clock.inner) { drawInnerRing(SaColors.Wine.copy(alpha = 0.4f), SaColors.Wine, u, secondDot = a) }
        if (a < 1f) {
            val pulse = if (motion.animated) pingPong(t, HALO_HALF) else 0f
            drawHalo(pulse, u, alpha = 1f - a)
            drawCore(SaColors.Wine.copy(alpha = 0.1f), SaColors.Wine.copy(alpha = 0.7f), u, alpha = 1f - a)
        }
        if (a > 0f) drawAnalyzingCore(u, alpha = a)
        if (a < 1f) drawBars(SaColors.Wine.copy(alpha = 1f - a), u, LISTENING_BARS, t, LISTENING_BAR_HALF, motion.animated)
        if (a > 0f) drawBars(SaColors.Wine.copy(alpha = a), u, ANALYZING_BARS, t, ANALYZING_BAR_HALF, motion.animated)
    }
}

/**
 * Movimiento compartido del Jarvis y de los títulos animados: un único reloj de
 * fotogramas y el progreso hacia ANALYZING (0 → 1 en 0,9 s con [SmoothEasing]). Los
 * valores se leen al dibujar ([clock].frame cambia en cada fotograma).
 */
@Stable
class JarvisMotion internal constructor(
    val clock: JarvisClock,
    private val analyzingState: State<Float>,
    val animated: Boolean,
) {
    /** 0 escuchando → 1 analizando, con transición suave. */
    val analyzing: Float get() = analyzingState.value
}

/** Movimiento del Jarvis para [mode]; quieto en pausa y en modo ambiente. */
@Composable
fun rememberJarvisMotion(mode: CoreMode): JarvisMotion {
    val ambient = LocalAmbient.current
    val animated = !ambient && mode != CoreMode.PAUSED
    val analyzing = animateFloatAsState(if (mode == CoreMode.ANALYZING) 1f else 0f, tween(TRANSITION_MS, easing = SmoothEasing), label = "análisis")
    val clock = remember { JarvisClock() }
    val speed by rememberUpdatedState(analyzing.value)
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            val now = withInfiniteAnimationFrameNanos { it }
            clock.advance((now - last) / 1_000_000_000f, speed)
            last = now
        }
    }
    return remember(clock, analyzing, animated) { JarvisMotion(clock, analyzing, animated) }
}

/** Duración del paso escuchando ↔ analizando (velocidades, capas y tamaño del emblema). */
const val TRANSITION_MS = 900

/**
 * Ángulos de las capas que giran, integrados fotograma a fotograma. [frame] es lo único
 * observable: sube en cada fotograma para que el Canvas se vuelva a dibujar. [seconds]
 * avanza a ritmo real (latidos de halo, barras y letras).
 */
class JarvisClock {
    private val tick = mutableLongStateOf(0)
    val frame: Long get() = tick.longValue

    var seconds = 0f; private set
    var arc = 0f; private set
    var inner = 0f; private set

    /** [analyzing] 0 → 1: la velocidad de cada capa se interpola entre escucha y análisis. */
    fun advance(dt: Float, analyzing: Float) {
        arc = (arc + dt * 360f / lerp(ARC_LISTENING_S, ARC_ANALYZING_S, analyzing)) % 360f
        inner = (inner - dt * 360f / lerp(RING_LISTENING_S, RING_ANALYZING_S, analyzing)) % 360f
        seconds += dt
        tick.longValue++
    }
}

/** Segundos por vuelta del arco y del anillo interior, al escuchar y al analizar. */
internal const val ARC_LISTENING_S = 8f
internal const val ARC_ANALYZING_S = 2.6f
internal const val RING_LISTENING_S = 16f
internal const val RING_ANALYZING_S = 4f

/**
 * Ida y vuelta con [SmoothEasing]: 0 → 1 en [half] segundos y de nuevo a 0 en otros
 * [half] (como `animation-direction: alternate`).
 */
fun pingPong(seconds: Float, half: Float): Float {
    val cycle = ((seconds / half) % 2f + 2f) % 2f
    return SmoothEasing.transform(if (cycle < 1f) cycle else 2f - cycle)
}

internal fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

/**
 * Un trazo del arco: guion y hueco (sumen la circunferencia, 2π·82 ≈ 515,2), desfase del
 * guion (como stroke-dashoffset), grosor y opacidad.
 */
internal data class ArcStroke(val dash: Float, val gap: Float, val offset: Float, val width: Float, val alpha: Float)

/** Cabeza, estelas y destello del arco para [analyzing] 0 (escuchando) → 1 (analizando). */
internal fun arcStrokes(analyzing: Float): List<ArcStroke> {
    fun stroke(dash: Pair<Float, Float>, offset: Pair<Float, Float>, width: Pair<Float, Float>, alpha: Float): ArcStroke {
        val d = lerp(dash.first, dash.second, analyzing)
        return ArcStroke(d, ARC_LENGTH - d, lerp(offset.first, offset.second, analyzing), lerp(width.first, width.second, analyzing), alpha)
    }
    return listOf(
        stroke(70f to 110f, 0f to 0f, 3.5f to 4f, alpha = 1f), // cabeza
        stroke(40f to 50f, 44f to 54f, 3f to 3f, alpha = 0.45f), // estela 1
        stroke(30f to 40f, 78f to 98f, 2.5f to 2.5f, alpha = 0.18f), // estela 2
        stroke(14f to 14f, -250f to -250f, 2f to 2f, alpha = 0.6f * (1f - analyzing)), // destello opuesto
    )
}

/** Circunferencia del arco (r 82) en unidades del viewBox. */
internal const val ARC_LENGTH = 515.2f

/** Barra del SVG: x de su lado izquierdo, alto máximo y desfase (s, negativo: ya empezada). */
internal class Bar(val x: Float, val height: Float, val delay: Float)

internal val LISTENING_BARS = listOf(
    Bar(82f, 18f, -0.5f),
    Bar(90f, 30f, -0.25f),
    Bar(98f, 40f, 0f),
    Bar(106f, 30f, -0.75f),
    Bar(114f, 18f, -1f),
)

/** Analizando: x = 66…130 cada 8, con desfases repartidos por todo el ciclo. */
internal val ANALYZING_BARS = listOf(12f, 24f, 36f, 48f, 56f, 44f, 30f, 20f, 12f)
    .zip(listOf(-0.2f, -0.5f, -0.1f, -0.7f, -0.35f, -0.9f, -0.05f, -0.6f, -0.25f))
    .mapIndexed { i, (h, delay) -> Bar(66f + 8f * i, h, delay) }

/** Medio ciclo (s) del latido de barras y halo. */
private const val LISTENING_BAR_HALF = 1.2f
private const val ANALYZING_BAR_HALF = 0.6f
private const val HALO_HALF = 3.2f

private const val BAR_MIN = 0.35f
private const val VIEWBOX = 200f

private fun DrawScope.drawTicks(color: Color, u: Float) {
    drawCircle(color, radius = 94f * u, style = Stroke(width = 5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * u, 6.2f * u))))
}

private fun DrawScope.drawArcStroke(stroke: ArcStroke, color: Color, u: Float) {
    if (stroke.alpha <= 0f) return
    drawCircle(
        color.copy(alpha = color.alpha * stroke.alpha),
        radius = 82f * u,
        style = Stroke(
            width = stroke.width * u,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke.dash * u, stroke.gap * u), stroke.offset * u),
        ),
    )
}

private fun DrawScope.drawArcWithTrail(u: Float, analyzing: Float) {
    arcStrokes(analyzing).forEach { drawArcStroke(it, SaColors.Accent, u) }
}

private fun DrawScope.drawInnerRing(ring: Color, dot: Color, u: Float, secondDot: Float) {
    drawCircle(ring, radius = 70f * u, style = Stroke(width = 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 5f * u))))
    // Punto en (100, 30); al analizar aparece otro en (100, 170).
    drawCircle(dot, radius = 2.6f * u, center = Offset(center.x, center.y - 70f * u))
    if (secondDot > 0f) drawCircle(dot.copy(alpha = dot.alpha * secondDot), radius = 2.6f * u, center = Offset(center.x, center.y + 70f * u))
}

private fun DrawScope.drawHalo(progress: Float, u: Float, alpha: Float) {
    val haloAlpha = lerp(0.26f, 0.07f, progress) * alpha
    drawCircle(SaColors.Accent.copy(alpha = haloAlpha), radius = 56f * u * lerp(1f, 1.12f, progress))
}

private fun DrawScope.drawCore(fill: Color, border: Color, u: Float, alpha: Float) {
    drawCircle(fill.copy(alpha = fill.alpha * alpha), radius = 40f * u)
    drawCircle(border.copy(alpha = border.alpha * alpha), radius = 40f * u, style = Stroke(width = 1.5f * u))
}

private fun DrawScope.drawAnalyzingCore(u: Float, alpha: Float) {
    drawCircle(SaColors.Wine.copy(alpha = 0.08f * alpha), radius = 54f * u)
    drawCircle(SaColors.Accent.copy(alpha = alpha), radius = 54f * u, style = Stroke(width = 1.5f * u))
}

/** Barras de ancho 4 y radio 2 centradas en y = 100; laten de 0,35 a 1 de su alto. */
private fun DrawScope.drawBars(color: Color, u: Float, bars: List<Bar>, seconds: Float, half: Float, animated: Boolean) {
    val width = 4f * u
    for (bar in bars) {
        val scale = if (animated) lerp(BAR_MIN, 1f, pingPong(seconds - bar.delay, half)) else 1f
        val h = bar.height * u * scale
        val x = center.x + (bar.x - 100f) * u
        drawRoundRect(color, topLeft = Offset(x, center.y - h / 2), size = Size(width, h), cornerRadius = CornerRadius(2f * u))
    }
}

/** Modo ambiente: emblema estático y simplificado, en gris sobre negro. */
private fun DrawScope.drawAmbient(u: Float) {
    drawTicks(SaColors.AmbientDim, u)
    drawArcStroke(arcStrokes(0f).first(), SaColors.AmbientDim, u)
    drawInnerRing(SaColors.AmbientDim, SaColors.AmbientContent, u, secondDot = 0f)
    drawCore(Color.Transparent, SaColors.AmbientDim, u, alpha = 1f)
    drawBars(SaColors.AmbientContent, u, LISTENING_BARS, 0f, LISTENING_BAR_HALF, animated = false)
}
