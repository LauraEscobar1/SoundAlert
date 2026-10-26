package com.soundalert.wear.ui.sounds

import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.soundalert.wear.R
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.ui.SoundUi
import com.soundalert.wear.ui.color
import com.soundalert.wear.ui.components.FitText
import com.soundalert.wear.ui.components.GlassToast
import com.soundalert.wear.ui.components.GlyphIcon
import com.soundalert.wear.ui.components.ToastState
import com.soundalert.wear.ui.components.WatchScale
import com.soundalert.wear.ui.components.WatchScreen
import com.soundalert.wear.ui.components.centerAt
import com.soundalert.wear.ui.components.rememberToastState
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.SoundCell
import com.soundalert.wear.ui.state.soundCells
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * G · Sonidos del contexto activo en un único panal de hexágonos (filas de 2 y 3) que se
 * desplaza en vertical con el dedo o con la corona, con inercia, como el lanzador de apps
 * de Wear OS: las celdas cerca de los bordes se achican y se desvanecen (ojo de pez).
 *
 * Son las reglas reales del reloj para ese contexto. Tocar una celda la enciende o la
 * apaga (SoundToggles, guardado por contexto) y el RuleEngine lo aplica a la siguiente
 * detección. Los sonidos de peligro llevan un candado: siempre están activos y tocarlos
 * solo lo recuerda. El encabezado (fijo) abre la pantalla de contexto.
 */
@Composable
fun SoundsScreen(visible: Boolean, onOpenContext: (() -> Unit)? = null) {
    val context by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val disabled by SoundAlertRuntime.soundToggles.disabled.collectAsStateWithLifecycle()
    val cells = remember(context, disabled) { soundCells(context, SoundAlertRuntime.ruleEngine) }
    val toast = rememberToastState()
    SoundsContent(
        context = context,
        cells = cells,
        onToggle = { cell ->
            val name = SoundUi.shortName(cell.category)
            when {
                cell.locked -> toast.show(LOCKED_TOAST)
                SoundAlertRuntime.soundToggles.setEnabled(context, cell.category, !cell.enabled) ->
                    toast.show(if (cell.enabled) "$name desactivado" else "$name activado", check = !cell.enabled)
            }
        },
        toast = toast,
        visible = visible,
        onOpenContext = onOpenContext,
    )
}

@Composable
fun SoundsContent(
    context: SoundAlertContext,
    cells: List<SoundCell>,
    onToggle: (SoundCell) -> Unit,
    toast: ToastState,
    visible: Boolean = true,
    onOpenContext: (() -> Unit)? = null,
    initialScrollDp: Float = 0f,
) {
    val slots = remember(cells.size) { honeycombSlots(cells.size, HEX_RADIUS, HEX_GAP) }
    val contentHeight = slots.maxOfOrNull { it.y } ?: 0f
    val maxScrollDp = (contentHeight - (LAST_ROW_Y - FIRST_ROW_Y)).coerceAtLeast(0f)
    WatchScreen { scale ->
        with(scale) {
            val unit = with(LocalDensity.current) { 1.mdp.toPx() } // px por dp del diseño
            val maxScroll = maxScrollDp * unit
            var offset by remember(context) { mutableFloatStateOf((initialScrollDp * unit).coerceIn(0f, maxScroll)) }
            val scrollState = rememberScrollableState { delta ->
                val target = (offset - delta).coerceIn(0f, maxScroll)
                val consumed = offset - target
                offset = target
                consumed
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(visible) { if (visible) focusRequester.requestFocus() }

            // Posición y ojo de pez de cada celda: se calculan en las fases de diseño y dibujo
            // (leen [offset] ahí), así que desplazar no recompone.
            fun centerY(i: Int) = (FIRST_ROW_Y + slots[i].y) * unit - offset
            fun lens(i: Int) = fisheye(centerY(i) / unit)

            Box(
                Modifier
                    .fillMaxSize()
                    .scrollable(scrollState, Orientation.Vertical)
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollState), focusRequester)
                    // Cada toque va a la celda de centro más cercano: cada una recibe toda su
                    // zona hexagonal (44 dp entre lados opuestos) sin pisar a sus vecinas.
                    .pointerInput(cells, slots) {
                        detectTapGestures { tap ->
                            val nearest = cells.indices.minByOrNull { i -> (Offset(size.width / 2f + slots[i].x * unit, centerY(i)) - tap).getDistance() }
                            if (nearest != null) {
                                val (cellScale, alpha) = lens(nearest)
                                val distance = (Offset(size.width / 2f + slots[nearest].x * unit, centerY(nearest)) - tap).getDistance()
                                if (alpha > 0.3f && distance <= HEX_RADIUS * unit * cellScale) onToggle(cells[nearest])
                            }
                        }
                    },
            ) {
                cells.forEachIndexed { index, cell ->
                    HexCell(
                        cell,
                        onToggle = { onToggle(cell) },
                        scale = scale,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset((slots[index].x * unit).roundToInt(), (centerY(index) - HEX_RADIUS * unit).roundToInt()) }
                            .graphicsLayer {
                                val (cellScale, alpha) = lens(index)
                                scaleX = cellScale
                                scaleY = cellScale
                                this.alpha = alpha
                            },
                    )
                }
            }

            // Encabezado fijo (centro en y≈26,6): icono y nombre del contexto + "5 DE 7". Abre el contexto.
            val open = if (onOpenContext != null) Modifier.clickable(onClickLabel = "Cambiar contexto", role = Role.Button, onClick = onOpenContext) else Modifier
            Row(
                Modifier
                    .centerAt(26.6.mdp)
                    .clip(RoundedCornerShape(50))
                    .background(SaColors.Background)
                    .then(open)
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 4.mdp, vertical = 1.mdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(context.icon, glyphHeight = 7.1.mdp, tint = SaColors.Wine, contentDescription = "Contexto")
                Spacer(Modifier.width(3.6.mdp))
                Text(context.label, style = SaText.screenTitle, color = SaColors.Wine, maxLines = 1)
                Spacer(Modifier.width(3.6.mdp))
                Text("${cells.count { it.enabled }} DE ${cells.size}", style = SaText.tag, color = SaColors.Wine, maxLines = 1)
            }

            GlassToast(toast, scale, Modifier.centerAt(163.mdp))
        }
    }
}

