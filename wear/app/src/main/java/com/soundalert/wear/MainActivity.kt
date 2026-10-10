package com.soundalert.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soundalert.wear.ui.WearApp
import com.soundalert.wear.ui.theme.SoundAlertTheme

/**
 * Interfaz del reloj (pantallas de los mockups, ver ui/WearApp.kt). Activar la escucha
 * debe hacerse aquí: Android solo concede el micrófono a un foreground service
 * iniciado con la app visible.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sincronización con el backend: secundaria y en segundo plano; nunca bloquea la UI ni la detección.
        SoundAlertRuntime.startBackendSync(this)
        setContent { SoundAlertTheme { WearApp() } }
    }
}
