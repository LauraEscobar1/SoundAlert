package com.soundalert.wear.ui.sessions

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.rules.Priority
import com.soundalert.wear.session.Session
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/**
 * H · Reloj de sesiones: esfera de 12 horas con las sesiones de las últimas 12 como
 * arcos en su franja horaria. La corona elige la sesión; un toque abre su detalle.
 */
@Composable
fun SessionClockScreen(visible: Boolean, onOpen: (String) -> Unit) {
    val all by SoundAlertRuntime.sessionTracker.sessions.collectAsStateWithLifecycle()
    val now by nowEvery(30_000)
    val sessions = visibleSessions(all, now)
    // Por defecto, la más reciente. Se recuerda por id para que una sesión nueva no la mueva.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = sessions.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: sessions.lastIndex
    SessionClockContent(
        sessions = sessions,
        selected = selected,
        nowMs = now,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onStep = { delta -> sessions.getOrNull((selected + delta).coerceIn(0, sessions.lastIndex))?.let { selectedId = it.id } },
        onOpen = onOpen,
        visible = visible,
    )
}

@Composable
fun SessionClockContent(
    sessions: List<Session>,
    selected: Int,
    nowMs: Long,
    is24Hour: Boolean,
    onStep: (Int) -> Unit,
    onOpen: (String) -> Unit,
    visible: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val session = sessions.getOrNull(selected)
    val open = if (session != null) {
        Modifier.clickable(onClickLabel = "Ver el detalle de la sesión", role = Role.Button) { onOpen(session.id) }
    } else {
        Modifier
    }
    WatchScreen(Modifier.rotarySteps(active = visible, onStep = onStep).then(open)) { scale ->
        with(scale) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 192f
                drawDialTicks(u)
                drawCircle(SaColors.BrownFaint, radius = TRACK_RADIUS * u, style = Stroke(10f * u))
                // De atrás hacia delante: el resto, la seleccionada y los eventos encima.
                sessions.forEachIndexed { i, s -> if (i != selected) drawSession(s, nowMs, zone, u, if (s.ongoing) SaColors.Rust else SaColors.Brown.copy(alpha = 0.4f), 10f) }
                session?.let { drawSession(it, nowMs, zone, u, SaColors.Orange, 15f) }
                sessions.forEach { s ->
                    s.events.forEach { e -> drawDot(polar(dialAngle(e.atMs, zone), TRACK_RADIUS * u), 3f * u, e.priority.color, 1.2f * u) }
                }
            }
            listOf("12" to 0f, "3" to 90f, "6" to 180f, "9" to 270f).forEach { (label, angle) ->
                val p = polarDp(angle, NUMBER_RADIUS)
                Text(label, style = SaText.tag, color = SaColors.Brown, modifier = Modifier.align(Alignment.Center).offset(p.x.mdp, p.y.mdp))
            }
            if (session == null) {
                EmptyCenter(scale)
            } else {
                SessionCenter(session, index = selected, total = sessions.size, nowMs, is24Hour, zone, scale)
            }
        }
    }
}

