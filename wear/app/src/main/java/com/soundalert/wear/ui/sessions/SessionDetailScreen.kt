package com.soundalert.wear.ui.sessions

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
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
import com.soundalert.wear.ui.components.rememberNow
import com.soundalert.wear.ui.components.centerAt
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
    val now = rememberNow(30_000)
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
                // Pista tenue de 4,9 y línea de tinta de 1,6 (medidas del mockup).
                drawArc(SaColors.Brown.copy(alpha = 0.12f), ARC_START, ARC_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(4.9f * u, cap = StrokeCap.Round))
                drawArc(SaColors.Ink, ARC_START, ARC_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(1.6f * u, cap = StrokeCap.Round))
                session.events.forEachIndexed { i, event ->
                    // ARC_START está en grados de Canvas (0° = las 3); polar usa la esfera (0° = las 12).
                    val angle = ARC_START + 90f + ARC_SWEEP * sessionFraction(event.atMs, session.startMs, end)
                    val at = polar(angle, r)
                    if (i == selectedEvent) {
                        drawCircle(SaColors.Peach, radius = 10.4f * u, center = at)
                        drawCircle(SaColors.Ink, radius = 10.4f * u, center = at, style = Stroke(1f * u))
                        drawDot(at, 4.5f * u, event.dotColor, 1.3f * u)
                    } else {
                        drawDot(at, 3.5f * u, event.dotColor, 1.3f * u)
                    }
                }
            }
            EndLabel(clockTime(session.startMs, is24Hour, zone), x = -END_LABEL_X, scale)
            EndLabel(if (session.ongoing) "ahora" else clockTime(end, is24Hour, zone), x = END_LABEL_X, scale)

            // Centros verticales medidos en el mockup (sobre 192).
            Text("SESIÓN ${index + 1} DE $total", style = SaText.labelSmall, color = SaColors.Brown, maxLines = 1, modifier = Modifier.centerAt(52.1.mdp))
            GlyphIcon(session.context.icon, glyphHeight = 11.mdp, tint = SaColors.Brown, contentDescription = "Contexto", modifier = Modifier.centerAt(66.3.mdp))
            FitText(session.context.label, SaText.title, SaColors.Ink, Modifier.centerAt(83.4.mdp).width(110.mdp))
            FitText(rangeText(session, nowMs, is24Hour, zone), SaText.body, SaColors.Brown, Modifier.centerAt(97.mdp).width(110.mdp))
            EventCard(session, selectedEvent, is24Hour, zone, scale)
        }
    }
}

/** Tarjeta de vidrio: "● Sirena · 08:05 ✓" y "Confirmada por ti". */
@Composable
private fun BoxScope.EventCard(session: Session, selected: Int, is24Hour: Boolean, zone: ZoneId, scale: WatchScale) = with(scale) {
    val event = session.events.getOrNull(selected)
    Box(Modifier.fillMaxSize().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        // Píldora de vidrio de 74 × 15 en y≈115 y, debajo, qué pasó (y≈131).
        Row(
            Modifier
                .centerAt(115.3.mdp)
                .height(14.9.mdp)
                .widthIn(min = 74.mdp)
                .glass(RoundedCornerShape(50))
                .padding(horizontal = 7.mdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (event == null) {
                Text("Sin sonidos en esta sesión", style = SaText.row, color = SaColors.Ink, maxLines = 1)
            } else {
                Box(Modifier.size(3.6.mdp).clip(CircleShape).background(event.dotColor))
                Spacer(Modifier.width(3.mdp))
                Text("${SoundUi.shortName(event.category)} · ${clockTime(event.atMs, is24Hour, zone)}", style = SaText.row, color = SaColors.Ink, maxLines = 1)
                if (event.acknowledged) {
                    Spacer(Modifier.width(3.mdp))
                    GlyphIcon(R.drawable.ic_check, glyphHeight = 3.9.mdp, tint = SaColors.Ink, contentDescription = "Confirmada")
                }
            }
        }
        if (event != null) {
            FitText(eventOutcome(event), SaText.body.copy(fontSize = 6.sp), SaColors.Brown, Modifier.centerAt(130.9.mdp).width(100.mdp))
        }
    }
}

/** Hora de inicio o de fin, junto a su extremo del arco (abajo a la izquierda o a la derecha). */
@Composable
private fun BoxScope.EndLabel(text: String, x: Float, scale: WatchScale) = with(scale) {
    // Cifras tabulares de Archivo: la monoespaciada del sistema separa los dos puntos ("07: 40").
    Text(text, style = SaText.clock.copy(fontSize = 5.8.sp, fontFeatureSettings = "tnum"), color = SaColors.Ink, maxLines = 1, modifier = Modifier.centerAt(END_LABEL_Y.mdp, x = x.mdp))
}

/** Arco de 240° en grados de Canvas: empieza abajo a la izquierda (150°) y acaba abajo a la derecha (30°). */
private const val ARC_START = 150f
private const val ARC_SWEEP = 240f
private const val ARC_RADIUS = 76.9f

/**
 * Horas de los extremos: bajo cada punta del arco, como en el mockup (±66,3; 144,2), pero
 * acercadas al centro hasta el margen de seguridad (radio 76 de 96, ~10 % del diámetro).
 */
private const val END_LABEL_X = 61.5f
private const val END_LABEL_Y = 140.7f
