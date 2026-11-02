package com.soundalert.wear.ui.sessions

import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.components.rememberNow
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * H · Historial como una pila de tarjetas: las sesiones de hoy, de la más reciente a la
 * más antigua, y al final "Tu día". Se pasa de tarjeta deslizando hacia arriba (y hacia
 * abajo para volver) o con la corona; un toque en una sesión abre su detalle. Todo sale
 * del SessionTracker y se actualiza al llegar sonidos nuevos.
 */
@Composable
fun SessionStackScreen(visible: Boolean, onOpen: (String) -> Unit) {
    val all by SoundAlertRuntime.sessionTracker.sessions.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    SessionStackContent(
        cards = stackCards(all, now),
        nowMs = now,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onOpen = onOpen,
        visible = visible,
    )
}

/**
 * La pila (sin estado propio salvo la tarjeta actual; también la usan las previews y los
 * tests). Medidas sobre 192: "Sesiones 2/5" en y≈30; tarjeta de 150 × 84 (78 % del ancho,
 * esquinas de 22) centrada en y = 100, con dos tarjetas asomando 8 y 16 dp por encima,
 * más angostas; y los puntitos de posición en y≈152.
 */
@Composable
fun SessionStackContent(
    cards: List<StackCard>,
    nowMs: Long,
    is24Hour: Boolean,
    onOpen: (String) -> Unit,
    visible: Boolean = true,
    initialIndex: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val last = (cards.size - 1).coerceAtLeast(0)
    var current by rememberSaveable { mutableIntStateOf(initialIndex) }
    current = current.coerceIn(0, last)
    // Posición continua de la pila (en tarjetas): la sigue el dedo y la mueve un resorte.
    val position = remember { Animatable(current.toFloat()) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val ambient = LocalAmbient.current

    fun goTo(target: Int, haptic: Boolean = false) {
        val clamped = target.coerceIn(0, last)
        if (haptic && clamped != current) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        current = clamped
        scope.launch { position.animateTo(clamped.toFloat(), spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) }
    }

    // Respiración de las tarjetas de atrás: ~1 dp en un ciclo de 4 s (quietas en modo ambiente).
    var breath by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ambient) {
        if (ambient) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            val now = withInfiniteAnimationFrameNanos { it }
            breath = sin((now - start) / 1_000_000_000f * 2f * PI.toFloat() / 4f)
        }
    }

    WatchScreen(
        Modifier
            .rotarySteps(active = visible) { goTo(current + it, haptic = true) }
            .pointerInput(cards.size) {
                val perCard = DRAG_PER_CARD.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = { goTo(settleIndex(current, position.value - current, cards.size)) },
                    onDragCancel = { goTo(current) },
                ) { change, dy ->
                    change.consume()
                    // Hacia arriba (dy < 0) avanza; la tarjeta de adelante sigue al dedo, una
                    // tarjeta como mucho por gesto (y un poco de holgura en los extremos).
                    val next = (position.value - dy / perCard).coerceIn(maxOf(-0.25f, current - 1f), minOf(last + 0.25f, current + 1f))
                    scope.launch { position.snapTo(next) }
                }
            },
    ) { scale ->
        with(scale) {
            Row(Modifier.centerAt(30.mdp), verticalAlignment = Alignment.Bottom) {
                Text("Sesiones", style = SaText.screenTitle, color = SaColors.Wine, maxLines = 1)
                Spacer(Modifier.width(3.mdp))
                Text("${current + 1}/${cards.size}", style = SaText.tag, color = SaColors.Wine, maxLines = 1, modifier = Modifier.padding(bottom = 1.mdp))
            }

            // De atrás hacia adelante: dos por detrás, la actual y la que acaba de salir por arriba.
            val p = position.value
            val front = floor(p).toInt()
            for (i in (front + 3) downTo (front - 1)) {
                val card = cards.getOrNull(i) ?: continue
                val depth = staggered(i - p)
                if (depth > 2.9f || depth < -1f) continue
                StackedCard(
                    card = card,
                    depth = depth,
                    breath = if (depth > 0.5f) breath else 0f,
                    isFront = i == current,
                    nowMs = nowMs,
                    is24Hour = is24Hour,
                    zone = zone,
                    scale = scale,
                    onClick = (card as? StackCard.SessionCard)?.let { { onOpen(it.session.id) } }.takeIf { i == current },
                )
            }

            PositionDots(cards.size, current, scale, Modifier.centerAt(152.mdp))
        }
    }
}

/**
 * Escalonamiento: las tarjetas de más atrás empiezan a moverse un poco después que la
 * de delante (12 % del recorrido por nivel), sin saltos al principio ni al final.
 */
internal fun staggered(depth: Float): Float {
    if (depth <= 0f) return depth
    val base = floor(depth)
    val progress = 1f - (depth - base) // 0 → 1 mientras sube de base + 1 a base
    val delay = 0.12f * minOf(base, 2f)
    val delayed = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    return base + 1f - delayed
}

