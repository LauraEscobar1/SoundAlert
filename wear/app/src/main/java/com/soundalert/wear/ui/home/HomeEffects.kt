package com.soundalert.wear.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.soundalert.wear.ui.components.JarvisMotion
import com.soundalert.wear.ui.components.SmoothEasing
import com.soundalert.wear.ui.theme.SaColors

/** Ritmo de la animación de un título: ciclo y desfase entre letras (s) de la onda y del brillo. */
data class TitleRhythm(val waveCycle: Float, val waveDelay: Float, val shineCycle: Float, val shineDelay: Float) {
    companion object {
        /** "Escuchando": onda de 2,8 s y brillo de 3,6 s. */
        val Listening = TitleRhythm(waveCycle = 2.8f, waveDelay = 0.09f, shineCycle = 3.6f, shineDelay = 0.12f)

        /** "Sonido detectado": el mismo movimiento, más rápido. */
        val Analyzing = TitleRhythm(waveCycle = 1.6f, waveDelay = 0.05f, shineCycle = 2.2f, shineDelay = 0.07f)
    }
}

/**
 * Título con vida: cada letra se anima por separado.
 *  - Onda: sube 1,25 dp (al 40 % del ciclo), baja 0,25 dp por debajo de su sitio (al 70 %)
 *    y vuelve, con un pequeño desfase entre letras.
 *  - Brillo: el color va de vino hacia acento y regresa, desfasado letra a letra para que
 *    el brillo recorra la palabra.
 * Con [live] false (pausa, modo ambiente) queda quieto en vino. El color y el
 * desplazamiento se leen al dibujar: no hay recomposición por fotograma.
 */
@Composable
fun AnimatedTitle(text: String, style: TextStyle, motion: JarvisMotion, rhythm: TitleRhythm, live: Boolean, modifier: Modifier = Modifier) {
    val clock = motion.clock
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { i, letter ->
            BasicText(
                letter.toString(),
                style = style,
                maxLines = 1,
                color = ColorProducer {
                    if (!live) return@ColorProducer SaColors.Wine
                    clock.frame
                    lerp(SaColors.Wine, SaColors.Accent, SHINE_MAX * titleShine(cyclePhase(clock.seconds - i * rhythm.shineDelay, rhythm.shineCycle)))
                },
                modifier = Modifier.graphicsLayer {
                    if (live) {
                        clock.frame
                        translationY = titleWave(cyclePhase(clock.seconds - i * rhythm.waveDelay, rhythm.waveCycle)).dp.toPx()
                    }
                },
            )
        }
    }
}

/** Fase (0–1) dentro de un ciclo de [cycle] segundos. */
internal fun cyclePhase(seconds: Float, cycle: Float): Float = ((seconds / cycle) % 1f + 1f) % 1f

/**
 * Desplazamiento vertical (dp) de una letra en la fase [phase] de la onda: 0 → −1,25 (al
 * 40 %) → +0,25 (al 70 %) → 0, con [SmoothEasing] en cada tramo.
 */
internal fun titleWave(phase: Float): Float = when {
    phase < 0.4f -> segment(0f, -1.25f, phase / 0.4f)
    phase < 0.7f -> segment(-1.25f, 0.25f, (phase - 0.4f) / 0.3f)
    else -> segment(0.25f, 0f, (phase - 0.7f) / 0.3f)
}

/** Brillo (0–1) en la fase [phase]: va y vuelve una vez por ciclo. */
internal fun titleShine(phase: Float): Float = SmoothEasing.transform(if (phase < 0.5f) phase * 2f else 2f - phase * 2f)

private fun segment(from: Float, to: Float, fraction: Float) = from + (to - from) * SmoothEasing.transform(fraction.coerceIn(0f, 1f))

/**
 * Hasta dónde llega el brillo hacia el acento: al 70 % el texto sigue por encima de 4,5:1
 * sobre el rosa (el acento puro queda en 3,8:1). Lo comprueba ContrastTest.
 */
const val SHINE_MAX = 0.7f
