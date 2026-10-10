package com.soundalert.wear.ui.context

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.components.ListFootnote
import com.soundalert.wear.ui.components.ListRow
import com.soundalert.wear.ui.components.WatchList
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText

/**
 * D · Contexto. Solo los contextos activos (CASA, CALLE, OTRO). Es MANUAL: lo elige
 * la persona y se guarda en el reloj (ContextManager + ContextStore); el siguiente
 * sonido ya usa el nuevo contexto, sin reiniciar el micrófono ni YAMNet.
 */
@Composable
fun ContextScreen(onSelected: () -> Unit) {
    val current by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    WatchList(title = "Contexto", tag = "MANUAL") { spec ->
        items(SoundAlertContext.ACTIVE) { option ->
            val selected = option == current
            val content = if (selected) SaColors.Background else SaColors.OnSurface
            ListRow(
                spec = spec,
                container = if (selected) SaColors.OnSurface else SaColors.Surface,
                height = 38.dp,
                role = Role.RadioButton,
                modifier = Modifier.semantics { this.selected = selected },
                onClick = {
                    SoundAlertRuntime.contextManager.set(option)
                    onSelected()
                },
            ) {
                Icon(painterResource(option.icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(option.label, style = SaText.row, color = content, modifier = Modifier.weight(1f))
                if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
        item { ListFootnote("Lo eliges tú. Cada contexto vigila sus propios sonidos.") }
    }
}
