package com.soundalert.wear.audio

/**
 * Buffer circular de muestras PCM16. Guarda siempre el audio más reciente
 * y permite copiar las últimas N muestras en orden cronológico.
 * No es thread-safe: lo usa solo el hilo de captura.
 */
class AudioRingBuffer(val capacity: Int) {
    private val data = ShortArray(capacity)
    private var writePos = 0

    /** Muestras escritas desde el inicio (no se reinicia al dar la vuelta). */
    var totalWritten: Long = 0
        private set

    val available: Int get() = minOf(totalWritten, capacity.toLong()).toInt()

    fun write(samples: ShortArray, count: Int = samples.size) {
        require(count in 0..samples.size)
        // Si llega más de lo que cabe, solo importan las últimas `capacity` muestras.
        val skip = maxOf(0, count - capacity)
        var i = skip
        while (i < count) {
            val n = minOf(count - i, capacity - writePos)
            System.arraycopy(samples, i, data, writePos, n)
            writePos = (writePos + n) % capacity
            i += n
        }
        totalWritten += count
    }

    /** Copia las últimas `dest.size` muestras. Devuelve false si aún no hay suficientes. */
    fun copyLatest(dest: ShortArray): Boolean {
        val n = dest.size
        require(n <= capacity)
        if (available < n) return false
        val start = (writePos - n + capacity) % capacity
        val first = minOf(n, capacity - start)
        System.arraycopy(data, start, dest, 0, first)
        if (first < n) System.arraycopy(data, 0, dest, first, n - first)
        return true
    }
}
