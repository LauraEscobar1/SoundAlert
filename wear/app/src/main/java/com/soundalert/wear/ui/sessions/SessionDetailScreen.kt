package com.soundalert.wear.ui.sessions

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.session.Session
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.glass
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import java.time.ZoneId

/**
 * I · Detalle de una sesión: arco de 240° del inicio (abajo a la izquierda) al fin
 * (abajo a la derecha) con cada alerta en su minuto. La corona recorre las alertas;
 * la seleccionada se ve en la tarjeta de vidrio de abajo.
 */
@Composable
fun SessionDetailScreen(sessionId: String) {
    val all by SoundAlertRuntime.sessionTracker.sessions.collectAsStateWithLifecycle()
    val now by nowEvery(30_000)
    val visible = visibleSessions(all, now)
    val session = all.firstOrNull { it.id == sessionId } ?: return
    // Misma numeración que la esfera; una sesión que ya salió de ella se numera entre todas.
    val (index, total) = visible.indexOf(session).takeIf { it >= 0 }?.let { it to visible.size } ?: (all.indexOf(session) to all.size)
    var selected by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    SessionDetailContent(
        session = session,
        index = index,
        total = total,
        selectedEvent = selected.coerceIn(0, (session.events.size - 1).coerceAtLeast(0)),
        nowMs = now,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onStep = { selected = (selected + it).coerceIn(0, (session.events.size - 1).coerceAtLeast(0)) },
    )
}

@Composable
fun SessionDetailContent(
    session: Session,
    index: Int,
    total: Int,
    selectedEvent: Int,
    nowMs: Long,
    is24Hour: Boolean,
    onStep: (Int) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val end = session.endOr(nowMs)
    WatchScreen(Modifier.rotarySteps(onStep = onStep)) { scale ->
        with(scale) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 192f
                val r = ARC_RADIUS * u
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(r * 2, r * 2)
                drawArc(SaColors.BrownFaint, ARC_START, ARC_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(9f * u, cap = StrokeCap.Round))
                drawArc(SaColors.Ink, ARC_START, ARC_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(3f * u, cap = StrokeCap.Round))
                session.events.forEachIndexed { i, event ->
                    // ARC_START está en grados de Canvas (0° = las 3); polar usa la esfera (0° = las 12).
                    val angle = ARC_START + 90f + ARC_SWEEP * sessionFraction(event.atMs, session.startMs, end)
                    val at = polar(angle, r)
                    if (i == selectedEvent) {
                        drawCircle(SaColors.Ink, radius = 8f * u, center = at, style = Stroke(1.5f * u))
                        drawDot(at, 5f * u, event.priority.color, 1.4f * u)
                    } else {
                        drawDot(at, 3.4f * u, event.priority.color, 1.2f * u)
                    }
                }
            }
            EndLabel(clockTime(session.startMs, is24Hour, zone), x = -60f, scale)
            EndLabel(if (session.ongoing) "ahora" else clockTime(end, is24Hour, zone), x = 60f, scale)

            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 40.mdp).width(110.mdp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SESIÓN ${index + 1} DE $total", style = SaText.labelSmall, color = SaColors.Brown, maxLines = 1)
                Spacer(Modifier.height(3.mdp))
                GlyphIcon(session.context.icon, glyphHeight = 11.mdp, tint = SaColors.Ink, contentDescription = "Contexto")
                Spacer(Modifier.height(2.mdp))
                FitText(session.context.label, SaText.screenTitle.copy(textAlign = TextAlign.Center), SaColors.Ink, Modifier.width(100.mdp))
                FitText(rangeText(session, nowMs, is24Hour, zone), SaText.body, SaColors.Brown, Modifier.width(110.mdp))
            }
            EventCard(session, selectedEvent, is24Hour, zone, scale)
        }
    }
}

/** Tarjeta de vidrio: "● Sirena · 08:05 ✓" y "Confirmada por ti". */
@Composable
private fun BoxScope.EventCard(session: Session, selected: Int, is24Hour: Boolean, zone: ZoneId, scale: WatchScale) = with(scale) {
    val event = session.events.getOrNull(selected)
    Column(
        Modifier
            .align(Alignment.Center)
            .offset(y = 24.mdp)
            .width(112.mdp)
            .glass(RoundedCornerShape(12.mdp))
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 8.mdp, vertical = 5.mdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (event == null) {
            FitText("Sin alertas en esta sesión", SaText.body, SaColors.Ink, Modifier.width(96.mdp), maxLines = 2)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.mdp).clip(CircleShape).background(event.priority.color))
            Spacer(Modifier.width(3.mdp))
            FitText(
                "${SoundUi.shortName(event.category)} · ${clockTime(event.atMs, is24Hour, zone)}",
                SaText.row.copy(fontSize = SaText.row.fontSize * 0.9f),
                SaColors.Ink,
                Modifier.weight(1f, fill = false),
            )
            if (event.acknowledged) {
                Spacer(Modifier.width(3.mdp))
                GlyphIcon(R.drawable.ic_check, glyphHeight = 6.5.mdp, tint = SaColors.Ink, contentDescription = "Confirmada")
            }
        }
        FitText(eventOutcome(event), SaText.body, SaColors.Ink, Modifier.width(96.mdp))
        if (session.events.size > 1) {
            Text("${selected + 1} / ${session.events.size}", style = SaText.tag, color = SaColors.Brown, maxLines = 1)
        }
    }
}

/** Hora de inicio o de fin, junto a su extremo del arco (abajo a la izquierda o a la derecha). */
@Composable
private fun BoxScope.EndLabel(text: String, x: Float, scale: WatchScale) = with(scale) {
    Text(text, style = SaText.tag, color = SaColors.Ink, maxLines = 1, modifier = Modifier.align(Alignment.Center).offset(x.mdp, 58.mdp))
}

/** Arco de 240° en grados de Canvas: empieza abajo a la izquierda (150°) y acaba abajo a la derecha (30°). */
private const val ARC_START = 150f
private const val ARC_SWEEP = 240f
private const val ARC_RADIUS = 80f
