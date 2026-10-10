package com.soundalert.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import com.soundalert.wear.R

/** Colores medidos sobre los mockups. */
object SaColors {
    val Background = Color(0xFF000000)
    /** Filas, chip de contexto, círculo de la oreja. */
    val Surface = Color(0xFF1E1E1E)
    /** Filas de sonidos desactivados en el contexto. */
    val SurfaceDim = Color(0xFF151515)
    /** Barras de prioridad apagadas, barras laterales de la onda. */
    val Inactive = Color(0xFF3A3A3A)
    /** Fondo de la barra de progreso y de la píldora de confianza. */
    val Track = Color(0xFF2A2A2A)
    val ToggleOff = Color(0xFF616161)
    val OnSurface = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFA3A3A3)
    val Muted = Color(0xFF8A8A8A)

    val Danger = Color(0xFFE5251C)
    val Attention = Color(0xFFFFB21A)
    val Information = Color(0xFF6CB8FF)
}

/**
 * Archivo (OFL, `res/font/archivo.ttf`): fuente variable con ejes de peso y de
 * ancho, para la tipografía condensada de los mockups con un solo archivo.
 */
object SaFonts {
    private fun archivo(weight: Int, width: Float) = FontFamily(
        Font(
            R.font.archivo,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
        ),
    )

    /** Titulares de alerta ("SIRENA DE EMERGENCIA"). */
    val Black = archivo(weight = 900, width = 62f)

    /** "Escuchando", "Sonido detectado", títulos de pantalla. */
    val Bold = archivo(weight = 800, width = 72f)

    /** Filas de lista. */
    val SemiBold = archivo(weight = 600, width = 100f)

    /** Texto secundario. */
    val Regular = archivo(weight = 450, width = 100f)

    /** Etiquetas técnicas ("PELIGRO", "IA · ANALIZANDO") y horas. */
    val Mono = FontFamily.Monospace
}

object SaText {
    val alertTitle = TextStyle(fontFamily = SaFonts.Black, fontSize = 24.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
    val title = TextStyle(fontFamily = SaFonts.Bold, fontSize = 18.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
    val screenTitle = TextStyle(fontFamily = SaFonts.Bold, fontSize = 17.sp, lineHeight = 18.sp)
    val row = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 14.sp, lineHeight = 16.sp)
    val body = TextStyle(fontFamily = SaFonts.Regular, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    val clock = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 13.sp, lineHeight = 14.sp)
    val label = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 11.sp, letterSpacing = 1.2.sp)
    val time = TextStyle(fontFamily = SaFonts.Mono, fontSize = 11.sp, lineHeight = 12.sp)
}

@Composable
fun SoundAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
