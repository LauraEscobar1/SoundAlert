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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import com.soundalert.wear.ui.components.centerAt
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
                drawCircle(SaColors.BrownFaint, radius = TRACK_RADIUS * u, style = Stroke(TRACK_WIDTH * u))
                // De atrás hacia delante: el resto, la seleccionada (más gruesa) y los eventos encima.
                sessions.forEachIndexed { i, s -> if (i != selected) drawSession(s, nowMs, zone, u, if (s.ongoing) SaColors.Rust else SaColors.Brown.copy(alpha = 0.45f), TRACK_WIDTH) }
                session?.let { drawSession(it, nowMs, zone, u, SaColors.Orange, SELECTED_WIDTH) }
                sessions.forEach { s ->
                    s.events.forEach { e -> drawDot(polar(dialAngle(e.atMs, zone), TRACK_RADIUS * u), 3.25f * u, e.priority.color, 1.3f * u) }
                }
            }
            listOf("12" to 0f, "3" to 90f, "6" to 180f, "9" to 270f).forEach { (label, angle) ->
                val p = polarDp(angle, NUMBER_RADIUS)
                Text(label, style = SaText.tag.copy(letterSpacing = 0.em), color = SaColors.Brown, modifier = Modifier.align(Alignment.Center).offset(p.x.mdp, p.y.mdp))
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
    // Centros verticales medidos en el mockup (sobre 192).
    Box(Modifier.fillMaxSize().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text("SESIÓN ${index + 1} DE $total", style = SaText.labelSmall, color = SaColors.Brown, maxLines = 1, modifier = Modifier.centerAt(50.4.mdp))
        GlyphIcon(session.context.icon, glyphHeight = 9.mdp, tint = SaColors.Ink, contentDescription = "Contexto", modifier = Modifier.centerAt(65.4.mdp))
        FitText(session.context.label, SaText.title, SaColors.Ink, Modifier.centerAt(84.7.mdp).width(110.mdp))
        FitText(range, SaText.bodyStrong, SaColors.Ink, Modifier.centerAt(98.3.mdp).width(110.mdp))
        LevelChip(counts, scale, Modifier.centerAt(112.7.mdp))
        Text("GIRA LA CORONA", style = SaText.footer, color = SaColors.Brown, maxLines = 1, modifier = Modifier.centerAt(126.8.mdp))
    }
}

/** Chip con el conteo por nivel ("● 2 avisos"); el punto lleva el color del nivel. */
@Composable
private fun LevelChip(counts: List<Pair<Priority, String>>, scale: WatchScale, modifier: Modifier) = with(scale) {
    val style = SaText.row.copy(fontSize = 6.3.sp)
    Row(
        modifier.height(11.mdp).clip(CircleShape).background(SaColors.BrownFaint).padding(horizontal = 4.8.mdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.mdp),
    ) {
        if (counts.isEmpty()) {
            Text("Sin alertas", style = style, color = SaColors.Brown, maxLines = 1)
        }
        counts.forEach { (priority, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.2.mdp).clip(CircleShape).background(priority.color))
                Spacer(Modifier.width(2.mdp))
                Text(text, style = style, color = SaColors.Brown, maxLines = 1)
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

/** Marcas finas del borde (r 90,5 – 92,1): las 12 de las horas algo más marcadas. */
private fun DrawScope.drawDialTicks(u: Float) {
    val outer = 92.1f * u
    for (i in 0 until 60) {
        val hour = i % 5 == 0
        val length = 1.8f * u
        val angle = i * 6f
        drawLine(
            SaColors.Brown.copy(alpha = if (hour) 0.6f else 0.4f),
            polar(angle, outer - length),
            polar(angle, outer),
            strokeWidth = (if (hour) 0.9f else 0.6f) * u,
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

/** Medidas del mockup sobre 192: pista de 6,5 en r = 82,9 (la seleccionada, de 10) y números en r = 67,8. */
private const val TRACK_RADIUS = 82.9f
private const val TRACK_WIDTH = 6.5f
private const val SELECTED_WIDTH = 10f
private const val NUMBER_RADIUS = 67.8f
