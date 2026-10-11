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

/**
 * Tamaños medidos sobre los mockups (pantalla de 192 dp, escala 1:1). Mismas familias,
 * pesos y anchos de siempre (SaFonts); solo cambian tamaño e interlineado.
 */
object SaText {
    /** "SIRENA DE / EMERGENCIA": mayúsculas de 13,1 dp, 17,2 dp entre líneas. */
    val alertTitle = TextStyle(fontFamily = SaFonts.Black, fontSize = 19.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)

    /** "Escuchando", "Sonido detectado", "Bocina de / vehículo", "Timbre". */
    val title = TextStyle(fontFamily = SaFonts.Bold, fontSize = 16.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)

    /** "Contexto", "Sonidos en calle", "Historial". */
    val screenTitle = TextStyle(fontFamily = SaFonts.Bold, fontSize = 11.5.sp, lineHeight = 13.sp)

    /** Filas de las listas ("Calle", "Sirena", "Bocina"). */
    val row = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 9.5.sp, lineHeight = 11.sp)

    /** Subtítulos ("Timbre · Alarma · Bebé", "Calle · ahora"). */
    val body = TextStyle(fontFamily = SaFonts.Regular, fontSize = 7.8.sp, lineHeight = 9.5.sp, textAlign = TextAlign.Center)

    /** "Entendido". */
    val button = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)

    /** Hora de las pantallas A y B. */
    val clock = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp)

    /** Hora de las listas (D, E, F): más pequeña y gris en el mockup. */
    val clockSmall = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 8.5.sp, lineHeight = 10.sp)

    /** "PELIGRO", "ATENCIÓN", "AVISO". El mockup no separa las letras más allá de la monoespaciada. */
    val label = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 7.9.sp, lineHeight = 9.sp)

    /** "IA · ANALIZANDO", chip "CASA", píldora "94%". */
    val labelSmall = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 7.sp, lineHeight = 8.5.sp)

    /** Etiqueta junto al título de las listas ("MANUAL", "HOY"). */
    val tag = TextStyle(fontFamily = SaFonts.Mono, fontSize = 5.5.sp, lineHeight = 7.sp)

    /** Hora del historial ("10:12"). */
    val time = TextStyle(fontFamily = SaFonts.Mono, fontSize = 6.5.sp, lineHeight = 8.sp)
}

@Composable
fun SoundAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
