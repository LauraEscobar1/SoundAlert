package com.soundalert.wear.context

/**
 * Entorno en el que está la persona. NO se obtiene del sonido: YAMNet dice QUÉ
 * suena; el contexto dice DÓNDE está la persona, y con ambos las reglas deciden
 * cuánto importa ese sonido.
 *
 * Única fuente de verdad de los contextos del reloj.
 *  - [active]: solo CASA, CALLE y OTRO pueden ser el contexto activo ([ACTIVE]).
 *    TRABAJO y TRANSPORTE se conservan (con sus reglas) pero están inactivos.
 *  - [apiCode]: código equivalente en el backend (UserContext): es el único punto
 *    de traducción reloj ↔ API. TRANSPORTE no existe en el backend.
 */
enum class SoundAlertContext(val label: String, val active: Boolean, val apiCode: String?) {
    CALLE("Calle", active = true, apiCode = "STREET"),
    CASA("Casa", active = true, apiCode = "HOME"),
    TRABAJO("Trabajo", active = false, apiCode = "WORK"),
    TRANSPORTE("Transporte", active = false, apiCode = null),
    OTRO("Otro", active = true, apiCode = "OTHER"),
    ;

    companion object {
        /** Contextos activos, en el orden en que se presentan. */
        val ACTIVE: List<SoundAlertContext> = listOf(CASA, CALLE, OTRO)

        /**
         * Contexto cuando no hay uno explícito. OTRO vigila todos los sonidos con
         * su prioridad por defecto: es el fallback seguro.
         */
        val DEFAULT: SoundAlertContext = OTRO

        /** Contexto activo correspondiente a un código de la API, o null si no es activo. */
        fun fromApiCode(code: String): SoundAlertContext? = ACTIVE.firstOrNull { it.apiCode == code }
    }
}
