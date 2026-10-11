package com.soundalert.wear.ui.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.ListFootnote
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.ListRow
import com.soundalert.wear.ui.components.WatchList
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.SoundRow
import com.soundalert.wear.ui.state.soundRows
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText

/**
 * E · Sonidos del contexto activo, en MODO SOLO LECTURA.
 *
 * Muestra las reglas reales del reloj (RuleEngine) para el contexto actual: el punto
 * es la prioridad y el interruptor indica si ese sonido alerta aquí. No se puede
 * cambiar desde el reloj: el motor de reglas no aplica todavía reglas personalizadas
 * (ni las del backend), así que un interruptor editable no tendría efecto real.
 */
@Composable
fun SoundsScreen() {
    val context by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val rows = remember(context) { soundRows(context, SoundAlertRuntime.ruleEngine) }
    WatchList(title = "Sonidos en ${context.label.lowercase()}", titleIcon = context.icon) { spec ->
        items(rows) { row ->
            ListRow(
                spec = spec,
                container = if (row.enabled) SaColors.Surface else SaColors.SurfaceDim,
                start = 9.1.dp,
                end = 6.3.dp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = describe(row, context) },
            ) {
                val dim = if (row.enabled) 1f else 0.55f
                // Mockup: punto de 5,9, texto a 6,3 del punto e interruptor de 22,2 × 13,6.
                Box(Modifier.size(5.9.dp).alpha(dim).clip(CircleShape).background(row.priority?.color ?: SaColors.ToggleOff))
                Spacer(Modifier.width(6.3.dp))
                FitText(
                    SoundUi.shortName(row.category),
                    SaText.row,
                    if (row.enabled) SaColors.OnSurface else SaColors.Muted,
                    Modifier.weight(1f),
                    maxLines = 2,
                )
                Spacer(Modifier.width(4.dp))
                if (row.recordOnly) {
                    Text("REGISTRO", style = SaText.labelSmall, color = SaColors.Muted, maxLines = 1)
                } else {
                    ToggleIndicator(on = row.enabled)
                }
            }
        }
        item { ListFootnote(spec, "Solo lectura: reglas del reloj para ${context.label}. Peligro siempre activo.") }
    }
}

/** Interruptor solo visual (no editable): blanco con el punto a la derecha si alerta. */
@Composable
private fun ToggleIndicator(on: Boolean) {
    Box(
        Modifier.size(width = 22.2.dp, height = 13.6.dp).clip(RoundedCornerShape(50)).background(if (on) SaColors.OnSurface else SaColors.Inactive).padding(1.6.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(10.4.dp).clip(CircleShape).background(if (on) SaColors.Background else SaColors.ToggleOff))
    }
}

private fun describe(row: SoundRow, context: SoundAlertContext): String {
    val name = SoundUi.name(row.category)
    return when {
        row.recordOnly -> "$name: solo registro, nunca alerta"
        row.priority != null -> "$name: alerta en ${context.label}, prioridad ${row.priority.label.lowercase()}"
        else -> "$name: no alerta en ${context.label}"
    }
}
