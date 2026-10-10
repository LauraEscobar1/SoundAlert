package com.soundalert.wear.ui.alert

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.components.PriorityHeader
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.relativeTime
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaFonts
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay

/**
 * C · Alerta. "Cuanto más urgente, más pantalla ocupa el color":
 * - PELIGRO: pantalla roja. Solo se cierra con "Entendido" (AlertManager.acknowledge);
 *   mientras tanto el AlertManager repite la vibración cada 15 s.
 * - ATENCIÓN: anillo ámbar. Un toque la confirma; si no, el AlertManager la cierra a los 10 s.
 * - AVISO: fondo negro, icono azul y barra del tiempo que queda (5 s).
 * Los estados, tiempos y vibraciones son los del AlertManager: aquí solo se pintan.
 */
@Composable
fun AlertScreen(alert: Alert, config: AlertConfig, onAcknowledge: () -> Unit) {
    // Mientras haya una alerta en pantalla, la pantalla no se apaga.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val description = "${alert.priority.label}: ${SoundUi.alertTitle(alert.category)}"
    val semantics = Modifier.semantics {
        liveRegion = LiveRegionMode.Assertive
        contentDescription = description
    }
    when (alert.priority) {
        Priority.DANGER -> WatchScreen(semantics.blockTouches(), background = SaColors.Danger) { scale -> Danger(alert, onAcknowledge, scale) }
        Priority.ATTENTION -> WatchScreen(semantics.tapToAcknowledge(onAcknowledge)) { scale -> Attention(alert, scale) }
        Priority.INFORMATION -> WatchScreen(semantics.tapToAcknowledge(onAcknowledge)) { scale -> Information(alert, config, scale) }
    }
}

@Composable
private fun BoxScope.Danger(alert: Alert, onAcknowledge: () -> Unit, scale: WatchScale) = with(scale) {
    PriorityHeader(alert.priority, onColor = Color.Black, inactive = Color.Black, scale = scale, modifier = Modifier.align(Alignment.TopCenter).offset(y = 15.mdp))
    Icon(
        painterResource(SoundUi.icon(alert.category)),
        contentDescription = null,
        tint = Color.Black,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 44.mdp).size(54.mdp),
    )
    Text(
        SoundUi.alertTitle(alert.category).uppercase(),
        style = SaText.alertTitle,
        color = Color.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 101.mdp).fillMaxWidth().padding(horizontal = 18.mdp),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 150.mdp)
            .size(width = 84.mdp, height = 27.mdp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black)
            .clickable(role = Role.Button, onClick = onAcknowledge),
        contentAlignment = Alignment.Center,
    ) {
        Text("Entendido", style = SaText.row.copy(fontFamily = SaFonts.SemiBold), color = Color.White)
    }
}

@Composable
private fun BoxScope.Attention(alert: Alert, scale: WatchScale) = with(scale) {
    val now by nowEverySecond()
    // Anillo ámbar que sigue el borde de la pantalla.
    Canvas(Modifier.fillMaxSize().padding(5.mdp)) {
        val stroke = 4.mdp.toPx()
        drawCircle(color = SaColors.Attention, radius = size.minDimension / 2 - stroke / 2, style = Stroke(width = stroke))
    }
    PriorityHeader(alert.priority, onColor = SaColors.Attention, inactive = SaColors.Inactive, scale = scale, modifier = Modifier.align(Alignment.TopCenter).offset(y = 15.mdp))
    Box(
        Modifier.align(Alignment.TopCenter).offset(y = 46.mdp).size(64.mdp).clip(CircleShape).background(SaColors.Attention),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(SoundUi.icon(alert.category)), contentDescription = null, tint = Color.Black, modifier = Modifier.size(34.mdp))
    }
    Column(
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 117.mdp).fillMaxWidth().padding(horizontal = 26.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(SoundUi.alertTitle(alert.category), style = SaText.title, color = SaColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${alert.context.label} · ${relativeTime(alert.createdAtMs, now)}", style = SaText.body, color = SaColors.Secondary, maxLines = 1)
    }
}

@Composable
private fun BoxScope.Information(alert: Alert, config: AlertConfig, scale: WatchScale) = with(scale) {
    val now by nowEverySecond()
    PriorityHeader(alert.priority, onColor = SaColors.Information, inactive = SaColors.Inactive, scale = scale, modifier = Modifier.align(Alignment.TopCenter).offset(y = 15.mdp))
    Icon(
        painterResource(SoundUi.icon(alert.category)),
        contentDescription = null,
        tint = SaColors.Information,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 63.mdp).size(42.mdp),
    )
    Column(
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 128.mdp).fillMaxWidth().padding(horizontal = 26.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(SoundUi.alertTitle(alert.category), style = SaText.title, color = SaColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${alert.context.label} · ${relativeTime(alert.createdAtMs, now)}", style = SaText.body, color = SaColors.Secondary, maxLines = 1)
    }
    // Tiempo que queda hasta que el AlertManager la cierre (informationTimeoutMs).
    val remaining by produceState(1f, alert.id) {
        while (true) {
            val elapsed = System.currentTimeMillis() - alert.createdAtMs
            value = (1f - elapsed.toFloat() / config.informationTimeoutMs).coerceIn(0f, 1f)
            delay(50)
        }
    }
    Box(Modifier.align(Alignment.TopCenter).offset(y = 169.mdp).size(width = 48.mdp, height = 2.mdp).clip(RoundedCornerShape(50)).background(SaColors.Track)) {
        Box(Modifier.width(48.mdp * remaining).height(2.mdp).background(SaColors.Information))
    }
}

@Composable
private fun nowEverySecond() = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1_000)
        value = System.currentTimeMillis()
    }
}

/** PELIGRO: los toques fuera de "Entendido" no hacen nada (no llegan a la pantalla de debajo). */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** ATENCIÓN y AVISO: un toque en cualquier parte confirma la alerta. */
private fun Modifier.tapToAcknowledge(onAcknowledge: () -> Unit): Modifier =
    clickable(onClickLabel = "Cerrar alerta", role = Role.Button, onClick = onAcknowledge)
