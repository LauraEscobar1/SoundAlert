package com.soundalert.wear.rules

import android.content.Context
import android.util.Log
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.context.SoundAlertContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sonidos que el usuario apagó en cada contexto (pantalla de sonidos). El RuleEngine
 * los consulta en cada detección: un sonido apagado no tiene regla en ese contexto y
 * por tanto no alerta ni vibra. Sigue detectándose y quedando en el historial.
 *
 * Solo se pueden apagar sonidos que tienen regla por defecto en el contexto; encender
 * uno lo devuelve a su prioridad por defecto. Se guarda por contexto en [store].
 */
class SoundToggles(private val store: SoundToggleStore? = null) {

    private val lock = Any()
    private val state = MutableStateFlow(restore())

    /** Sonidos apagados por contexto. */
    val disabled: StateFlow<Map<SoundAlertContext, Set<SoundCategory>>> = state

    fun isEnabled(context: SoundAlertContext, category: SoundCategory): Boolean = category !in state.value[context].orEmpty()

    fun setEnabled(context: SoundAlertContext, category: SoundCategory, enabled: Boolean) = synchronized(lock) {
        val current = state.value[context].orEmpty()
        val updated = if (enabled) current - category else current + category
        if (updated == current) return@synchronized
        state.value = state.value + (context to updated)
        store?.save(context.name, updated.map { it.name }.toSet())
        Log.i(TAG, "$category en $context -> ${if (enabled) "activado" else "desactivado"}")
    }

    private fun restore(): Map<SoundAlertContext, Set<SoundCategory>> {
        val saved = store?.load() ?: return emptyMap()
        return saved.mapNotNull { (contextName, names) ->
            val context = SoundAlertContext.entries.firstOrNull { it.name == contextName } ?: return@mapNotNull null
            context to names.mapNotNull { name -> SoundCategory.entries.firstOrNull { it.name == name } }.toSet()
        }.toMap()
    }

    private companion object {
        const val TAG = "SA/Rule"
    }
}

/** Dónde se guardan los sonidos apagados: nombre del contexto → nombres de las categorías. */
interface SoundToggleStore {
    fun load(): Map<String, Set<String>>
    fun save(context: String, disabled: Set<String>)
}

/** Implementación real: SharedPreferences privadas, una entrada por contexto. */
class SharedPreferencesSoundToggleStore(context: Context) : SoundToggleStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Map<String, Set<String>> =
        prefs.all.mapNotNull { (key, value) ->
            val names = (value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: return@mapNotNull null
            key.removePrefix(KEY_PREFIX).takeIf { key.startsWith(KEY_PREFIX) }?.let { it to names }
        }.toMap()

    /** commit (síncrono): lo que se apaga debe quedar escrito aunque el proceso muera justo después. */
    override fun save(context: String, disabled: Set<String>) {
        prefs.edit().putStringSet(KEY_PREFIX + context, disabled).commit()
    }

    private companion object {
        const val PREFS_NAME = "soundalert_sounds"
        const val KEY_PREFIX = "disabled_"
    }
}
