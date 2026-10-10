package com.soundalert.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
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
    ScreenScaffold(scrollState = state, modifier = modifier.background(SaColors.Background)) { contentPadding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Box(Modifier.edgeTransformed(this, spec), contentAlignment = Alignment.Center) { Clock() } }
            item {
                // Arriba la esfera es más estrecha: margen lateral y letra que se ajusta antes de cortarse.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.edgeTransformed(this, spec).padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                ) {
                    if (titleIcon != null) {
                        Icon(painterResource(titleIcon), contentDescription = null, tint = SaColors.OnSurface, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    FitText(title, SaText.screenTitle, SaColors.OnSurface, Modifier.weight(1f, fill = false), minSize = 13.sp)
                    if (tag != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(tag, style = SaText.label.copy(fontSize = SaText.label.fontSize * 0.8f), color = SaColors.Muted, maxLines = 1)
                    }
                }
            }
            content(spec)
        }
    }
}

/**
 * Fila en forma de píldora con la transformación de los bordes de la esfera.
 * Sin [onClick] es solo informativa (no se anuncia como botón).
 */
@Composable
fun TransformingLazyColumnItemScope.ListRow(
    spec: TransformationSpec,
    container: Color,
    height: Dp,
    modifier: Modifier = Modifier,
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
            // Altura mínima, no fija: con la letra grande la fila crece en vez de cortar el texto.
            .heightIn(min = height)
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .paint(transformation.createContainerPainter(ColorPainter(container), shape, null))
            .clip(shape)
            .then(clickable)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .graphicsLayer { with(transformation) { applyContentTransformation() } },
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

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
