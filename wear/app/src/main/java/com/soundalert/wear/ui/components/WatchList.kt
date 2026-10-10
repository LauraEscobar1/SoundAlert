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
import androidx.compose.foundation.layout.height
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
            item { Clock() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    if (titleIcon != null) {
                        Icon(painterResource(titleIcon), contentDescription = null, tint = SaColors.OnSurface, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(title, style = SaText.screenTitle, color = SaColors.OnSurface, maxLines = 1)
                    if (tag != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(tag, style = SaText.label.copy(fontSize = SaText.label.fontSize * 0.8f), color = SaColors.Muted)
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
            .height(height)
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .paint(transformation.createContainerPainter(ColorPainter(container), shape, null))
            .clip(shape)
            .then(clickable)
            .padding(horizontal = 12.dp)
            .graphicsLayer { with(transformation) { applyContentTransformation() } },
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Nota pequeña al pie de una lista (estado de la sincronización, solo lectura…). */
@Composable
fun ListFootnote(text: String, color: Color = SaColors.Muted) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = SaText.body.copy(fontSize = SaText.body.fontSize * 0.92f), color = color, textAlign = TextAlign.Center)
    }
}
