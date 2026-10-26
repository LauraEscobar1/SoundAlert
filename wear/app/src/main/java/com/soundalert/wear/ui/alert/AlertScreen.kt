package com.soundalert.wear.ui.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
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
import com.soundalert.wear.ui.components.PriorityBars
import com.soundalert.wear.ui.components.centerAt
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
    val layout = AlertLayout.of(alert.priority)
    WatchScreen(touch, background = alert.priority.color) { scale ->
        with(scale) {
            // Borde: anillo negro completo y otro punteado (peligro) o cuenta atrás (atención y aviso).
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 192f
                if (remaining == null) {
                    drawCircle(SaColors.Ink, radius = 91.3f * u, style = Stroke(2.9f * u))
                    drawCircle(
                        SaColors.Ink.copy(alpha = 0.3f),
                        radius = 85.3f * u,
                        style = Stroke(0.8f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * u, 2.4f * u))),
                    )
                } else {
                    val r = 91.6f * u
                    drawCircle(SaColors.Ink.copy(alpha = 0.15f), radius = r, style = Stroke(3.2f * u))
                    drawArc(
                        SaColors.Ink,
                        startAngle = -90f,
                        sweepAngle = 360f * remaining.value,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(3.2f * u, cap = StrokeCap.Round),
                    )
                }
            }
            // Arriba, a su altura: barras, etiqueta y disco con sus ondas o ecos.
            PriorityBars(alert.priority, scale, Modifier.centerAt(21.mdp))
            Text(alert.priority.label, style = SaText.label, color = SaColors.Ink, maxLines = 1, modifier = Modifier.centerAt(29.5.mdp))
            IconDisc(alert, layout, scale)
            // Debajo, cada cosa en su línea y centradas, repartidas en el alto que queda:
            // titular, contexto y hora, chip "SIGUE SONANDO" y botón (peligro) o barrita.
            Column(
                Modifier.fillMaxSize().padding(top = layout.textTop.mdp, bottom = 23.mdp),
                verticalArrangement = Arrangement.spacedBy(2.6.mdp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AlertTitle(alert, scale)
                ContextLine(alert, nowMs, scale)
                if (stillSounding) InkChip("SIGUE SONANDO", scale)
                if (remaining == null) {
                    AcknowledgeButton(onAcknowledge, scale, Modifier.padding(top = 1.mdp))
                } else {
                    // Barrita de 41 × 2,6 sincronizada con el anillo.
                    Box(
                        Modifier.padding(top = 2.mdp).size(width = 41.mdp, height = 2.6.mdp).drawBehind {
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
 * Disco de cada nivel (sobre 192): un 30–35 % mayor que en el mockup y lo más grande que
 * deja el resto sin salir del margen de seguridad. [textTop]: donde empieza el bloque de textos.
 */
private class AlertLayout(val discY: Float, val disc: Float, val textTop: Float) {
    companion object {
        fun of(priority: Priority) = when (priority) {
            Priority.DANGER -> AlertLayout(discY = 61.5f, disc = 56f, textTop = 92.5f)
            Priority.ATTENTION -> AlertLayout(discY = 62.5f, disc = 58f, textTop = 95.5f)
            Priority.INFORMATION -> AlertLayout(discY = 67f, disc = 52f, textTop = 102f)
        }
    }
}

/**
 * Disco de tinta con el icono en el color del nivel. Peligro: 3 ondas a cada lado;
 * atención: 2; aviso: un anillo punteado y otro tenue alrededor (ecos).
 */
@Composable
private fun BoxScope.IconDisc(alert: Alert, layout: AlertLayout, scale: WatchScale) = with(scale) {
    Canvas(Modifier.fillMaxSize()) {
        val u = size.minDimension / 192f
        val c = Offset(center.x, layout.discY * u)
        val r = layout.disc / 2 * u
        when (alert.priority) {
            Priority.INFORMATION -> {
                drawCircle(
                    SaColors.Ink.copy(alpha = 0.3f),
                    radius = r + 3.8f * u,
                    center = c,
                    style = Stroke(1.3f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.6f * u, 1.8f * u))),
                )
                drawCircle(SaColors.Ink.copy(alpha = 0.15f), radius = r + 7f * u, center = c, style = Stroke(1.3f * u))
            }
            else -> drawWaves(c, r, waves = if (alert.priority == Priority.DANGER) 3 else 2, u)
        }
    }
    Box(
        Modifier.centerAt(layout.discY.mdp).size(layout.disc.mdp).clip(CircleShape).background(SaColors.Ink),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            SoundUi.icon(alert.category),
            glyphHeight = (layout.disc * 0.36f).mdp,
            maxGlyphWidth = (layout.disc * 0.42f).mdp,
            tint = alert.priority.color,
            contentDescription = SoundUi.name(alert.category),
        )
    }
}

/** Ondas ")))" a cada lado del disco: la primera en tinta y las siguientes más tenues. */
private fun DrawScope.drawWaves(c: Offset, discRadius: Float, waves: Int, u: Float) {
    for (k in 1..waves) {
        val r = discRadius + (6.1f + (k - 1) * 6f) * u
        val half = 17f + (k - 1) * 2.5f
        val alpha = when (k) {
            1 -> 1f
            2 -> if (waves == 2) 0.45f else 0.5f
            else -> 0.32f
        }
        val topLeft = Offset(c.x - r, c.y - r)
        for (mid in listOf(0f, 180f)) {
            drawArc(SaColors.Ink.copy(alpha = alpha), mid - half, half * 2, useCenter = false, topLeft = topLeft, size = Size(r * 2, r * 2), style = Stroke(1.8f * u, cap = StrokeCap.Round))
        }
    }
}

/** Titular en mayúsculas: más grande si cabe en una línea; si no, en dos. */
@Composable
private fun AlertTitle(alert: Alert, scale: WatchScale) = with(scale) {
    val text = SoundUi.alertTitle(alert.category).uppercase()
    val width = 140.mdp
    val measurer = rememberTextMeasurer()
    val oneLine = with(LocalDensity.current) {
        measurer.measure(text, TITLE_ONE_LINE, maxLines = 1, softWrap = false).size.width <= width.roundToPx()
    }
    FitText(text, if (oneLine) TITLE_ONE_LINE else TITLE_TWO_LINES, SaColors.Ink, Modifier.width(if (oneLine) width else 112.mdp), maxLines = 2)
}

private val TITLE_ONE_LINE = SaText.alertTitle.copy(fontSize = 19.sp, lineHeight = 18.sp)
private val TITLE_TWO_LINES = SaText.alertTitle.copy(fontSize = 16.sp, lineHeight = 15.sp)

/** "Casa · ahora"; aviso: icono del contexto + "Casa · hace 2 s". */
@Composable
private fun ContextLine(alert: Alert, nowMs: Long, scale: WatchScale) = with(scale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val text = if (alert.priority == Priority.INFORMATION) {
            GlyphIcon(alert.context.icon, glyphHeight = 5.mdp, tint = SaColors.Ink)
            Spacer(Modifier.width(2.6.mdp))
            "${alert.context.label} · ${relativeTimeSeconds(alert.createdAtMs, nowMs)}"
        } else {
            "${alert.context.label} · ${relativeTime(alert.createdAtMs, nowMs)}"
        }
        FitText(text, SaText.body.copy(fontSize = 7.6.sp), SaColors.Ink, Modifier.widthIn(max = 110.mdp))
    }
}

/** Chip negro con texto blanco ("SIGUE SONANDO"), 9 de alto. */
@Composable
private fun InkChip(text: String, scale: WatchScale) = with(scale) {
    Box(Modifier.height(9.1.mdp).clip(RoundedCornerShape(50)).background(SaColors.Ink).padding(horizontal = 4.4.mdp), contentAlignment = Alignment.Center) {
        Text(text, style = SaText.chip, color = Color.White, maxLines = 1)
    }
}

/** Botón negro "Entendido" de 64 × 19,5 visibles; Compose amplía la zona táctil a 48 dp. */
@Composable
private fun AcknowledgeButton(onAcknowledge: () -> Unit, scale: WatchScale, modifier: Modifier) = with(scale) {
    Box(
        modifier
            .size(width = 63.6.mdp, height = 19.5.mdp)
            .clip(RoundedCornerShape(50))
            .background(SaColors.Ink)
            .clickable(role = Role.Button, onClick = onAcknowledge),
        contentAlignment = Alignment.Center,
    ) {
        Text("Entendido", style = SaText.button, color = Color.White, maxLines = 1)
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
