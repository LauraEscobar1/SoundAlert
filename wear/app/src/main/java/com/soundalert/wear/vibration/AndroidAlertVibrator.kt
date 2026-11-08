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
 * Vibración real del reloj. Los tres niveles usan uso ALARM, la vía con la que
 * DANGER ya se siente en el Galaxy Watch4: con NOTIFICATION, el sistema aplica la
 * intensidad de vibración de notificaciones (que puede estar baja o apagada) y
 * "No molestar", y AVISO no se sentía. Es una app para personas sordas: ningún
 * nivel puede depender de esos ajustes.
 *
 * DANGER conserva su efecto de siempre (amplitud por defecto). ATTENTION e
 * INFORMATION llevan amplitud explícita si el vibrador la soporta; si no, se usa la
 * amplitud por defecto con la misma duración (nunca un toque más corto).
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
            Log.w(TAG, "nivel=$priority patrón=$pattern aceptado=false motivo=sin vibrador")
            return
        }
        val amplitudeControl = vibrator.hasAmplitudeControl()
        val withAmplitudes = amplitudeControl && !pattern.usesDefaultAmplitude
        val effect = if (withAmplitudes) {
            VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(pattern.timings, -1)
        }
        val amplitude = if (withAmplitudes) pattern.pulseAmplitude.toString() else "DEFAULT"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        } catch (e: RuntimeException) {
            // IllegalArgumentException (efecto inválido) o SecurityException (sin permiso VIBRATE).
            Log.e(TAG, "nivel=$priority patrón=$pattern amplitud=$amplitude uso=ALARM aceptado=false", e)
            return
        }
        // vibrate() no devuelve resultado: "aceptado" significa que el sistema recibió el
        // efecto sin rechazarlo. Si aun así no se siente, ver `adb shell dumpsys vibrator_manager`.
        Log.i(
            TAG,
            "nivel=$priority patrón=$pattern amplitud=$amplitude uso=ALARM " +
                "controlAmplitud=$amplitudeControl aceptado=true",
        )
    }

    override fun cancel() = vibrator.cancel()

    private companion object {
        const val TAG = "SoundAlertVibe"
    }
}
