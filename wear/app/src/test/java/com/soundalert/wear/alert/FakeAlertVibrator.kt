package com.soundalert.wear.alert

import com.soundalert.wear.rules.Priority
import com.soundalert.wear.vibration.AlertVibrator
import com.soundalert.wear.vibration.VibrationPattern

/** Registra las vibraciones en lugar de usar el hardware. */
class FakeAlertVibrator : AlertVibrator {
    val vibrations = mutableListOf<Pair<Priority, VibrationPattern>>()
    var cancels = 0

    override fun vibrate(priority: Priority, pattern: VibrationPattern) {
        vibrations += priority to pattern
    }

    override fun cancel() {
        cancels++
    }
}
