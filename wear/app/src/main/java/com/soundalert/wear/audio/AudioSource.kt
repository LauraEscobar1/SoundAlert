package com.soundalert.wear.audio

import kotlinx.coroutines.flow.Flow

/**
 * Origen de audio PCM16 mono. El pipeline no sabe si viene del micrófono
 * o de un archivo (útil para pruebas reproducibles).
 */
interface AudioSource {
    val sampleRate: Int

    /** Emite bloques de exactamente [blockSamples] muestras hasta que se cancela. */
    fun blocks(blockSamples: Int): Flow<ShortArray>
}
