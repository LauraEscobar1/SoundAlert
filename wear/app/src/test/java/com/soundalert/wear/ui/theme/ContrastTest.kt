package com.soundalert.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Todos los textos de la interfaz deben dar al menos 4,5:1 (WCAG AA) sobre su fondo. */
class ContrastTest {

    private fun channel(c: Float) = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color) = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(text: Color, background: Color): Double {
        val (light, dark) = listOf(luminance(text), luminance(background)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color) {
        val ratio = contrast(text, background)
        assertTrue("$name: %.2f:1".format(ratio), ratio >= 4.5)
    }

    @Test
    fun `paleta rosa Cherry Marshmallow`() {
        assertEquals(Color(0xFFEAADC1), SaColors.Background)
        assertEquals(Color(0xFF5F0C2F), SaColors.Wine)
        assertEquals(Color(0xFFA3245C), SaColors.Accent)
        assertEquals(SaColors.Accent.copy(alpha = 0.25f), SaColors.Glass)
    }

    @Test
    fun `textos en vino sobre el rosa, el vidrio y lo tenue`() {
        assertReadable("vino / rosa", SaColors.Wine, SaColors.Background)
        assertReadable("vino / vidrio", SaColors.Wine, SaColors.Glass.compositeOver(SaColors.Background))
        assertReadable("vino / tenue", SaColors.Wine, SaColors.WineFaint.compositeOver(SaColors.Background))
        assertReadable("rosa / chip de vino", SaColors.Background, SaColors.Wine)
    }

    @Test
    fun `textos en tinta sobre los colores de alerta`() {
        for (priority in Priority.entries) assertReadable("tinta / ${priority.name}", SaColors.Ink, priority.color)
        assertEquals(4.69, contrast(SaColors.Ink, SaColors.Danger), 0.01)
    }

    @Test
    fun `modo ambiente`() {
        assertReadable("ambiente", SaColors.AmbientContent, SaColors.AmbientBackground)
        assertReadable("ambiente tenue", SaColors.AmbientDim, SaColors.AmbientBackground)
    }
}
