package com.soundalert.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.soundalert.wear.BuildConfig
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.ui.alert.AlertScreen
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.context.ContextScreen
import com.soundalert.wear.ui.diagnostic.DiagnosticScreen
import com.soundalert.wear.ui.home.HomeScreen
import com.soundalert.wear.ui.sessions.SessionClockScreen
import com.soundalert.wear.ui.sessions.SessionDetailScreen
import com.soundalert.wear.ui.sounds.SoundsScreen
import com.soundalert.wear.ui.state.alertOnScreen
import com.soundalert.wear.ui.theme.SaColors

private object Routes {
    const val HOME = "home"
    const val CONTEXT = "context"
    const val DIAGNOSTIC = "diagnostic"
    const val SESSION = "session/{id}"

    fun session(id: String) = "session/$id"
}

/**
 * Navegación de reloj:
 * - Inicio: páginas horizontales  A/B · Escuchando  →  G · Sonidos  →  H · Reloj de sesiones
 *   (indicador de página abajo; se desliza con el dedo).
 * - F · Contexto: tocando el chip del contexto. Se vuelve deslizando hacia la derecha.
 * - I · Detalle de sesión: tocando la esfera de H. Se vuelve deslizando hacia la derecha.
 * - C/D/E · Alertas: aparecen encima de cualquier pantalla mientras haya una alerta ACTIVE.
 * - Modo ambiente: se muestra el reposo atenuado, salvo que haya una alerta.
 */
@Composable
fun WearApp() {
    val android = LocalContext.current
    val alertManager = remember { SoundAlertRuntime.alertManager(android) }
    val alerts by alertManager.alerts.collectAsStateWithLifecycle()
    val onScreen = alertOnScreen(alerts)
    val navController = rememberSwipeDismissableNavController()

    AppScaffold(timeText = {}) {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomePager(
                    onOpenContext = { navController.navigate(Routes.CONTEXT) },
                    onOpenDiagnostic = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.DIAGNOSTIC) }) else null,
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.CONTEXT) { ContextScreen(onSelected = { navController.popBackStack() }) }
            composable(Routes.SESSION) { entry ->
                entry.arguments?.getString("id")?.let { SessionDetailScreen(it) }
            }
            composable(Routes.DIAGNOSTIC) { DiagnosticScreen() }
        }
        if (onScreen != null) {
            key(onScreen.id) {
                AlertScreen(onScreen, SoundAlertRuntime.alertConfig, onAcknowledge = { alertManager.acknowledge(onScreen.id) })
            }
        } else if (LocalAmbient.current) {
            // Always-on: se ve el reposo (sin animación y en oscuro) desde cualquier pantalla.
            HomeScreen(onOpenContext = {}, onOpenDiagnostic = null)
        }
    }
}

@Composable
private fun HomePager(onOpenContext: () -> Unit, onOpenDiagnostic: (() -> Unit)?, onOpenSession: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES })
    // Indicador en tinta sobre el durazno (el de serie es claro y no se ve).
    HorizontalPagerScaffold(
        pagerState = pagerState,
        pageIndicator = {
            HorizontalPageIndicator(pagerState, selectedColor = SaColors.Ink, unselectedColor = SaColors.Brown.copy(alpha = 0.4f), backgroundColor = Color.Transparent)
        },
    ) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    0 -> HomeScreen(onOpenContext, onOpenDiagnostic)
                    1 -> SoundsScreen(visible = pagerState.currentPage == page)
                    else -> SessionClockScreen(visible = pagerState.currentPage == page, onOpen = onOpenSession)
                }
            }
        }
    }
}

private const val PAGES = 3