/**
 * Una tarjeta en su lugar de la pila según [depth]: 0 al frente; 1 y 2 detrás (8 y 16 dp
 * más arriba, al 92 % y 84 %, en vino al 45 % y 25 %); entre −1 y 0, saliendo por arriba
 * con un giro de 4° mientras se desvanece.
 */
@Composable
private fun BoxScope.StackedCard(
    card: StackCard,
    depth: Float,
    breath: Float,
    isFront: Boolean,
    nowMs: Long,
    is24Hour: Boolean,
    zone: ZoneId,
    scale: WatchScale,
    onClick: (() -> Unit)?,
) = with(scale) {
    val unit = with(LocalDensity.current) { 1.mdp.toPx() }
    val tint = when {
        depth <= 0f -> 1f
        depth <= 1f -> 1f - 0.55f * depth
        else -> 0.45f - 0.20f * (depth - 1f).coerceAtMost(1f)
    }
    val fill = SaColors.Wine.copy(alpha = tint).compositeOver(SaColors.Background)
    val contentAlpha = (1f - depth * 2.5f).coerceIn(0f, 1f) * (1f + depth).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(CARD_CORNER.mdp)
    val click = if (onClick != null) Modifier.clickable(onClickLabel = "Ver el detalle de la sesión", role = Role.Button, onClick = onClick) else Modifier
    Box(
        Modifier
            .centerAt(CARD_Y.mdp)
            .size(width = CARD_WIDTH.mdp, height = CARD_HEIGHT.mdp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                if (depth >= 0f) {
                    val s = 1f - 0.08f * depth
                    scaleX = s
                    scaleY = s
                    translationY = (-8f * depth + breath) * unit
                } else {
                    // Sale volando hacia arriba con un leve giro y se desvanece.
                    translationY = depth * 150f * unit
                    rotationZ = depth * 4f
                    alpha = (1f + depth * 1.4f).coerceIn(0f, 1f)
                }
            }
            .clip(shape)
            .background(fill)
            .then(click),
    ) {
        if (contentAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }.padding(horizontal = 12.mdp, vertical = 9.mdp)) {
                when (card) {
                    is StackCard.SessionCard -> SessionCardContent(card.session, isFront, nowMs, is24Hour, zone, scale)
                    is StackCard.DayCard -> DayCardContent(card.day, isFront, scale)
                }
            }
        }
    }
}

