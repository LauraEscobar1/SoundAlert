package com.soundalert.wear.context

import android.content.Context

/**
 * Dónde se guarda el contexto elegido para que sobreviva a un reinicio del proceso
 * (Android lo mata en segundo plano, reinstalación, reinicio del reloj…). Guarda el
 * nombre tal cual; ContextManager valida que sea un contexto activo.
 */
interface ContextStore {
    fun load(): String?
    fun save(name: String)
}

/** Implementación real: SharedPreferences privadas de la app. */
class SharedPreferencesContextStore(context: Context, prefsName: String = PREFS_NAME) : ContextStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    /** commit (síncrono): el contexto debe quedar escrito aunque el proceso muera justo después. */
    override fun save(name: String) {
        prefs.edit().putString(KEY, name).commit()
    }

    private companion object {
        const val PREFS_NAME = "soundalert_context"
        const val KEY = "active_context"
    }
}
