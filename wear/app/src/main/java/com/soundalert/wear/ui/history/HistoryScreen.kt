package com.soundalert.wear.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.history.HistoryEntry
import com.soundalert.wear.history.HistoryOutcome
import com.soundalert.wear.history.HistoryRepository
import com.soundalert.wear.sync.SyncState
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.ListFootnote
import com.soundalert.wear.ui.components.ListRow
import com.soundalert.wear.ui.components.SoundBadge
import com.soundalert.wear.ui.components.WatchList
import com.soundalert.wear.ui.state.historyTime
import com.soundalert.wear.ui.state.isToday
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay

/**
 * F · Historial: detecciones y alertas reales. Con conexión, las guardadas en el
 * backend (Railway → Supabase) más las que aún esperan en la cola; sin conexión, las
 * que el reloj tiene en memoria. Se recarga al mostrarse y tras cada sincronización.
 */
@Composable
fun HistoryScreen(visible: Boolean) {
    val android = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(android)
    val detections by SoundAlertRuntime.detectionHistory.detections.collectAsStateWithLifecycle()
    val alerts by SoundAlertRuntime.alertManager(android).alerts.collectAsStateWithLifecycle()
    val sync by SoundAlertRuntime.backendSync(android).status.collectAsStateWithLifecycle()
    var remote by remember { mutableStateOf<HistoryRepository.RemoteResult?>(null) }

    LaunchedEffect(visible, sync.lastSyncAtMs, sync.deviceId) {
        if (visible && sync.state != SyncState.DISABLED) remote = SoundAlertRuntime.historyRepository(android).loadRemote()
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val local = remember(detections, alerts) { HistoryRepository.fromLocal(detections, alerts) }
    val loaded = (remote as? HistoryRepository.RemoteResult.Loaded)?.entries
    val entries = HistoryRepository.merge(loaded, local, sync.pendingDetectionIds)
    val today = entries.isNotEmpty() && entries.all { isToday(it.atMs, now) }

    WatchList(title = "Historial", tag = if (today) "HOY" else null) { spec ->
        if (entries.isEmpty()) {
            item { ListFootnote(if (remote == null && visible && sync.state != SyncState.DISABLED) "Cargando…" else "Aún no se ha detectado ningún sonido", SaColors.Secondary) }
        }
        items(entries, key = { it.id }) { entry ->
            ListRow(
                spec = spec,
                container = SaColors.Surface,
                height = 42.dp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = describe(entry, historyTime(entry.atMs, now, is24Hour)) },
            ) {
                val badge = entry.priority?.color ?: SaColors.Inactive
                SoundBadge(entry.category, badge, 24.dp, iconTint = if (entry.priority != null) SaColors.Background else SaColors.OnSurface)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name(entry), style = SaText.row, color = SaColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        Text(historyTime(entry.atMs, now, is24Hour), style = SaText.time, color = SaColors.Secondary, maxLines = 1)
                    }
                    Text(
                        "${entry.contextLabel} · ${outcomeText(entry.outcome)}",
                        style = SaText.body.copy(fontSize = SaText.body.fontSize * 0.85f, textAlign = TextAlign.Start),
                        color = SaColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item { ListFootnote(if (sync.state == SyncState.DISABLED) "Datos de este reloj" else sourceText(remote, sync.pending)) }
    }
}

private fun name(entry: HistoryEntry): String =
    if (entry.category != SoundCategory.UNKNOWN) SoundUi.shortName(entry.category) else entry.remoteName ?: SoundUi.shortName(entry.category)

private fun outcomeText(outcome: HistoryOutcome): String = when (outcome) {
    HistoryOutcome.ALERT_ACTIVE -> "Sin confirmar"
    HistoryOutcome.ALERT_ACKNOWLEDGED -> "Confirmada"
    HistoryOutcome.ALERT_EXPIRED -> "Se cerró sola"
    HistoryOutcome.NO_ALERT -> "Sin alerta"
    HistoryOutcome.RECORD_ONLY -> "Solo registro"
}

private fun sourceText(remote: HistoryRepository.RemoteResult?, pending: Int): String {
    val queued = if (pending > 0) " · $pending por enviar" else ""
    return when (remote) {
        is HistoryRepository.RemoteResult.Loaded -> "Sincronizado con SoundAlert$queued"
        is HistoryRepository.RemoteResult.Unavailable -> "Sin conexión (${remote.reason}) · datos de este reloj$queued"
        null -> "Conectando…"
    }
}

private fun describe(entry: HistoryEntry, time: String): String =
    "${SoundUi.name(entry.category).takeIf { entry.category != SoundCategory.UNKNOWN } ?: entry.remoteName ?: "Sonido"}, " +
        "${entry.contextLabel}, ${outcomeText(entry.outcome)}, $time"
