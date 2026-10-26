package com.soundalert.wear.ui.sounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.soundalert.wear.ui.components.rotarySteps
import com.soundalert.wear.ui.icon
import com.soundalert.wear.ui.label
import com.soundalert.wear.ui.state.CELLS_PER_PAGE
import com.soundalert.wear.ui.state.SoundCell
import com.soundalert.wear.ui.state.soundCells
import com.soundalert.wear.ui.theme.SaColors
import com.soundalert.wear.ui.theme.SaText
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * G · Sonidos del contexto activo en un panal de hexágonos (filas de 2 – 3 – 2).
 *
 * Son las reglas reales del reloj para ese contexto. Tocar una celda la enciende o la
 * apaga (SoundToggles, guardado por contexto) y el RuleEngine lo aplica a la siguiente
 * detección. Los sonidos de peligro llevan un candado: siempre están activos y tocarlos
 * solo lo recuerda. Si hay más de 7 sonidos, la corona (o deslizar en vertical) pasa de página.
 */
@Composable
fun SoundsScreen(visible: Boolean) {
    val context by SoundAlertRuntime.contextManager.context.collectAsStateWithLifecycle()
    val disabled by SoundAlertRuntime.soundToggles.disabled.collectAsStateWithLifecycle()
    val cells = remember(context, disabled) { soundCells(context, SoundAlertRuntime.ruleEngine) }
    var page by rememberSaveable(context) { mutableIntStateOf(0) }
    val toast = rememberToastState()
    SoundsContent(
        context = context,
        cells = cells,
        page = page,
        onPage = { page = it },
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
    )
}

@Composable
fun SoundsContent(
    context: SoundAlertContext,
    cells: List<SoundCell>,
    page: Int,
    onPage: (Int) -> Unit,
    onToggle: (SoundCell) -> Unit,
    toast: ToastState,
    visible: Boolean = true,
) {
    val pages = cells.chunked(CELLS_PER_PAGE).ifEmpty { listOf(emptyList()) }
    val current = page.coerceIn(0, pages.lastIndex)
    val changePage = { delta: Int -> onPage((current + delta).coerceIn(0, pages.lastIndex)) }
    WatchScreen(
        Modifier
            .rotarySteps(active = visible) { changePage(it) }
            .pointerInput(pages.size) {
                var dragged = 0f
                detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = {
                    if (abs(dragged) > 24.dp.toPx()) changePage(if (dragged < 0) 1 else -1)
                }) { _, delta -> dragged += delta }
            },
    ) { scale ->
        with(scale) {
            // Encabezado (centro en y≈26,6 sobre 192): icono y nombre del contexto + "5 DE 7".
            Row(
                Modifier.centerAt(26.6.mdp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(context.icon, glyphHeight = 7.1.mdp, tint = SaColors.Ink, contentDescription = "Contexto")
                Spacer(Modifier.width(3.6.mdp))
                Text(context.label, style = SaText.screenTitle, color = SaColors.Ink, maxLines = 1)
                Spacer(Modifier.width(3.6.mdp))
                Text("${cells.count { it.enabled }} DE ${cells.size}", style = SaText.tag, color = SaColors.Brown, maxLines = 1)
            }

            val slots = honeycombSlots(HEX_RADIUS, HEX_GAP)
            val pageCells = pages[current]
            // Cada toque va a la celda de centro más cercano: cada una recibe toda su zona
            // hexagonal (44 dp entre lados opuestos, la distancia entre centros) sin pisar a
            // sus vecinas.
            Box(
                Modifier.fillMaxSize().pointerInput(pageCells) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f + HONEYCOMB_OFFSET_Y.mdp.toPx())
                        val nearest = pageCells.indices.minByOrNull { i ->
                            (Offset(slots[i].x.mdp.toPx(), slots[i].y.mdp.toPx()) + center - tap).getDistance()
                        }
                        if (nearest != null) {
                            val distance = (Offset(slots[nearest].x.mdp.toPx(), slots[nearest].y.mdp.toPx()) + center - tap).getDistance()
                            if (distance <= HEX_RADIUS.mdp.toPx()) onToggle(pageCells[nearest])
                        }
                    }
                },
            )
            pageCells.forEachIndexed { index, cell ->
                val slot = slots[index]
                HexCell(
                    cell,
                    onToggle = { onToggle(cell) },
                    scale = scale,
                    modifier = Modifier.align(Alignment.Center).offset(slot.x.mdp, (slot.y + HONEYCOMB_OFFSET_Y).mdp),
                )
            }

            // Pie en y≈163; el toast aparece en su lugar, dentro del margen de seguridad.
            if (toast.message == null) {
                val footer = if (pages.size > 1) "TOCA PARA ACTIVAR · ${current + 1}/${pages.size}" else "TOCA PARA ACTIVAR"
                Text(footer, style = SaText.footer, color = SaColors.Ink, maxLines = 1, modifier = Modifier.centerAt(166.mdp))
            }
            GlassToast(toast, scale, Modifier.centerAt(163.mdp))
        }
    }
}

