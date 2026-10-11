package com.soundalert.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText

/**
 * Lista de las pantallas D, E y F: hora, título y filas que se estrechan hacia los
 * bordes de la esfera (TransformingLazyColumn). Se desplaza con la corona.
 *
 * Medidas del mockup (sobre 192): hora gris en y≈18,5, título en y≈31,5, filas de 132,6
 * de ancho (29,8 de margen a cada lado) que empiezan en y≈43, de 27 de alto y 2,7 de
 * separación. Los márgenes escalan con la esfera; filas y letra no.
 */
@Composable
fun WatchList(
    title: String,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes titleIcon: Int? = null,
    tag: String? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val u = LocalConfiguration.current.screenWidthDp / MOCKUP_DIAMETER_DP
    ScreenScaffold(scrollState = state, modifier = modifier.background(SaColors.Background)) { scaffoldPadding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = PaddingValues(
                start = (29.8f * u).dp,
                end = (29.8f * u).dp,
                top = (13.5f * u).dp,
                bottom = scaffoldPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.7.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hora y título sin transformación: en reposo conservan el tamaño y la posición del
            // mockup (la transformación de borde los encogía un 10 %).
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Clock(style = SaText.clockSmall, color = SaColors.Secondary)
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    if (titleIcon != null) {
                        GlyphIcon(titleIcon, glyphHeight = 10.dp, tint = SaColors.OnSurface)
                        Spacer(Modifier.width(6.7.dp))
                    }
                    FitText(title, SaText.screenTitle, SaColors.OnSurface, Modifier.weight(1f, fill = false))
                    if (tag != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(tag, style = SaText.tag, color = SaColors.Muted, maxLines = 1)
                    }
                }
            }
            content(spec)
        }
    }
}

/**
 * Fila en forma de píldora con la transformación de los bordes de la esfera (27 de alto
 * en el mockup). [height] es la altura mínima: con la letra grande la fila crece en vez
 * de cortar el texto. Sin [onClick] es solo informativa (no se anuncia como botón).
 */
@Composable
fun TransformingLazyColumnItemScope.ListRow(
    spec: TransformationSpec,
    container: Color,
    modifier: Modifier = Modifier,
    height: Dp = ROW_HEIGHT,
    start: Dp = 10.dp,
    end: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val transformation = SurfaceTransformation(spec)
    val shape = RoundedCornerShape(50)
    val clickable = if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .heightIn(min = height)
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .paint(transformation.createContainerPainter(ColorPainter(container), shape, null))
            .clip(shape)
            .then(clickable)
            .padding(start = start, end = end, top = 2.dp, bottom = 2.dp)
            .graphicsLayer { with(transformation) { applyContentTransformation() } },
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Alto de las filas en el mockup. */
val ROW_HEIGHT = 27.dp

private const val MOCKUP_DIAMETER_DP = 192f

/** Nota pequeña al pie de una lista (estado de la sincronización, solo lectura…). */
@Composable
fun TransformingLazyColumnItemScope.ListFootnote(spec: TransformationSpec, text: String, color: Color = SaColors.Muted) {
    Box(Modifier.edgeTransformed(this, spec).padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = SaText.body.copy(fontSize = SaText.body.fontSize * 0.92f), color = color, textAlign = TextAlign.Center)
    }
}

/**
 * Elemento sin fondo (hora, título, notas) que, como las filas, se encoge y se
 * desvanece al llegar al borde de la esfera en lugar de quedar cortado.
 */
fun Modifier.edgeTransformed(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier {
    val transformation = with(scope) { SurfaceTransformation(spec) }
    return fillMaxWidth()
        .transformedHeight(scope, spec)
        .graphicsLayer { with(transformation) { applyContentTransformation() } }
}
