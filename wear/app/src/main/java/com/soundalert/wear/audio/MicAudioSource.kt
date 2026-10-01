package com.soundalert.wear.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Micrófono real con AudioRecord: 16 kHz, mono, PCM 16 bits.
 *
 * Usa VOICE_RECOGNITION (sin control automático de ganancia ni supresión de
 * ruido en la mayoría de dispositivos: queremos el ruido ambiente) y, si no
 * está disponible, MIC. Avisa cuando Android silencia la captura (llamada,
 * asistente u otra app con prioridad sobre el micrófono).
 *
 * La lectura es bloqueante: el flow debe recogerse en un hilo dedicado.
 */
class MicAudioSource(
    private val context: Context,
    override val sampleRate: Int = 16_000,
    private val onSilencedChanged: (Boolean) -> Unit = {},
) : AudioSource {

    override fun blocks(blockSamples: Int): Flow<ShortArray> = flow {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Falta el permiso RECORD_AUDIO"
        }
        val record = open()
        val audioManager = context.getSystemService(AudioManager::class.java)
        var silenced = false
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId } ?: return
                if (mine.isClientSilenced != silenced) {
                    silenced = mine.isClientSilenced
                    Log.w(TAG, if (silenced) "Android ha SILENCIADO la captura (micrófono en uso por otra app o llamada)" else "Captura reanudada")
                    onSilencedChanged(silenced)
                }
            }
        }
        audioManager.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord no pudo empezar a grabar" }
            Log.i(TAG, "Grabando: fuente=${sourceName(record.audioSource)}, ${record.sampleRate} Hz, mono, PCM16, bloque=$blockSamples muestras")
            while (true) {
                currentCoroutineContext().ensureActive()
                val block = ShortArray(blockSamples)
                var offset = 0
                while (offset < blockSamples) {
                    val n = record.read(block, offset, blockSamples - offset, AudioRecord.READ_BLOCKING)
                    if (n < 0) throw IOException("AudioRecord.read devolvió $n")
                    offset += n
                }
                emit(block)
            }
        } finally {
            audioManager.unregisterAudioRecordingCallback(callback)
            runCatching { record.stop() }
            record.release()
            Log.i(TAG, "Micrófono liberado")
        }
    }

    @Suppress("MissingPermission") // comprobado en blocks()
    private fun open(): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "Formato no soportado por el dispositivo (getMinBufferSize=$minBytes)" }
        // 2 s de margen interno para no perder audio si el hilo se retrasa.
        val bufferBytes = maxOf(minBytes, sampleRate * 2 * 2)
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            Log.w(TAG, "No se pudo abrir la fuente ${sourceName(source)}; probando la siguiente")
            record.release()
        }
        throw IOException("No se pudo inicializar AudioRecord con ninguna fuente")
    }

    private fun sourceName(source: Int) = when (source) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> source.toString()
    }

    private companion object {
        const val TAG = "SA/Audio"
    }
}