/**
 * Ojo de pez del panal según la altura del centro de la celda (dp sobre 192): a tamaño
 * completo en la franja central y cada vez más pequeña y transparente hacia los bordes,
 * hasta desaparecer bajo el encabezado y fuera del margen de seguridad.
 */
internal fun fisheye(y: Float): Pair<Float, Float> {
    val distance = abs(y - LENS_CENTER_Y)
    val t = ((distance - LENS_FLAT) / (LENS_EDGE - LENS_FLAT)).coerceIn(0f, 1f)
    val eased = t * t * (3 - 2 * t) // suave en los dos extremos
    return (1f - 0.55f * eased) to (1f - eased)
}

/** Centro del ojo de pez, franja a tamaño completo y distancia a la que la celda desaparece. */
private const val LENS_CENTER_Y = 100f
private const val LENS_FLAT = 26f
private const val LENS_EDGE = 78f

/** Altura de la primera fila sin desplazar y hasta dónde puede subir la última (sobre 192). */
private const val FIRST_ROW_Y = 60f
private const val LAST_ROW_Y = 140f

/**
 * Celda hexagonal con la punta arriba. Encendida: vidrio con borde blanco al 45 % y
 * texto en vino. Apagada: contorno punteado en vino, icono al 45 % tachado en diagonal.
 * El punto de arriba a la derecha es el color de su nivel (borde del color del fondo);
 * las de peligro llevan además un candado arriba a la izquierda y no se apagan.
 */
