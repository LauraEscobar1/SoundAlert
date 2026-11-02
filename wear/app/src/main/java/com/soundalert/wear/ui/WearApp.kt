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
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.ui.alert.AlertScreen
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.context.ContextScreen
import com.soundalert.wear.ui.home.HomeScreen
import com.soundalert.wear.ui.sessions.SessionStackScreen
import com.soundalert.wear.ui.sessions.SessionDetailScreen
import com.soundalert.wear.ui.sounds.SoundsScreen
import com.soundalert.wear.ui.state.alertOnScreen

private object Routes {
    const val HOME = "home"
    const val CONTEXT = "context"
    const val SESSION = "session/{id}"

    fun session(id: String) = "session/$id"
}

/**
 * Navegación de reloj:
 * - Inicio: páginas horizontales  A/B · Escuchando  →  G · Sonidos  →  H · Pila de sesiones
 *   (indicador de página abajo; se desliza con el dedo).
 * - F · Contexto: tocando el botón del contexto en A o el encabezado de G. Se vuelve deslizando hacia la derecha.
 * - I · Detalle de sesión: tocando una tarjeta de sesión de H. Se vuelve deslizando hacia la derecha.
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
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.CONTEXT) { ContextScreen(onSelected = { navController.popBackStack() }) }
            composable(Routes.SESSION) { entry ->
                entry.arguments?.getString("id")?.let { SessionDetailScreen(it) }
            }
        }
        if (onScreen != null) {
            key(onScreen.id) {
                AlertScreen(onScreen, SoundAlertRuntime.alertConfig, onAcknowledge = { alertManager.acknowledge(onScreen.id) })
            }
        } else if (LocalAmbient.current) {
            // Always-on: se ve el reposo (sin animación y en oscuro) desde cualquier pantalla.
            HomeScreen(onOpenContext = null)
        }
    }
}

@Composable
private fun HomePager(onOpenContext: () -> Unit, onOpenSession: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES })
    // Sin indicador de página: se superponía al contenido, y el inicio solo muestra la hora,
    // el emblema y su estado.
    HorizontalPagerScaffold(pagerState = pagerState, pageIndicator = null) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    0 -> HomeScreen(onOpenContext)
                    1 -> SoundsScreen(visible = pagerState.currentPage == page, onOpenContext = onOpenContext)
                    else -> SessionStackScreen(visible = pagerState.currentPage == page, onOpen = onOpenSession)
                }
            }
        }
    }
}

private const val PAGES = 3
