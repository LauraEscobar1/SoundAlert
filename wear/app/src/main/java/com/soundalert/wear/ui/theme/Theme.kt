package com.soundalert.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import com.soundalert.wear.R

/**
 * Tokens del diseño (paleta rosa "Cherry Marshmallow"). Toda la interfaz toma sus colores
 * de aquí. Los colores de alerta (Danger, Attention, Information) y la tinta [Ink] solo
 * se usan en las pantallas de alerta y en los indicadores de nivel.
 *
 * Contraste (lo comprueba ContrastTest): vino sobre el rosa 7,1:1, sobre el vidrio 5,2:1
 * y sobre las filas tenues 6,2:1; tinta sobre peligro 4,7:1, atención 9,3:1, aviso 7,3:1.
 */
object SaColors {
    /** Fondo sólido de escucha, análisis, contexto, sonidos e historial. */
    val Background = Color(0xFFEAADC1)
    /** Títulos, texto e iconos (los títulos en negrita). */
    val Wine = Color(0xFF5F0C2F)
    /** Acento: arco del Jarvis, sesión en curso y sesión seleccionada, detalles. */
    val Accent = Color(0xFFA3245C)

    /** Vidrio de lo seleccionado o activo: acento al 25 % con borde blanco al 45 %; encima, vino en negrita. */
    val Glass = Accent.copy(alpha = 0.25f)
    val GlassBorder = Color.White.copy(alpha = 0.45f)

    /** Pistas y chips tenues (vino al 10 %). */
    val WineFaint = Wine.copy(alpha = 0.10f)

    /** Tinta del texto sobre los colores de alerta. */
    val Ink = Color(0xFF141414)
    val Danger = Color(0xFFF03A2E)
    val Attention = Color(0xFFF5A90A)
    val Information = Color(0xFF5BA8F5)

    /** Modo ambiente (always-on): fondo negro y trazos grises, sin color. */
    val AmbientBackground = Color(0xFF000000)
    val AmbientContent = Color(0xFFD9D9D9)
    val AmbientDim = Color(0xFF7A7A7A)

    // Nombres de la paleta anterior: se retiran cuando migre la última pantalla.
    val Peach = Background
    val Brown = Wine
    val Rust = Accent
    val Orange = Accent
    val BrownFaint = WineFaint
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

    /** Titulares de alerta ("ALARMA DE INCENDIO"): negra y condensada. */
    val Black = archivo(weight = 880, width = 76f)

    /** "Escuchando", "Sonido detectado", nombre de la sesión: negrita condensada. */
    val Bold = archivo(weight = 800, width = 76f)

    /** "Contexto", "Calle" en los encabezados. */
    val Heading = archivo(weight = 780, width = 92f)

    /** Filas, celdas, tarjetas, botones y la hora. */
    val SemiBold = archivo(weight = 680, width = 96f)

    /** Texto secundario. */
    val Regular = archivo(weight = 430, width = 100f)

    /** Etiquetas en mayúsculas ("PELIGRO", "IA · ANALIZANDO", "5 DE 7"). */
    val Mono = FontFamily.Monospace
}

/**
 * Tamaños medidos sobre el mockup final (pantalla de 192 dp, escala 1:1): cada uno
 * reproduce la altura de mayúscula y el ancho de su texto en el diseño.
 */
object SaText {
    /** "ALARMA DE / INCENDIO": mayúscula de 11,4 dp y 14,9 dp entre líneas. */
    val alertTitle = TextStyle(fontFamily = SaFonts.Black, fontSize = 15.8.sp, lineHeight = 14.9.sp, textAlign = TextAlign.Center)

    /** "Escuchando", "Sonido detectado", "Universidad". */
    val title = TextStyle(fontFamily = SaFonts.Bold, fontSize = 13.8.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)

    /** "Contexto", "Calle" junto a su icono. */
    val screenTitle = TextStyle(fontFamily = SaFonts.Heading, fontSize = 9.9.sp, lineHeight = 11.sp)

    /** Filas de contexto. */
    val row = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 6.9.sp, lineHeight = 8.sp)

    /** Nombre dentro de una celda del panal. */
    val cell = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 5.sp, lineHeight = 5.6.sp, textAlign = TextAlign.Center)

    /** Subtítulos ("Timbre · Alarma · Bebé", "Calle · ahora"). */
    val body = TextStyle(fontFamily = SaFonts.Regular, fontSize = 7.1.sp, lineHeight = 8.5.sp, textAlign = TextAlign.Center)

    /** Horario de una sesión ("08:30 – 12:10 · 3 h 40"). */
    val bodyStrong = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 6.8.sp, lineHeight = 8.sp, textAlign = TextAlign.Center)

    /** "Entendido". */
    val button = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 7.sp, lineHeight = 8.sp, textAlign = TextAlign.Center)

    /** Hora ("10:24"). */
    val clock = TextStyle(fontFamily = SaFonts.SemiBold, fontSize = 7.2.sp, lineHeight = 8.5.sp)

    /** "PELIGRO", "ATENCIÓN", "AVISO". */
    val label = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 5.8.sp, lineHeight = 7.sp, letterSpacing = 0.12.em)

    /** "IA · ANALIZANDO", chip "CASA", píldora "94%", "SESIÓN 3 DE 4". */
    val labelSmall = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 5.5.sp, lineHeight = 6.5.sp, letterSpacing = 0.02.em)

    /** Etiquetas junto a un título ("MANUAL", "5 DE 7"), números de la esfera y horas del detalle. */
    val tag = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Medium, fontSize = 5.2.sp, lineHeight = 6.sp, letterSpacing = 0.08.em)

    /** Pie del panal ("TOCA PARA ACTIVAR") y "GIRA LA CORONA". */
    val footer = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 4.6.sp, lineHeight = 5.5.sp, letterSpacing = 0.2.em)

    /** Chip negro de las alertas ("SIGUE SONANDO"). */
    val chip = TextStyle(fontFamily = SaFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 5.sp, lineHeight = 6.sp, letterSpacing = 0.1.em)
}

@Composable
fun SoundAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
