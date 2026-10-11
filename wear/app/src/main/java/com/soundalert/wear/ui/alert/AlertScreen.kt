package com.soundalert.wear.ui.alert

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.components.PriorityHeader
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.relativeTime
import com.soundalert.wear.ui.theme.SaColors
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

/*
 * Las tres alertas con las medidas de los mockups: cabecera de prioridad, icono centrado
 * en el hueco libre (lo único que encoge si falta sitio, p. ej. con la letra grande) y
 * textos con el ancho del diseño. Un titular largo pasa a otra línea antes de reducirse.
 */

@Composable
private fun BoxScope.Danger(alert: Alert, onAcknowledge: () -> Unit, scale: WatchScale) = with(scale) {
    // Mockup: barras en y≈19, icono de ~53 × 40 (y≈70), titular en dos líneas de 19 sp
    // (y≈112–142) y píldora "Entendido" de 84,6 × 26,3 con centro en y≈164.
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 16.6.mdp, bottom = 14.7.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PriorityHeader(alert.priority, onColor = Color.Black, inactive = Color.Black, scale = scale)
        AlertGlyph(alert, glyphHeight = 39.mdp, maxGlyphWidth = 53.mdp, tint = Color.Black)
        FitText(SoundUi.alertTitle(alert.category).uppercase(), SaText.alertTitle, Color.Black, Modifier.width(140.mdp), maxLines = 3)
        Spacer(Modifier.height(5.mdp))
        Box(
            modifier = Modifier
                .size(width = 84.6.mdp, height = 26.3.mdp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black)
                .clickable(role = Role.Button, onClick = onAcknowledge),
            contentAlignment = Alignment.Center,
        ) {
            Text("Entendido", style = SaText.button, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun BoxScope.Attention(alert: Alert, scale: WatchScale) = with(scale) {
    val now by nowEverySecond()
    // Anillo ámbar pegado al borde de la pantalla: 5 de grosor (mockup: radio 91,4–96,4).
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 5.mdp.toPx()
        drawCircle(color = SaColors.Attention, radius = size.minDimension / 2 - stroke / 2, style = Stroke(width = stroke))
    }
    // Mockup: círculo ámbar de 64,7 (centro y≈78), titular en dos líneas (y≈124–152) y
    // "Calle · ahora" en y≈163, todo dentro del anillo.
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 16.7.mdp, bottom = 24.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PriorityHeader(alert.priority, onColor = SaColors.Attention, inactive = SaColors.Inactive, scale = scale)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val circle = minOf(64.7.mdp, maxHeight)
            Box(Modifier.size(circle).clip(CircleShape).background(SaColors.Attention), contentAlignment = Alignment.Center) {
                GlyphIcon(SoundUi.icon(alert.category), glyphHeight = circle * 0.43f, maxGlyphWidth = circle * 0.5f, tint = Color.Black)
            }
        }
        FitText(SoundUi.alertTitle(alert.category), SaText.title, SaColors.OnSurface, Modifier.width(100.mdp), maxLines = 3)
        Spacer(Modifier.height(4.mdp))
        FitText("${alert.context.label} · ${relativeTime(alert.createdAtMs, now)}", SaText.body, SaColors.Secondary, Modifier.width(96.mdp))
    }
}

@Composable
private fun BoxScope.Information(alert: Alert, config: AlertConfig, scale: WatchScale) = with(scale) {
    val now by nowEverySecond()
    // Tiempo que queda hasta que el AlertManager la cierre (informationTimeoutMs).
    val remaining by produceState(1f, alert.id) {
        while (true) {
            val elapsed = System.currentTimeMillis() - alert.createdAtMs
            value = (1f - elapsed.toFloat() / config.informationTimeoutMs).coerceIn(0f, 1f)
            delay(50)
        }
    }
    // Mockup: icono de ~34 × 36 (y≈84), "Timbre" en y≈141, subtítulo en y≈159 y barra
    // de 48 × 2,3 en y≈171.
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 16.6.mdp, bottom = 19.7.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PriorityHeader(alert.priority, onColor = SaColors.Information, inactive = SaColors.Inactive, scale = scale)
        AlertGlyph(alert, glyphHeight = 36.2.mdp, maxGlyphWidth = 40.mdp, tint = SaColors.Information)
        FitText(SoundUi.alertTitle(alert.category), SaText.title, SaColors.OnSurface, Modifier.width(110.mdp), maxLines = 3)
        Spacer(Modifier.height(2.5.mdp))
        FitText("${alert.context.label} · ${relativeTime(alert.createdAtMs, now)}", SaText.body, SaColors.Secondary, Modifier.width(116.mdp))
        Spacer(Modifier.height(7.6.mdp))
        Box(Modifier.size(width = 48.mdp, height = 2.3.mdp).clip(RoundedCornerShape(50)).background(SaColors.Track)) {
            Box(Modifier.width(48.mdp * remaining).height(2.3.mdp).background(SaColors.Information))
        }
    }
}

/** Icono de la alerta centrado en el hueco libre; encoge solo si los textos necesitan el sitio. */
@Composable
private fun ColumnScope.AlertGlyph(alert: Alert, glyphHeight: Dp, maxGlyphWidth: Dp, tint: Color) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fit = (maxHeight / glyphHeight).coerceAtMost(1f)
        GlyphIcon(SoundUi.icon(alert.category), glyphHeight = glyphHeight * fit, maxGlyphWidth = maxGlyphWidth * fit, tint = tint)
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