/** Contenido de una tarjeta de sesión, en rosa sobre vino (7,1:1). */
@Composable
private fun SessionCardContent(session: Session, isFront: Boolean, nowMs: Long, is24Hour: Boolean, zone: ZoneId, scale: WatchScale) = with(scale) {
    val end = session.endOr(nowMs)
    // Al llegar al frente: la línea se dibuja (400 ms), los puntos aparecen uno tras otro
    // con un pequeño rebote y el resumen entra con un fundido.
    val line = remember(session.id) { Animatable(if (isFront) 0f else 1f) }
    val dots = remember(session.id, session.events.size) { List(session.events.size) { Animatable(0f) } }
    val summary = remember(session.id) { Animatable(0f) }
    LaunchedEffect(isFront, session.id, session.events.size) {
        if (!isFront) return@LaunchedEffect
        line.snapTo(0f)
        summary.snapTo(0f)
        dots.forEach { it.snapTo(0f) }
        launch { line.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
        session.events.forEachIndexed { k, event ->
            launch {
                delay((sessionFraction(event.atMs, session.startMs, end) * 400).toLong() + 60L * k)
                dots[k].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
        }
        launch {
            delay(300)
            summary.animateTo(1f, tween(350))
        }
    }
    // Sin fusionar aquí: los textos se fusionan en la tarjeta, que es la que se toca.
    Column(Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(session.context.icon, glyphHeight = 7.5.mdp, tint = SaColors.Background, contentDescription = "Contexto")
            Spacer(Modifier.width(4.mdp))
            FitText(session.context.label, SaText.screenTitle.copy(fontSize = 10.sp), SaColors.Background, Modifier.weight(1f))
            Text(
                durationText(end - session.startMs).uppercase(),
                style = SaText.tag.copy(fontSize = 5.2.sp),
                color = SaColors.Background,
                maxLines = 1,
                modifier = Modifier
                    .border(0.75.dp, SaColors.Background.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 3.mdp, vertical = 1.mdp),
            )
        }
        val endLabel = if (session.ongoing) "ahora" else clockTime(end, is24Hour, zone)
        Text("${clockTime(session.startMs, is24Hour, zone)} – $endLabel", style = SaText.body.copy(fontSize = 7.sp), color = SaColors.Background, maxLines = 1)
        Column {
            Canvas(Modifier.fillMaxWidth().height(7.mdp)) {
                val y = size.height / 2
                val drawn = size.width * line.value
                drawLine(SaColors.Background.copy(alpha = 0.45f), Offset(0f, y), Offset(drawn, y), strokeWidth = 1.dp.toPx())
                session.events.forEachIndexed { k, event ->
                    val pop = dots.getOrNull(k)?.value ?: 1f
                    if (pop <= 0f) return@forEachIndexed
                    val at = Offset(size.width * sessionFraction(event.atMs, session.startMs, end), y)
                    val r = 2.3.mdp.toPx() * pop
                    drawCircle(SaColors.Wine, radius = r + 1.dp.toPx() * pop, center = at)
                    drawCircle(event.dotColor, radius = r, center = at)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clockTime(session.startMs, is24Hour, zone), style = SaText.tag.copy(fontSize = 5.sp, letterSpacing = 0.em), color = SaColors.Background, maxLines = 1)
                Text(endLabel, style = SaText.tag.copy(fontSize = 5.sp, letterSpacing = 0.em), color = SaColors.Background, maxLines = 1)
            }
        }
        FitText(
            sessionSummary(session),
            SaText.row.copy(fontSize = 6.8.sp, fontWeight = FontWeight.ExtraBold),
            SaColors.Background,
            Modifier.fillMaxWidth().graphicsLayer { alpha = summary.value },
        )
    }
}

/**
 * "Tu día": sesiones y tiempo escuchando (cuentan hacia arriba desde 0), barra apilada de
 * alertas por nivel que crece segmento a segmento y la sesión más tranquila.
 */
@Composable
private fun DayCardContent(day: DaySummary, isFront: Boolean, scale: WatchScale) = with(scale) {
    val count = remember { Animatable(if (isFront) 0f else 1f) }
    val bar = remember { Animatable(if (isFront) 0f else 1f) }
    LaunchedEffect(isFront) {
        if (!isFront) return@LaunchedEffect
        count.snapTo(0f)
        bar.snapTo(0f)
        launch { count.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        launch {
            delay(150)
            bar.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val minutes = (day.listeningMs / 60_000 * count.value).roundToInt()
    val sessions = (day.sessions * count.value).roundToInt()
    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.SpaceBetween) {
        Text("TU DÍA", style = SaText.tag.copy(fontSize = 5.6.sp), color = SaColors.Background, maxLines = 1)
        Text(sessionsText(sessions), style = SaText.title.copy(fontSize = 13.sp, lineHeight = 13.sp), color = SaColors.Background, maxLines = 1)
        Text(
            if (day.sessions == 0) "Activa la escucha para empezar" else "${durationText(minutes * 60_000L)} escuchando por ti",
            style = SaText.body.copy(fontSize = 6.8.sp),
            color = SaColors.Background,
            maxLines = 1,
        )
        Column {
            Canvas(Modifier.fillMaxWidth().height(4.5.mdp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(SaColors.Background.copy(alpha = 0.2f), cornerRadius = r)
                if (day.alerts == 0) return@Canvas
                var x = 0f
                // Cada segmento crece cuando termina el anterior.
                listOf(Priority.DANGER to day.danger, Priority.ATTENTION to day.attention, Priority.INFORMATION to day.information)
                    .forEachIndexed { k, (priority, n) ->
                        val share = size.width * n / day.alerts
                        val grown = share * ((bar.value * 3f - k).coerceIn(0f, 1f))
                        if (grown > 0f) drawRoundRect(priority.color, topLeft = Offset(x, 0f), size = Size(grown, size.height), cornerRadius = r)
                        x += share
                    }
            }
            Spacer(Modifier.height(1.5.mdp))
            Text(levelsLine(day), style = SaText.tag.copy(fontSize = 4.8.sp, letterSpacing = 0.04.em), color = SaColors.Background, maxLines = 1)
        }
        val calmest = day.calmest
        Text(
            if (calmest != null) "Más tranquila: ${calmest.context.label}" else "Sin sesiones todavía",
            style = SaText.body.copy(fontSize = 6.5.sp),
            color = SaColors.Background,
            maxLines = 1,
        )
    }
}

/** Puntitos de posición: el actual es una píldora alargada en vino; el resto, puntos en vino al 45 %. Como mucho 7. */
@Composable
private fun PositionDots(count: Int, current: Int, scale: WatchScale, modifier: Modifier) = with(scale) {
    val window = 7
    val first = (current - window / 2).coerceIn(0, (count - window).coerceAtLeast(0))
    Row(modifier.semantics { contentDescription = "Tarjeta ${current + 1} de $count" }, horizontalArrangement = Arrangement.spacedBy(2.2.mdp), verticalAlignment = Alignment.CenterVertically) {
        for (i in first until minOf(count, first + window)) {
            val width by animateDpAsState(if (i == current) 8.mdp else 3.mdp, spring(stiffness = Spring.StiffnessMediumLow), label = "punto")
            Box(
                Modifier
                    .size(width = width, height = 3.mdp)
                    .clip(CircleShape)
                    .background(if (i == current) SaColors.Wine else SaColors.Wine.copy(alpha = 0.45f)),
            )
        }
    }
}

/** Tarjeta sobre 192: 78 % del ancho, esquinas de 22, centrada en y = 100. */
private const val CARD_WIDTH = 150f
private const val CARD_HEIGHT = 84f
private const val CARD_CORNER = 22f
private const val CARD_Y = 100f

/** Arrastre (dp) que equivale a pasar una tarjeta entera. */
private const val DRAG_PER_CARD = 90f
