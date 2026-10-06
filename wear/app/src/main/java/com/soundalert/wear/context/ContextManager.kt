package com.soundalert.wear.context

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contexto activo explícito: única fuente de verdad del contexto durante la escucha.
 * El ContextualClassifier lo lee al confirmar cada detección, así que un cambio se
 * aplica al siguiente sonido sin reiniciar el micrófono ni YAMNet.
 *
 * Solo acepta contextos activos (CASA, CALLE, OTRO).
 *
 * Con [store], el contexto elegido se guarda y se restaura al crear el
 * ContextManager (al arrancar el proceso). Sin esto, cualquier reinicio del proceso
 * volvía en silencio a [initial] (OTRO) aunque el usuario hubiera elegido CASA o CALLE.
 * [initial] solo se usa si no hay nada guardado o lo guardado no es un contexto activo.
 */
class ContextManager(
    initial: SoundAlertContext = SoundAlertContext.DEFAULT,
    private val store: ContextStore? = null,
) : ActiveContextProvider {

    init {
        require(initial.active) { "Contexto inicial no activo: $initial" }
    }

    private val state = MutableStateFlow(restore(initial))
    override val activeContext: StateFlow<SoundAlertContext> = state

    /** Alias de [activeContext] (lo observa la pantalla de diagnóstico). */
    val context: StateFlow<SoundAlertContext> get() = state

    fun set(context: SoundAlertContext) {
        require(context.active) { "$context no es un contexto activo (${SoundAlertContext.ACTIVE.joinToString()})" }
        val previous = state.value
        state.value = context
        store?.save(context.name)
        if (previous != context) Log.i(TAG, "contexto actual=$context (antes $previous)")
    }

    private fun restore(fallback: SoundAlertContext): SoundAlertContext {
        val saved = store?.load() ?: return fallback
        val context = SoundAlertContext.entries.firstOrNull { it.name == saved }
        return if (context != null && context.active) {
            Log.i(TAG, "contexto restaurado=$context")
            context
        } else {
            Log.w(TAG, "contexto guardado no válido ($saved): se usa $fallback")
            fallback
        }
    }

    companion object {
        /** Igual que [SoundAlertContext.DEFAULT] (se mantiene por compatibilidad). */
        val DEFAULT = SoundAlertContext.DEFAULT
        private const val TAG = "SA/Context"
    }
}
