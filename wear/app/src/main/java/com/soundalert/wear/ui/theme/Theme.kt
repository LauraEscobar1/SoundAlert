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

/**
 * Tokens del diseño final. Los colores de alerta (Danger, Attention, Information)
 * solo se usan en las pantallas de alerta y en los indicadores de nivel.
 *
 * Contraste sobre el durazno: Ink 8,9:1, Brown 7,0:1; sobre el vidrio, Ink 7,0:1.
 */
object SaColors {
    /** Fondo sólido de reposo, análisis, contexto, sonidos e historial. */
    val Peach = Color(0xFFF4A07A)
    /** Texto secundario e iconos. */
    val Brown = Color(0xFF4A1A0C)
    /** Títulos y textos importantes (siempre en negrita). */
    val Ink = Color(0xFF141414)
    /** Acento del anillo del núcleo y de la sesión en curso. */
    val Rust = Color(0xFFA33A12)
    /** Arco de la sesión seleccionada; base del vidrio. */
    val Orange = Color(0xFFC8501C)

    /** Vidrio de lo seleccionado o activo: naranja al 32 % con borde blanco al 45 %. */
    val Glass = Orange.copy(alpha = 0.32f)
    val GlassBorder = Color.White.copy(alpha = 0.45f)

    /** Filas y pistas tenues (café al 10 %). */
    val BrownFaint = Brown.copy(alpha = 0.10f)

    val Danger = Color(0xFFE1251B)
    val Attention = Color(0xFFF5A90A)
    val Information = Color(0xFF5BA8F5)

    /** Modo ambiente (always-on): fondo negro y trazos grises, sin color. */
    val AmbientBackground = Color(0xFF000000)
    val AmbientContent = Color(0xFFD9D9D9)
    val AmbientDim = Color(0xFF7A7A7A)

}

/**
 * Archivo (OFL, `res/font/archivo.ttf`): fuente variable con ejes de peso y de
 * ancho, para la tipografía condensada del diseño con un solo archivo.
 */
object SaFonts {
    private fun archivo(weight: Int, width: Float) = FontFamily(
        Font(
            R.font.archivo,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
        ),
    )

    /** Titulares de alerta ("ALARMA DE INCENDIO"). */
    val Black = archivo(weight = 900, width = 62f)

    /** "Escuchando", "Sonido detectado", títulos de pantalla. */
    val Bold = archivo(weight = 800, width = 72f)

    /** Nombres de filas, celdas y tarjetas. */
    val SemiBold = archivo(weight = 700, width = 88f)

    /** Texto secundario. */
    val Regular = archivo(weight = 450, width = 100f)

    /** Etiquetas en mayúsculas ("PELIGRO", "IA · ANALIZANDO", "5 DE 7") y horas. */
    val Mono = FontFamily.Monospace
}

/** Tamaños sobre la pantalla de 192 dp (escala 1:1). */
object SaText {
    /** "ALARMA DE / INCENDIO". */
    val alertTitle = TextStyle(fontFamily = SaFonts.Black, fontSize = 19.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)

    /** "Escuchando", "Sonido detectado". */
    val title = TextStyle(fontFamily = SaFonts.Bold, fontSize = 16.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)

    /** "Contexto", "Calle", nombre de la sesión. */
    val screenTitle = TextStyle(fontFamily = SaFonts.Bold, fontSize = 12.sp, lineHeight = 13.sp)

    /** Filas de contexto. */
    val row = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 10.sp, lineHeight = 11.sp)

    /** Nombre dentro de una celda del panal. */
    val cell = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 7.sp, lineHeight = 7.5.sp, textAlign = TextAlign.Center)

    /** Subtítulos ("Timbre · Alarma · Bebé", "Calle · ahora"). */
    val body = TextStyle(fontFamily = SaFonts.Regular, fontSize = 8.sp, lineHeight = 9.5.sp, textAlign = TextAlign.Center)

    /** "Entendido". */
    val button = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 10.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)

    /** Hora de la pantalla de reposo. */
    val clock = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp)

    /** "PELIGRO", "ATENCIÓN", "AVISO". */
    val label = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, lineHeight = 9.sp)

    /** "IA · ANALIZANDO", chip "CASA", píldora "94%", "SESIÓN 3 DE 4". */
    val labelSmall = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 7.sp, lineHeight = 8.5.sp)

    /** Etiquetas junto a un título ("AUTO", "5 DE 7") y pies ("TOCA PARA ACTIVAR"). */
    val tag = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 6.5.sp, lineHeight = 7.5.sp)

}

@Composable
fun SoundAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
