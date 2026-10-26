package com.soundalert.wear.ui.context

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.components.Clock
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.components.glass
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * F · Contexto: los contextos activos (CASA, CALLE, OTRO) en una lista fluida y sin
 * recuadros (icono y nombre en vino, centrados). Solo el activo lleva una píldora de
 * vidrio con check, que se desliza hasta la nueva fila al cambiar. La lista se desplaza
 * con el dedo o con la corona, con inercia, y las filas cerca de los bordes se achican y
 * se desvanecen. Un toque aplica el contexto (ContextManager + ContextStore) sin reiniciar
 * el micrófono ni YAMNet, y se vuelve atrás cuando la píldora termina de llegar.
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
                delay(PILL_SETTLE_MS)
                onSelected()
            }
        },
    )
}

@Composable
fun ContextContent(options: List<SoundAlertContext>, current: SoundAlertContext, onSelect: (SoundAlertContext) -> Unit) {
    val scroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val selected = options.indexOf(current).coerceAtLeast(0)
    // Posición de la píldora (en filas): se desliza con un resorte sin rebote brusco.
    val pillRow by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "píldora")
    WatchScreen { scale ->
        with(scale) {
            val unit = with(LocalDensity.current) { 1.mdp.toPx() }
            // Centro de una fila en pantalla (dp sobre 192), ya desplazada.
            fun rowCenter(row: Float) = LIST_TOP + ROW_PITCH * row + ROW_PITCH / 2 - scroll.value / unit
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(scroll), focusRequester),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(LIST_TOP.mdp))
                    // Píldora de vidrio con el check, detrás de las filas.
                    Box(
                        Modifier
                            .height(0.dp)
                            .graphicsLayer {
                                val (lens, alpha) = edgeLens(rowCenter(pillRow))
                                // La caja mide 0 de alto y la píldora queda centrada en ella: se lleva al centro de su fila.
                                translationY = (ROW_PITCH * pillRow + ROW_PITCH / 2) * unit
                                scaleX = lens
                                scaleY = lens
                                this.alpha = alpha
                            },
                    ) {
                        Box(
                            Modifier.requiredSize(width = PILL_WIDTH.mdp, height = PILL_HEIGHT.mdp).glass(RoundedCornerShape(50)).padding(end = 10.mdp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            GlyphIcon(R.drawable.ic_check, glyphHeight = 5.mdp, tint = SaColors.Wine, contentDescription = null)
                        }
                    }
                    options.forEachIndexed { index, option ->
                        ContextRow(
                            option,
                            isCurrent = option == current,
                            onClick = { onSelect(option) },
                            scale = scale,
                            modifier = Modifier.graphicsLayer {
                                val (lens, alpha) = edgeLens(rowCenter(index.toFloat()))
                                scaleX = lens
                                scaleY = lens
                                this.alpha = alpha
                            },
                        )
                    }
                    Spacer(Modifier.height(LIST_BOTTOM.mdp))
                }
            }
            // Encabezado fijo: la hora y "Contexto", sobre un fondo que tapa las filas que pasan por debajo.
            Box(Modifier.fillMaxWidth().height(40.mdp).background(Brush.verticalGradient(0.75f to SaColors.Background, 1f to SaColors.Background.copy(alpha = 0f))))
            Clock(Modifier.centerAt(21.mdp), color = SaColors.Wine)
            Text("Contexto", style = SaText.screenTitle, color = SaColors.Wine, maxLines = 1, modifier = Modifier.centerAt(32.mdp))
        }
    }
}

/** Fila sin fondo: icono y nombre en vino, centrados. La zona táctil es la fila entera (34 de alto). */
@Composable
private fun ContextRow(option: SoundAlertContext, isCurrent: Boolean, onClick: () -> Unit, scale: WatchScale, modifier: Modifier) = with(scale) {
    Row(
        modifier
            .size(width = PILL_WIDTH.mdp, height = ROW_PITCH.mdp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isCurrent },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(option.icon, glyphHeight = 7.5.mdp, maxGlyphWidth = 8.5.mdp, tint = SaColors.Wine)
        Spacer(Modifier.width(6.mdp))
        Text(option.label, style = SaText.row.copy(fontSize = 8.6.sp, fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold), color = SaColors.Wine, maxLines = 1)
    }
}

/**
 * Las filas cerca de los bordes se achican y se desvanecen, como en las listas de Wear OS:
 * a tamaño completo en la franja central y, hacia arriba y abajo, hasta el 75 % y casi
 * transparentes en el margen de seguridad.
 */
internal fun edgeLens(y: Float): Pair<Float, Float> {
    val distance = abs(y - 100f)
    val t = ((distance - 38f) / 40f).coerceIn(0f, 1f)
    val eased = t * t * (3 - 2 * t)
    return (1f - 0.25f * eased) to (1f - 0.85f * eased)
}

/** Lista sobre 192: empieza en y = 45; filas de 34 (la fila visible más la separación). */
private const val LIST_TOP = 45f
private const val LIST_BOTTOM = 70f
private const val ROW_PITCH = 34f
private const val PILL_WIDTH = 124f
private const val PILL_HEIGHT = 30f

/** Tiempo para que la píldora llegue a su fila antes de volver atrás. */
private const val PILL_SETTLE_MS = 450L
