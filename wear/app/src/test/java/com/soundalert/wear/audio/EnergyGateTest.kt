package com.soundalert.wear.audio

import com.soundalert.wear.audio.EnergyGate.Reason
import com.soundalert.wear.config.GateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EnergyGateTest {

    private val blockMs = 500L
    private fun gate() = EnergyGate(GateConfig(), blockMs)

    /** Tono de 1 kHz con la amplitud indicada (0-1). */
    private fun tone(amplitude: Double, n: Int = 8_000) =
        ShortArray(n) { (amplitude * 32767 * sin(2 * PI * 1000 * it / 16_000)).toInt().toShort() }

    @Test
    fun `dBFS de referencia`() {
        assertEquals(EnergyGate.SILENCE_DBFS, EnergyGate.rmsDbfs(ShortArray(100)), 0f)
        // Una senoidal a fondo de escala tiene RMS de 1/sqrt(2), unos -3 dBFS.
        assertEquals(-3f, EnergyGate.rmsDbfs(tone(1.0)), 0.1f)
        assertEquals(-23f, EnergyGate.rmsDbfs(tone(0.1)), 0.1f)
    }

    @Test
    fun `silencio no infiere salvo la inferencia periodica`() {
        val g = gate()
        val silence = ShortArray(8_000)
        val reasons = (0 until 20).map { g.evaluate(silence, silence.size, it * blockMs).reason }
        // t=0 cuenta como periódica (nunca se infirió); luego cada 5 s.
        assertEquals(Reason.PERIODIC, reasons[0])
        assertEquals(Reason.PERIODIC, reasons[10])
        assertEquals(18, reasons.count { it == Reason.QUIET })
    }

    @Test
    fun `un sonido claramente por encima del fondo infiere y luego hay margen de 3 s`() {
        val g = gate()
        val quiet = tone(0.001) // ≈ -63 dBFS de ruido de fondo
        var t = 0L
        repeat(10) { g.evaluate(quiet, quiet.size, t); t += blockMs }

        val loud = g.evaluate(tone(0.3), 8_000, t)
        assertEquals(Reason.LOUD, loud.reason)
        t += blockMs

        val after = (1..8).map { g.evaluate(quiet, quiet.size, t).also { t += blockMs }.reason }
        assertEquals(List(6) { Reason.HANGOVER }, after.take(6)) // 500 ms × 6 = 3 s
        assertTrue(after.drop(6).all { it != Reason.LOUD && it != Reason.HANGOVER })
    }

    @Test
    fun `en un entorno ruidoso solo cuenta lo que supera el fondo`() {
        val g = gate()
        val street = tone(0.05) // ≈ -29 dBFS de fondo constante
        var t = 0L
        repeat(120) { g.evaluate(street, street.size, t); t += blockMs } // 60 s
        val floor = g.noiseFloorDbfs
        assertEquals(-29f, floor, 1f)
        // Mismo nivel que el fondo: no es "fuerte".
        assertTrue(g.evaluate(street, street.size, t).reason != Reason.LOUD)
        t += 10_000
        // +12 dB sobre el fondo: sí.
        assertEquals(Reason.LOUD, g.evaluate(tone(0.2), 8_000, t).reason)
    }

    @Test
    fun `un sonido fuerte y largo no sube el ruido de fondo`() {
        val g = gate()
        val quiet = tone(0.001)
        var t = 0L
        repeat(4) { g.evaluate(quiet, quiet.size, t); t += blockMs }
        val before = g.noiseFloorDbfs
        repeat(60) { g.evaluate(tone(0.3), 8_000, t); t += blockMs } // 30 s de sirena
        assertEquals(before, g.noiseFloorDbfs, 0.01f)
    }
}
