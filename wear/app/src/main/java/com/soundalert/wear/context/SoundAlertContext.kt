package com.soundalert.wear.context

/**
 * Entorno en el que está la persona. No clasifica sonidos: YAMNet dice QUÉ
 * suena; el contexto dice DÓNDE está la persona, y con eso las reglas deciden
 * cuánto importa ese sonido.
 *
 * v1: lo elige el usuario a mano. La inferencia automática (ubicación,
 * movimiento) queda para una fase posterior.
 */
enum class SoundAlertContext(val label: String) {
    CALLE("Calle"),
    CASA("Casa"),
    TRABAJO("Trabajo"),
    TRANSPORTE("Transporte"),
    OTRO("Otro"),
}
