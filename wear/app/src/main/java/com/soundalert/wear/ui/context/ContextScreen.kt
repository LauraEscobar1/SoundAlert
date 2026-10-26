package com.soundalert.wear.ui.context

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.components.Clock
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F · Contexto: los contextos activos (CASA, CALLE, OTRO), todos visibles a la vez en un
 * bloque centrado: la hora pequeña, "Contexto" y una píldora rellena por contexto. El
 * activo se resalta (relleno más intenso, borde blanco y check) y el resaltado pasa con
 * una transición suave al cambiar. Un toque aplica el contexto (ContextManager +
 * ContextStore) sin reiniciar el micrófono ni YAMNet, y se vuelve atrás al terminar la
 * transición.
 */
@Composable
fun ContextScreen(onSelected: () -> Unit) {
    val current by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    ContextContent(
        options = SoundAlertContext.ACTIVE,
        current = current,
        onSelect = {
            SoundAlertRuntime.contextManager.set(it)
            scope.launch {
                delay(SETTLE_MS)
                onSelected()
            }
        },
    )
}

@Composable
fun ContextContent(options: List<SoundAlertContext>, current: SoundAlertContext, onSelect: (SoundAlertContext) -> Unit) {
    WatchScreen { scale ->
        with(scale) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Clock(style = SaText.clock.copy(fontSize = 6.6.sp), color = SaColors.Wine)
                Spacer(Modifier.height(1.mdp))
                Text("Contexto", style = SaText.screenTitle, color = SaColors.Wine, maxLines = 1)
                Spacer(Modifier.height(4.mdp))
                options.forEachIndexed { index, option ->
                    // La última fila, más cerca de la curva de la pantalla, es algo más angosta.
                    val width = if (index == options.lastIndex && options.size > 2) LAST_ROW_WIDTH else ROW_WIDTH
                    ContextRow(option, isCurrent = option == current, width = width, onClick = { onSelect(option) }, scale = scale)
                }
            }
        }
    }
}

/**
 * Píldora completamente redondeada de 30 de alto: relleno de vino al 10 %, icono pequeño
 * a la izquierda y nombre en vino en negrita. La activa: relleno al 18 %, borde fino
 * blanco al 45 % y check. La zona táctil es la fila más la separación (34 de alto).
 */
@Composable
private fun ContextRow(option: SoundAlertContext, isCurrent: Boolean, width: Float, onClick: () -> Unit, scale: WatchScale) = with(scale) {
    val fill by animateColorAsState(SaColors.Wine.copy(alpha = if (isCurrent) 0.18f else 0.10f), tween(TRANSITION_MS, easing = FastOutSlowInEasing), label = "relleno")
    val border by animateColorAsState(if (isCurrent) SaColors.GlassBorder else Color.Transparent, tween(TRANSITION_MS, easing = FastOutSlowInEasing), label = "borde")
    val check by animateFloatAsState(if (isCurrent) 1f else 0f, tween(TRANSITION_MS, easing = FastOutSlowInEasing), label = "check")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .size(width = width.mdp, height = (ROW_HEIGHT + ROW_GAP).mdp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isCurrent },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .size(width = width.mdp, height = ROW_HEIGHT.mdp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .padding(start = 12.mdp, end = 11.mdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(option.icon, glyphHeight = 6.5.mdp, maxGlyphWidth = 7.5.mdp, tint = SaColors.Wine)
            Spacer(Modifier.width(7.mdp))
            FitText(option.label, SaText.row.copy(fontSize = 8.4.sp, fontWeight = FontWeight.ExtraBold), SaColors.Wine, Modifier.weight(1f))
            GlyphIcon(R.drawable.ic_check, glyphHeight = 5.mdp, tint = SaColors.Wine, modifier = Modifier.alpha(check), contentDescription = if (isCurrent) "Actual" else null)
        }
    }
}

/**
 * Sobre 192: filas de 30 de alto con 4 de separación, del 70 % del ancho. La última (centro
 * en y≈142) mide el 59 %: así sus extremos redondeados no salen del margen de seguridad.
 */
private const val ROW_HEIGHT = 30f
private const val ROW_GAP = 4f
private const val ROW_WIDTH = 134f
private const val LAST_ROW_WIDTH = 114f

/** Duración del paso del resaltado; se vuelve atrás cuando termina. */
private const val TRANSITION_MS = 300
private const val SETTLE_MS = 380L
