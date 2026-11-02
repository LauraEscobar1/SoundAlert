package com.soundalert.wear

import android.app.Application

/**
 * Se ejecuta al arrancar el proceso, antes que cualquier Activity o Service:
 * prepara SoundAlertRuntime para que el contexto elegido se restaure siempre
 * desde el almacenamiento, también tras un reinicio del proceso, y empieza a
 * agrupar la escucha en sesiones.
 */
class SoundAlertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoundAlertRuntime.init(this)
        SoundAlertRuntime.startSessions(this)
    }
}
