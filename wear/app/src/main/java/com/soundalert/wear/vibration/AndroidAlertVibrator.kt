package com.soundalert.wear.vibration

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.soundalert.wear.rules.Priority

/**
 * Vibración real del reloj. Usa uso ALARM para DANGER y ATTENTION (son avisos
 * de seguridad y no deben atenuarse por los ajustes de vibración táctil) y
 * NOTIFICATION para INFORMATION. El comportamiento exacto con "No molestar"
 * depende de los ajustes del reloj.
 */
class AndroidAlertVibrator(context: Context) : AlertVibrator {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun vibrate(priority: Priority, pattern: VibrationPattern) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "El dispositivo no tiene vibrador; $priority pattern=${pattern.name} no se ejecuta")
            return
        }
        val effect = VibrationEffect.createWaveform(pattern.timings, -1)
        val alarm = priority != Priority.INFORMATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val usage = if (alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage))
        } else {
            val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
        }
        Log.i(TAG, "$priority pattern=${pattern.name} timings=${pattern.timings.contentToString()}")
    }

    override fun cancel() = vibrator.cancel()

    private companion object {
        const val TAG = "SA/Vibration"
    }
}