@Composable
private fun HexCell(cell: SoundCell, onToggle: () -> Unit, scale: WatchScale, modifier: Modifier = Modifier) = with(scale) {
    val width = (HEX_RADIUS * SQRT3).mdp
    val height = (HEX_RADIUS * 2).mdp
    val name = SoundUi.shortName(cell.category)
    Box(
        modifier
            .size(width, height)
            .clip(HexShape)
            .then(if (cell.enabled) Modifier.background(SaColors.Glass).border(0.75.dp, SaColors.GlassBorder, HexShape) else Modifier)
            // Los toques los reparte el panal (celda más cercana); aquí solo la semántica.
            .semantics {
                contentDescription = "$name, ${cell.priority.label.lowercase()}"
                if (cell.locked) {
                    stateDescription = "siempre activo"
                } else {
                    role = Role.Switch
                    toggleableState = ToggleableState(cell.enabled)
                    stateDescription = if (cell.enabled) "activado" else "desactivado"
                }
                onClick(label = if (cell.locked) null else if (cell.enabled) "Desactivar" else "Activar") { onToggle(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!cell.enabled) {
            Canvas(Modifier.fillMaxSize()) {
                drawPath(
                    hexPath(size),
                    SaColors.Wine.copy(alpha = 0.45f),
                    style = Stroke(0.9.mdp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4.mdp.toPx(), 1.8.mdp.toPx()))),
                )
            }
        }
        // Icono con su centro 4,3 por encima del centro de la celda y nombre 8,7 por debajo.
        Box(Modifier.offset(y = (-4.3).mdp), contentAlignment = Alignment.Center) {
            GlyphIcon(
                SoundUi.icon(cell.category),
                glyphHeight = 8.6.mdp,
                maxGlyphWidth = 10.6.mdp,
                tint = if (cell.enabled) SaColors.Wine else SaColors.Wine,
                modifier = Modifier.alpha(if (cell.enabled) 1f else 0.45f),
            )
            if (!cell.enabled) {
                // Línea diagonal que tacha el icono apagado.
                Canvas(Modifier.size(11.5.mdp)) {
                    drawLine(SaColors.Wine, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 0.9.mdp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        // El nombre apagado va en vino sólido (7,1:1), no atenuado: el texto debe cumplir 4,5:1.
        FitText(name, SaText.cell.copy(fontSize = 5.5.sp, lineHeight = 6.2.sp), if (cell.enabled) SaColors.Wine else SaColors.Wine, Modifier.offset(y = 8.7.mdp).width(width * 0.8f), maxLines = 2)
        // Punto del nivel en la esquina superior derecha (centro a +10,7, −15,7 del centro).
        LevelDot(cell, scale, Modifier.align(Alignment.TopStart).offset(x = (width / 2 + 10.7.mdp - 3.25.mdp), y = (height / 2 - 15.7.mdp - 3.25.mdp)))
        if (cell.locked) {
            // Candado en lugar de la opción de apagar: peligro siempre activo (simétrico al punto).
            GlyphIcon(
                R.drawable.ic_lock,
                glyphHeight = 3.6.mdp,
                tint = SaColors.Wine,
                modifier = Modifier.align(Alignment.TopStart).offset(x = (width / 2 - 10.7.mdp - 1.45.mdp), y = (height / 2 - 15.7.mdp - 1.8.mdp)),
            )
        }
    }
}

/** Toast al tocar un sonido de peligro. */
const val LOCKED_TOAST = "Siempre activo por seguridad"

@Composable
private fun LevelDot(cell: SoundCell, scale: WatchScale, modifier: Modifier) = with(scale) {
    Box(
        modifier
            .size(6.5.mdp)
            .clip(CircleShape)
            .background(SaColors.Background)
            .padding(1.mdp)
            .clip(CircleShape)
            // Apagada: el color del nivel atenuado, como en el mockup.
            .background(if (cell.enabled) cell.priority.color else cell.priority.color.copy(alpha = 0.45f).compositeOver(SaColors.Background)),
    )
}

/** Hexágono con la punta arriba que llena su caja (ancho = √3 · radio, alto = 2 · radio). */
private val HexShape = GenericShape { size, _ -> addPath(hexPath(size)) }

private fun hexPath(size: Size) = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w / 2, 0f)
    lineTo(w, h / 4)
    lineTo(w, h * 3 / 4)
    lineTo(w / 2, h)
    lineTo(0f, h * 3 / 4)
    lineTo(0f, h / 4)
    close()
}

/** Centro de una celda respecto a la primera fila del panal, en dp del diseño (sobre 192). */
data class HexSlot(val x: Float, val y: Float)

/**
 * Posiciones de [count] celdas en un panal continuo de hexágonos con la punta arriba, de
 * radio [radius] y separados [gap]: filas alternas de 2 y 3 encajadas, centradas en x y
 * empezando en y = 0.
 */
fun honeycombSlots(count: Int, radius: Float, gap: Float): List<HexSlot> {
    val dx = radius * SQRT3 + gap
    val dy = radius * 1.5f + gap * SQRT3 / 2
    val slots = ArrayList<HexSlot>(count)
    var row = 0
    while (slots.size < count) {
        val inRow = if (row % 2 == 0) 2 else 3
        val xs = if (inRow == 2) listOf(-dx / 2, dx / 2) else listOf(-dx, 0f, dx)
        for (x in xs) if (slots.size < count) slots += HexSlot(x, row * dy)
        row++
    }
    return slots
}

/**
 * Radio de las celdas y hueco entre ellas, sobre 192: celdas un 10 % mayores que en el
 * mockup (41,2 × 47,5), con centros vecinos a 44 y 3 de separación. Las filas de 3 miden
 * 130 de ancho y caben dentro del margen de seguridad.
 */
const val HEX_RADIUS = 23.76f
const val HEX_GAP = 3f


private val SQRT3 = sqrt(3f)
