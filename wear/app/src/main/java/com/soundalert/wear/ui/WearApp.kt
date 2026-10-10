package com.soundalert.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.soundalert.wear.BuildConfig
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.ui.alert.AlertScreen
import com.soundalert.wear.ui.context.ContextScreen
import com.soundalert.wear.ui.diagnostic.DiagnosticScreen
import com.soundalert.wear.ui.history.HistoryScreen
import com.soundalert.wear.ui.home.HomeScreen
import com.soundalert.wear.ui.sounds.SoundsScreen
import com.soundalert.wear.ui.state.alertOnScreen

private object Routes {
    const val HOME = "home"
    const val CONTEXT = "context"
    const val DIAGNOSTIC = "diagnostic"
}

/**
 * Navegación de reloj:
 * - Inicio: páginas horizontales  A · Escuchando  →  E · Sonidos  →  F · Historial
 *   (indicador de página abajo; se desliza con el dedo).
 * - D · Contexto: tocando el chip del contexto. Se vuelve deslizando hacia la derecha.
 * - C · Alerta: aparece encima de cualquier pantalla mientras haya una alerta ACTIVE.
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
                )
            }
            composable(Routes.CONTEXT) { ContextScreen(onSelected = { navController.popBackStack() }) }
            composable(Routes.DIAGNOSTIC) { DiagnosticScreen() }
        }
        if (onScreen != null) {
            key(onScreen.id) {
                AlertScreen(onScreen, SoundAlertRuntime.alertConfig, onAcknowledge = { alertManager.acknowledge(onScreen.id) })
            }
        }
    }
}

@Composable
private fun HomePager(onOpenContext: () -> Unit, onOpenDiagnostic: (() -> Unit)?) {
    val pagerState = rememberPagerState(pageCount = { PAGES })
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    0 -> HomeScreen(onOpenContext, onOpenDiagnostic)
                    1 -> SoundsScreen()
                    else -> HistoryScreen(visible = pagerState.currentPage == page)
                }
            }
        }
    }
}

private const val PAGES = 3
