package com.soundalert.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.wear.ambient.AmbientLifecycleObserver
import com.soundalert.wear.ui.WearApp
import com.soundalert.wear.ui.components.LocalAmbient
import com.soundalert.wear.ui.theme.SoundAlertTheme

/**
 * Interfaz del reloj (pantallas del diseño, ver ui/WearApp.kt). Activar la escucha
 * debe hacerse aquí: Android solo concede el micrófono a un foreground service
 * iniciado con la app visible.
 *
 * Modo ambiente (always-on): la app sigue visible con la pantalla atenuada; las
 * pantallas lo leen de [LocalAmbient] para quitar animaciones y oscurecer el fondo.
 */
class MainActivity : ComponentActivity() {

    private val ambient = mutableStateOf(false)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.value = true
            }

            override fun onExitAmbient() {
                ambient.value = false
            }

            override fun onUpdateAmbient() = Unit
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        // Sincronización con el backend: secundaria y en segundo plano; nunca bloquea la UI ni la detección.
        SoundAlertRuntime.startBackendSync(this)
        setContent {
            CompositionLocalProvider(LocalAmbient provides ambient.value) {
                SoundAlertTheme { WearApp() }
            }
        }
    }
}