/**
 * Celda hexagonal con la punta arriba. Encendida: vidrio con borde blanco al 45 % y
 * texto en tinta. Apagada: contorno punteado café, icono al 45 % tachado en diagonal.
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
                    SaColors.Brown.copy(alpha = 0.45f),
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
                tint = if (cell.enabled) SaColors.Ink else SaColors.Brown,
                modifier = Modifier.alpha(if (cell.enabled) 1f else 0.45f),
            )
            if (!cell.enabled) {
                // Línea diagonal que tacha el icono apagado.
                Canvas(Modifier.size(11.5.mdp)) {
                    drawLine(SaColors.Brown, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 0.9.mdp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        // El nombre apagado va en café sólido (7:1), no atenuado: el texto debe cumplir 4,5:1.
        FitText(name, SaText.cell.copy(fontSize = 5.5.sp, lineHeight = 6.2.sp), if (cell.enabled) SaColors.Ink else SaColors.Brown, Modifier.offset(y = 8.7.mdp).width(width * 0.8f), maxLines = 2)
        // Punto del nivel en la esquina superior derecha (centro a +10,7, −15,7 del centro).
        LevelDot(cell, scale, Modifier.align(Alignment.TopStart).offset(x = (width / 2 + 10.7.mdp - 3.25.mdp), y = (height / 2 - 15.7.mdp - 3.25.mdp)))
        if (cell.locked) {
            // Candado en lugar de la opción de apagar: peligro siempre activo (simétrico al punto).
            GlyphIcon(
                R.drawable.ic_lock,
                glyphHeight = 3.6.mdp,
                tint = SaColors.Ink,
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
            .background(SaColors.Peach)
            .padding(1.mdp)
            .clip(CircleShape)
            // Apagada: el color del nivel atenuado, como en el mockup.
            .background(if (cell.enabled) cell.priority.color else cell.priority.color.copy(alpha = 0.45f).compositeOver(SaColors.Peach)),
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

/** Centro de una celda respecto al centro del panal, en dp del diseño (sobre 192). */
data class HexSlot(val x: Float, val y: Float)

/**
 * Las 7 posiciones del panal 2 – 3 – 2 para hexágonos con la punta arriba de radio
 * [radius] separados [gap]: arriba dos, en el centro tres y abajo dos, encajados.
 */
fun honeycombSlots(radius: Float, gap: Float): List<HexSlot> {
    val dx = radius * SQRT3 + gap
    val dy = radius * 1.5f + gap * SQRT3 / 2
    return listOf(
        HexSlot(-dx / 2, -dy), HexSlot(dx / 2, -dy),
        HexSlot(-dx, 0f), HexSlot(0f, 0f), HexSlot(dx, 0f),
        HexSlot(-dx / 2, dy), HexSlot(dx / 2, dy),
    )
}

/**
 * Radio de las celdas y hueco entre ellas, sobre 192: celdas un 10 % mayores que en el
 * mockup (41,2 × 47,5), con centros vecinos a 44 y 3 de separación. El panal (130 × 124)
 * cabe entero, con 7 celdas, dentro del margen de seguridad.
 */
const val HEX_RADIUS = 23.76f
const val HEX_GAP = 3f

/** El panal baja un poco respecto al centro de la esfera para dejar sitio al encabezado. */
private const val HONEYCOMB_OFFSET_Y = 0.6f

private val SQRT3 = sqrt(3f)
