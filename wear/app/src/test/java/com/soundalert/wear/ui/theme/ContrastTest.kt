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

    private val glass = SaColors.Glass.compositeOver(SaColors.Peach)

    @Test
    fun `textos sobre el durazno, el vidrio y las filas tenues`() {
        assertReadable("tinta / durazno", SaColors.Ink, SaColors.Peach)
        assertReadable("café / durazno", SaColors.Brown, SaColors.Peach)
        assertReadable("tinta / vidrio", SaColors.Ink, glass)
        assertReadable("café / fila tenue", SaColors.Brown, SaColors.BrownFaint.compositeOver(SaColors.Peach))
        assertReadable("durazno / chip de tinta", SaColors.Peach, SaColors.Ink)
    }

    @Test
    fun `textos en tinta sobre los colores de alerta`() {
        for (priority in Priority.entries) assertReadable("tinta / ${priority.name}", SaColors.Ink, priority.color)
    }

    @Test
    fun `el rojo de peligro es F03A2E y da al menos 4,5 a 1 con la tinta`() {
        assertEquals(Color(0xFFF03A2E), SaColors.Danger)
        assertEquals(4.69, contrast(SaColors.Ink, SaColors.Danger), 0.01)
    }

    @Test
    fun `modo ambiente`() {
        assertReadable("ambiente", SaColors.AmbientContent, SaColors.AmbientBackground)
        assertReadable("ambiente tenue", SaColors.AmbientDim, SaColors.AmbientBackground)
    }
}
