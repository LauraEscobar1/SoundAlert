package com.soundalert.wear.context

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contexto actual. El AlertManager lo consulta en cada evento, así que un
 * cambio se aplica al siguiente sonido sin reiniciar el micrófono ni YAMNet.
 */
class ContextManager(initial: SoundAlertContext = DEFAULT) {

    private val state = MutableStateFlow(initial)
    val context: StateFlow<SoundAlertContext> = state

    val current: SoundAlertContext get() = state.value

    fun set(context: SoundAlertContext) {
        val previous = state.value
        state.value = context
        if (previous != context) Log.i(TAG, "contexto actual=$context (antes $previous)")
    }

    companion object {
        /**
         * OTRO al arrancar: es el contexto que vigila todos los sonidos con su
         * prioridad por defecto, así no se pierde nada hasta que el usuario elija.
         */
        val DEFAULT = SoundAlertContext.OTRO
        private const val TAG = "SA/Context"
    }
}
