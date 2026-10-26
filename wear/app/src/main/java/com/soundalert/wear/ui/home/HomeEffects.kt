package com.soundalert.wear.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.soundalert.wear.ui.components.JarvisMotion
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.theme.SaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

/**
 * Ondas de sonido: cada 2,5 s sale del borde del Jarvis (radio [fromRadius], centro en
 * [centerY], dp sobre 192) una onda circular muy fina en acento al 25 % que se expande
 * hasta casi el borde de la pantalla mientras se desvanece. Viven 6,2 s, así que hay 2 o
 * 3 a la vez, escalonadas. Al analizar el reloj avanza más deprisa y salen más seguido.
 */
fun DrawScope.drawListeningWaves(motion: JarvisMotion, u: Float, centerY: Float, fromRadius: Float) {
    val t = motion.clock.time
    val newest = floor(t / WAVE_PERIOD)
    for (k in 0 until 3) {
        val age = t - (newest - k) * WAVE_PERIOD
        val progress = age / WAVE_LIFE
        if (progress !in 0f..1f) continue
        val eased = 1f - (1f - progress).pow(2) // sale rápido y frena al final
        val radius = (fromRadius + (WAVE_TO - fromRadius) * eased) * u
        val alpha = 0.25f * (1f - progress).pow(1.4f)
        drawCircle(SaColors.Accent.copy(alpha = alpha), radius = radius, center = Offset(size.width / 2, centerY * u), style = Stroke(width = 0.8f * u))
    }
}

private const val WAVE_PERIOD = 2.5f
private const val WAVE_LIFE = 6.2f
private const val WAVE_TO = 86f

/**
 * Medidor en el borde: un anillo de marcas muy tenues (vino al 15 %) pegado al borde,
 * entre el margen de seguridad y la curva (r 85,5–89,5 sobre 192). Desde arriba, por los
 * dos lados, se iluminan en acento tantas marcas como el nivel real del micrófono; sin
 * nivel, una ola lenta recorre el círculo. Quieto (sin luces) en pausa.
 */
fun DrawScope.drawEdgeMeter(motion: JarvisMotion, u: Float, live: Boolean) {
    val ticks = 96
    val c = Offset(size.width / 2, size.height / 2)
    val base = SaColors.Wine.copy(alpha = 0.15f)
    val lit = SaColors.Accent.copy(alpha = 0.85f)
    val wave = (motion.clock.time * 360f / 7f) % 360f
    for (i in 0 until ticks) {
        val deg = i * 360f / ticks
        val intensity = when {
            !live -> 0f
            motion.hasLevel -> {
                // Ángulo desde las 12 (0–180) frente al alcance del nivel, con borde suave.
                val fromTop = abs(((deg + 180f) % 360f) - 180f)
                ((motion.mic * 180f - fromTop) / 14f).coerceIn(0f, 1f)
            }
            else -> {
                val d = abs(((deg - wave + 540f) % 360f) - 180f)
                exp(-(d / 22f).pow(2))
            }
        }
        val rad = Math.toRadians((deg - 90f).toDouble())
        val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        drawLine(lerp(base, lit, intensity), c + dir * (85.5f * u), c + dir * (89.5f * u), strokeWidth = (1f + 0.4f * intensity) * u)
    }
}

/**
 * "Escuchando" con vida: cada letra sube y baja (2 dp, escala 1 → 1,06) en secuencia de
 * izquierda a derecha (ciclo 1,8 s, 70 ms entre letras); un brillo vino → acento → vino
 * la recorre cada 3 s (como mucho al 50 %, así el contraste sigue por encima de 4,5:1); y
 * tres puntos en acento laten en secuencia con el pulso del halo del Jarvis. Con
 * [live] false (pausa, modo ambiente) todo queda quieto y sin puntos.
 */
@Composable
fun LivelyTitle(text: String, style: TextStyle, motion: JarvisMotion, live: Boolean, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    val clock = motion.clock
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.Bottom) {
        Row(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (live) {
                        clock.frame
                        // Banda de luz que cruza la palabra de izquierda a derecha cada 3 s.
                        val x = ((clock.seconds % SHIMMER_PERIOD) / SHIMMER_PERIOD) * (size.width * 1.6f) - size.width * 0.3f
                        val band = size.width * 0.28f
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to SaColors.Accent.copy(alpha = 0.5f),
                                1f to Color.Transparent,
                                startX = x - band,
                                endX = x + band,
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        ) {
            text.forEachIndexed { i, letter ->
                Text(
                    letter.toString(),
                    style = style,
                    color = SaColors.Wine,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        if (live) {
                            clock.frame
                            val lift = 0.5f - 0.5f * cos(TWO_PI * (clock.seconds - i * LETTER_DELAY) / LETTER_CYCLE)
                            translationY = -2.dp.toPx() * lift
                            scaleX = 1f + 0.06f * lift
                            scaleY = scaleX
                        }
                    },
                )
            }
        }
        if (live) {
            Spacer(Modifier.width(1.5.mdp))
            repeat(3) { k ->
                Box(
                    Modifier
                        .padding(bottom = 2.6.mdp)
                        .size(2.4.mdp)
                        .graphicsLayer {
                            clock.frame
                            // Cada punto sigue el pulso del halo, un poco después que el anterior.
                            val pulse = 0.5f - 0.5f * cos(clock.halo - k * 0.7f)
                            alpha = 0.35f + 0.65f * pulse
                            scaleX = 0.75f + 0.4f * pulse
                            scaleY = scaleX
                        },
                ) {
                    Canvas(Modifier.size(2.4.mdp)) { drawCircle(SaColors.Accent) }
                }
                if (k < 2) Spacer(Modifier.width(1.6.mdp))
            }
        }
    }
}

private const val LETTER_CYCLE = 1.8f
private const val LETTER_DELAY = 0.07f
private const val SHIMMER_PERIOD = 3f
