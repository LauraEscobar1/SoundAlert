package com.soundalert.wear.ui.context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.glass
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText

/**
 * F · Contexto: los contextos activos (CASA, CALLE, OTRO). La corona mueve el vidrio
 * de fila en fila y un toque aplica ese contexto (ContextManager + ContextStore); el
 * siguiente sonido ya lo usa, sin reiniciar el micrófono ni YAMNet. El check marca el
 * contexto actual.
 */
@Composable
fun ContextScreen(onSelected: () -> Unit) {
    val current by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    ContextContent(
        options = SoundAlertContext.ACTIVE,
        current = current,
        onSelect = {
            SoundAlertRuntime.contextManager.set(it)
            onSelected()
        },
    )
}

@Composable
fun ContextContent(options: List<SoundAlertContext>, current: SoundAlertContext, onSelect: (SoundAlertContext) -> Unit) {
    var cursor by rememberSaveable { mutableIntStateOf(options.indexOf(current).coerceAtLeast(0)) }
    WatchScreen(Modifier.rotarySteps { cursor = (cursor + it).coerceIn(0, options.lastIndex) }) { scale ->
        with(scale) {
            Row(Modifier.align(Alignment.TopCenter).padding(top = 19.mdp), verticalAlignment = Alignment.CenterVertically) {
                Text("Contexto", style = SaText.screenTitle, color = SaColors.Ink, maxLines = 1)
                Spacer(Modifier.width(5.mdp))
                Text(CONTEXT_SOURCE_TAG, style = SaText.tag, color = SaColors.Brown, maxLines = 1)
            }
            // Filas con zona táctil de 46 (fila visible de 36 + margen): 138 en total, centradas en y≈106.
            Column(Modifier.align(Alignment.Center).offset(y = 10.mdp)) {
                options.forEachIndexed { index, option ->
                    ContextRow(
                        option,
                        highlighted = index == cursor,
                        isCurrent = option == current,
                        onClick = {
                            cursor = index
                            onSelect(option)
                        },
                        scale = scale,
                    )
                }
            }
        }
    }
}

/** Fila resaltada: vidrio con texto e icono en tinta. Resto: café al 10 % con texto café. */
@Composable
private fun ContextRow(option: SoundAlertContext, highlighted: Boolean, isCurrent: Boolean, onClick: () -> Unit, scale: WatchScale) = with(scale) {
    val content = if (highlighted) SaColors.Ink else SaColors.Brown
    Box(
        Modifier
            .size(width = 120.mdp, height = 46.mdp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isCurrent }
            .padding(vertical = 5.mdp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .fillMaxSize()
                .then(if (highlighted) Modifier.glass(shape) else Modifier.clip(shape).background(SaColors.BrownFaint))
                .padding(start = 12.mdp, end = 11.mdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(option.icon, glyphHeight = 10.mdp, maxGlyphWidth = 11.mdp, tint = content)
            Spacer(Modifier.width(8.mdp))
            FitText(option.label, SaText.row, content, Modifier.weight(1f))
            if (isCurrent) GlyphIcon(R.drawable.ic_check, glyphHeight = 7.5.mdp, tint = content, contentDescription = "Actual")
        }
    }
}

/**
 * De dónde sale el contexto: hoy lo elige la persona (ContextManager). Pasará a "AUTO"
 * cuando exista un proveedor por ubicación y movimiento (ActiveContextProvider).
 */
private const val CONTEXT_SOURCE_TAG = "MANUAL"
