package com.soundalert.wear.context

import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente del contexto activo durante la escucha. El pipeline (ContextualClassifier)
 * solo depende de esta interfaz, así que el origen se puede cambiar sin tocar la
 * clasificación del sonido ni las reglas.
 *
 * Hoy: [ContextManager], contexto explícito (lo fija la app o un test).
 * Más adelante: un proveedor alimentado por ubicación/movimiento implementará esta
 * misma interfaz. Siempre debe entregar un contexto ACTIVO (CASA, CALLE u OTRO).
 */
interface ActiveContextProvider {
    val activeContext: StateFlow<SoundAlertContext>

    val current: SoundAlertContext get() = activeContext.value
}
