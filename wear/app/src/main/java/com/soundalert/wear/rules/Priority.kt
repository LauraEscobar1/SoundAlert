package com.soundalert.wear.rules

/** Nivel de una alerta. Determina la vibración y si la alerta se cierra sola. */
enum class Priority {
    /** Sirenas, alarmas: 3 vibraciones largas, persiste hasta que el usuario confirma. */
    DANGER,

    /** Bocinas, bicicletas: 2 vibraciones medias, se cierra sola a los 10 s. */
    ATTENTION,

    /** Timbre, golpe en la puerta: 1 vibración corta, se cierra sola a los 5 s. */
    INFORMATION,
}
