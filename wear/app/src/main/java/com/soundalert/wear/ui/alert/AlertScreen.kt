package com.soundalert.wear.ui.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.alert.Alert
import com.soundalert.wear.config.AlertConfig
import com.soundalert.wear.pipeline.PipelineStatus
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.PriorityHeader
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.relativeTime
import com.soundalert.wear.ui.state.relativeTimeSeconds
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay

/**
 * C · D · E · Alertas a pantalla completa en el color de su nivel, con texto en tinta.
 * - PELIGRO (rojo): anillo negro completo y botón "Entendido". No se cierra sola: solo
 *   con la confirmación (AlertManager.acknowledge), y mientras tanto el AlertManager
 *   repite las 3 vibraciones largas.
 * - ATENCIÓN (ámbar): sin botón. Anillo de cuenta atrás de 10 s y barra sincronizada;
 *   el AlertManager la cierra a los 10 s.
 * - AVISO (azul): igual, con 5 s. Al cerrarse queda en el historial de la sesión.
 * En ATENCIÓN y AVISO un toque en cualquier parte también la cierra.
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
    val status by PipelineStatus.snapshot.collectAsStateWithLifecycle()
    val now by nowEverySecond()
    AlertContent(
        alert = alert,
        stillSounding = alert.category in status.activeCategories,
        nowMs = now,
        timeoutMs = when (alert.priority) {
            Priority.DANGER -> null
            Priority.ATTENTION -> config.attentionTimeoutMs
            Priority.INFORMATION -> config.informationTimeoutMs
        },
        onAcknowledge = onAcknowledge,
    )
}

/** Alerta sin estado propio (también la usan las previews). [timeoutMs] null: no se cierra sola. */
@Composable
fun AlertContent(alert: Alert, stillSounding: Boolean, nowMs: Long, timeoutMs: Long?, onAcknowledge: () -> Unit) {
    val description = "${alert.priority.label}: ${SoundUi.alertTitle(alert.category)}"
    val semantics = Modifier.semantics {
        liveRegion = LiveRegionMode.Assertive
        contentDescription = description
    }
    val touch = if (alert.priority == Priority.DANGER) semantics.blockTouches() else semantics.tapToAcknowledge(onAcknowledge)
    val remaining = remainingFraction(alert, timeoutMs)
    WatchScreen(touch, background = alert.priority.color) { scale ->
        with(scale) {
            // Borde: anillo negro completo (peligro) o cuenta atrás (atención y aviso).
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.5.mdp.toPx()
                val radius = size.minDimension / 2 - stroke / 2
                if (remaining == null) {
                    drawCircle(SaColors.Ink, radius = radius, style = Stroke(stroke))
                } else {
                    drawCircle(SaColors.Ink.copy(alpha = 0.18f), radius = radius, style = Stroke(stroke))
                    drawArc(
                        SaColors.Ink,
                        startAngle = -90f,
                        sweepAngle = 360f * remaining.value,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            // Medidas sobre 192: barras en y≈15, disco de 46 en el hueco, titular de dos
            // líneas de 19 sp, subtítulo, chip y, abajo, botón (peligro) o barra (resto).
            Column(
                Modifier.fillMaxSize().padding(top = 15.mdp, bottom = if (alert.priority == Priority.DANGER) 12.mdp else 22.mdp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PriorityHeader(alert.priority, scale)
                IconDisc(alert, scale)
                FitText(SoundUi.alertTitle(alert.category).uppercase(), SaText.alertTitle, SaColors.Ink, Modifier.width(136.mdp), maxLines = 2)
                Spacer(Modifier.height(2.mdp))
                Subtitle(alert, nowMs, scale)
                if (stillSounding) {
                    Spacer(Modifier.height(3.mdp))
                    InkChip("SIGUE SONANDO", scale)
                }
                if (remaining == null) {
                    Spacer(Modifier.height(4.mdp))
                    AcknowledgeButton(onAcknowledge, scale)
                } else {
                    Spacer(Modifier.height(5.mdp))
                    // Barra de 48 × 2,3 sincronizada con el anillo.
                    Box(
                        Modifier.size(width = 48.mdp, height = 2.3.mdp).drawBehind {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(SaColors.Ink.copy(alpha = 0.18f), cornerRadius = r)
                            drawRoundRect(SaColors.Ink, size = Size(size.width * remaining.value, size.height), cornerRadius = r)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Disco de tinta con el icono en el color del nivel. Peligro: 3 ondas a cada lado;
 * atención: 2; aviso: anillos de eco concéntricos. Encoge si los textos necesitan sitio.
 */
@Composable
private fun ColumnScope.IconDisc(alert: Alert, scale: WatchScale) = with(scale) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val disc = minOf(46.mdp, maxHeight - 4.mdp).coerceAtLeast(20.mdp)
        Canvas(Modifier.fillMaxSize()) {
            val r = disc.toPx() / 2
            when (alert.priority) {
                Priority.INFORMATION -> drawEchoes(r, 6.mdp.toPx())
                else -> drawWaves(r, waves = if (alert.priority == Priority.DANGER) 3 else 2, step = 5.5.mdp.toPx(), stroke = 1.8.mdp.toPx())
            }
        }
        Box(Modifier.size(disc).clip(CircleShape).background(SaColors.Ink), contentAlignment = Alignment.Center) {
            GlyphIcon(
                SoundUi.icon(alert.category),
                glyphHeight = disc * 0.46f,
                maxGlyphWidth = disc * 0.56f,
                tint = alert.priority.color,
                contentDescription = SoundUi.name(alert.category),
            )
        }
    }
}

/** Ondas de sonido ")))" a cada lado del disco. */
private fun DrawScope.drawWaves(discRadius: Float, waves: Int, step: Float, stroke: Float) {
    for (k in 1..waves) {
        val r = discRadius + step * k
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        val alpha = 1f - (k - 1) * 0.25f
        for (start in listOf(-32f, 148f)) {
            drawArc(SaColors.Ink.copy(alpha = alpha), start, 64f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Anillos de eco alrededor de la campana. */
private fun DrawScope.drawEchoes(discRadius: Float, step: Float) {
    drawCircle(SaColors.Ink.copy(alpha = 0.55f), radius = discRadius + step, style = Stroke(step * 0.3f))
    drawCircle(SaColors.Ink.copy(alpha = 0.25f), radius = discRadius + step * 2, style = Stroke(step * 0.25f))
}

/** "Casa · ahora" (peligro y atención); aviso: icono del contexto + "Casa · hace 2 s". */
@Composable
private fun Subtitle(alert: Alert, nowMs: Long, scale: WatchScale) = with(scale) {
    if (alert.priority == Priority.INFORMATION) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(alert.context.icon, glyphHeight = 7.mdp, tint = SaColors.Ink)
            Spacer(Modifier.width(3.mdp))
            FitText("${alert.context.label} · ${relativeTimeSeconds(alert.createdAtMs, nowMs)}", SaText.body, SaColors.Ink, Modifier.widthIn(max = 100.mdp))
        }
    } else {
        FitText("${alert.context.label} · ${relativeTime(alert.createdAtMs, nowMs)}", SaText.body, SaColors.Ink, Modifier.width(110.mdp))
    }
}

/** Chip negro con texto durazno ("SIGUE SONANDO"). */
@Composable
private fun InkChip(text: String, scale: WatchScale) = with(scale) {
    Text(
        text,
        style = SaText.labelSmall,
        color = SaColors.Peach,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(SaColors.Ink).padding(horizontal = 5.mdp, vertical = 2.mdp),
    )
}

/** Botón negro "Entendido": 86 × 28 visibles; Compose amplía la zona táctil a 48 dp. */
@Composable
private fun AcknowledgeButton(onAcknowledge: () -> Unit, scale: WatchScale) = with(scale) {
    Box(
        Modifier
            .size(width = 86.mdp, height = 28.mdp)
            .clip(RoundedCornerShape(50))
            .background(SaColors.Ink)
            .clickable(role = Role.Button, onClick = onAcknowledge),
        contentAlignment = Alignment.Center,
    ) {
        Text("Entendido", style = SaText.button, color = SaColors.Peach, maxLines = 1)
    }
}

/**
 * Fracción que queda hasta que el AlertManager cierre la alerta (1 → 0, lineal), o null
 * si no se cierra sola. El anillo y la barra la leen al dibujar.
 */
@Composable
private fun remainingFraction(alert: Alert, timeoutMs: Long?): Animatable<Float, *>? {
    if (timeoutMs == null) return null
    val progress = remember(alert.id) {
        val elapsed = System.currentTimeMillis() - alert.createdAtMs
        Animatable((1f - elapsed.toFloat() / timeoutMs).coerceIn(0f, 1f))
    }
    LaunchedEffect(alert.id) {
        val left = (progress.value * timeoutMs).toInt()
        if (left > 0) progress.animateTo(0f, tween(left, easing = LinearEasing))
    }
    return progress
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
