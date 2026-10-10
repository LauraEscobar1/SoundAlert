package com.soundalert.wear.sync

import android.content.Context

/**
 * Id que el backend asignó a ESTE reloj (POST /devices). Se registra una sola vez
 * y se reutiliza siempre; solo se borra si el backend dice que ya no existe.
 */
interface DeviceStore {
    fun load(): String?
    fun save(deviceId: String)
    fun clear()
}

/** Implementación real: SharedPreferences privadas de la app (igual que ContextStore). */
class SharedPreferencesDeviceStore(context: Context, prefsName: String = PREFS_NAME) : DeviceStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    /** commit (síncrono): un id perdido haría registrar el reloj otra vez. */
    override fun save(deviceId: String) {
        prefs.edit().putString(KEY, deviceId).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    private companion object {
        const val PREFS_NAME = "soundalert_device"
        const val KEY = "device_id"
    }
}