/** Centro de H: "SESIÓN 3 DE 4", icono, nombre, horario, conteo por nivel y "GIRA LA CORONA". */
@Composable
private fun BoxScope.SessionCenter(
    session: Session,
    index: Int,
    total: Int,
    nowMs: Long,
    is24Hour: Boolean,
    zone: ZoneId,
    scale: WatchScale,
) = with(scale) {
    val range = rangeText(session, nowMs, is24Hour, zone)
    val counts = levelCounts(session.events)
    Column(
        Modifier
            .align(Alignment.Center)
            .width(104.mdp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SESIÓN ${index + 1} DE $total", style = SaText.labelSmall, color = SaColors.Brown, maxLines = 1)
        Spacer(Modifier.height(3.mdp))
        GlyphIcon(session.context.icon, glyphHeight = 11.mdp, tint = SaColors.Ink, contentDescription = "Contexto")
        Spacer(Modifier.height(2.mdp))
        FitText(session.context.label, SaText.screenTitle.copy(textAlign = TextAlign.Center), SaColors.Ink, Modifier.width(100.mdp))
        FitText(range, SaText.body, SaColors.Brown, Modifier.width(104.mdp))
        Spacer(Modifier.height(3.mdp))
        LevelChip(counts, scale)
        Spacer(Modifier.height(3.mdp))
        Text("GIRA LA CORONA", style = SaText.tag, color = SaColors.Brown, maxLines = 1)
    }
}

/** Chip con el conteo por nivel ("● 2 avisos"); el punto lleva el color del nivel. */
@Composable
private fun LevelChip(counts: List<Pair<Priority, String>>, scale: WatchScale) = with(scale) {
    Row(
        Modifier.clip(CircleShape).background(SaColors.BrownFaint).padding(horizontal = 5.mdp, vertical = 2.mdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.mdp),
    ) {
        if (counts.isEmpty()) {
            Text("Sin alertas", style = SaText.labelSmall, color = SaColors.Ink, maxLines = 1)
        }
        counts.forEach { (priority, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.5.mdp).clip(CircleShape).background(priority.color))
                Spacer(Modifier.width(2.mdp))
                Text(text, style = SaText.labelSmall, color = SaColors.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BoxScope.EmptyCenter(scale: WatchScale) = with(scale) {
    Column(Modifier.align(Alignment.Center).width(104.mdp), horizontalAlignment = Alignment.CenterHorizontally) {
        FitText("Sin sesiones", SaText.screenTitle.copy(textAlign = TextAlign.Center), SaColors.Ink, Modifier.width(100.mdp))
        Spacer(Modifier.height(2.mdp))
        FitText("Activa la escucha para empezar la primera", SaText.body, SaColors.Brown, Modifier.width(100.mdp), maxLines = 2)
    }
}

/** Arco de una sesión sobre la pista, de su inicio a su fin (o a ahora). */
private fun DrawScope.drawSession(session: Session, nowMs: Long, zone: ZoneId, u: Float, color: Color, width: Float) {
    val r = TRACK_RADIUS * u
    drawArc(
        color,
        startAngle = dialAngle(session.startMs, zone) - 90f,
        sweepAngle = dialSweep(session.startMs, session.endOr(nowMs)),
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width * u, cap = StrokeCap.Butt),
    )
}

/** Marcas del borde: 12 horas más largas y 48 intermedias más tenues. */
private fun DrawScope.drawDialTicks(u: Float) {
    val outer = 93f * u
    for (i in 0 until 60) {
        val hour = i % 5 == 0
        val length = (if (hour) 4.5f else 2f) * u
        val angle = i * 6f
        drawLine(
            if (hour) SaColors.Brown else SaColors.Brown.copy(alpha = 0.35f),
            polar(angle, outer - length),
            polar(angle, outer),
            strokeWidth = (if (hour) 1.2f else 0.8f) * u,
            cap = StrokeCap.Round,
        )
    }
}

/** Punto de un evento con borde del color del fondo, para que se distinga sobre el arco. */
internal fun DrawScope.drawDot(at: Offset, radius: Float, color: Color, border: Float) {
    drawCircle(SaColors.Peach, radius = radius + border, center = at)
    drawCircle(color, radius = radius, center = at)
}

/** Punto a [radius] px del centro en el ángulo de la esfera (0° = las 12). */
internal fun DrawScope.polar(dialDegrees: Float, radius: Float): Offset {
    val rad = Math.toRadians((dialDegrees - 90f).toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

private fun polarDp(dialDegrees: Float, radius: Float): Offset {
    val rad = Math.toRadians((dialDegrees - 90f).toDouble())
    return Offset(radius * cos(rad).toFloat(), radius * sin(rad).toFloat())
}

@Composable
internal fun nowEvery(periodMs: Long) = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(periodMs)
        value = System.currentTimeMillis()
    }
}

/** Radios sobre 192: pista de 10 de grosor en r = 80, números en r = 66. */
private const val TRACK_RADIUS = 80f
private const val NUMBER_RADIUS = 66f
