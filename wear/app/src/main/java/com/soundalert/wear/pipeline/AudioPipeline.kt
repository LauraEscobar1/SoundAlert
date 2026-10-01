package com.soundalert.wear.pipeline

import android.util.Log
import com.soundalert.wear.audio.AudioPreprocessor
import com.soundalert.wear.audio.AudioRingBuffer
import com.soundalert.wear.audio.AudioSource
import com.soundalert.wear.audio.EnergyGate
import com.soundalert.wear.classifier.LabelMapper
import com.soundalert.wear.classifier.SoundClassifier
import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.detection.DetectionStabilizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Micrófono → buffer circular → puerta de energía → YAMNet → categorías →
 * estabilizador → eventos.
 *
 * Dos hilos: la captura nunca espera a la IA. Entre ambos hay un canal de
 * capacidad 2 que descarta lo más antiguo: si la inferencia se retrasa se
 * pierden ventanas viejas (se cuentan en `droppedWindows`), no se acumula memoria.
 */
class AudioPipeline(
    private val source: AudioSource,
    private val classifier: SoundClassifier,
    private val mapper: LabelMapper,
    private val config: PipelineConfig,
    private val captureDispatcher: CoroutineDispatcher,
    private val inferenceDispatcher: CoroutineDispatcher,
    private val onEvent: (DetectionEvent) -> Unit = {},
) {
    /** Una ventana para inferir (o null si la puerta decidió no inferir). */
    private class Work(val audioTimeMs: Long, val window: ShortArray?)

    suspend fun run() = coroutineScope {
        require(source.sampleRate == config.sampleRate) { "Frecuencia de la fuente distinta a la del modelo" }
        require(classifier.inputSamples == config.windowSamples) { "La ventana no coincide con la entrada del modelo" }

        var dropped = 0L
        val work = Channel<Work>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST) {
            if (it.window != null) dropped++
        }

        launch(inferenceDispatcher) { infer(work, droppedCount = { dropped }) }

        withContext(captureDispatcher) {
            val ring = AudioRingBuffer(config.ringSamples)
            val gate = EnergyGate(config.gate, config.hopMs)
            val windowSamples = ShortArray(config.windowSamples)
            var blocks = 0L
            try {
                source.blocks(config.hopSamples).collect { block ->
                    ring.write(block)
                    blocks++
                    val audioTimeMs = ring.totalWritten * 1_000 / config.sampleRate
                    val decision = gate.evaluate(block, block.size, audioTimeMs)
                    val window = if (decision.infer && ring.copyLatest(windowSamples)) windowSamples.copyOf() else null
                    Log.d(
                        GATE,
                        String.format(Locale.US, "t=%.1fs nivel=%.1f dBFS fondo=%.1f dBFS → %s", audioTimeMs / 1000.0, decision.dbfs, decision.noiseFloorDbfs, decision.reason),
                    )
                    PipelineStatus.update { it.copy(dbfs = decision.dbfs, noiseFloorDbfs = decision.noiseFloorDbfs, blocks = blocks) }
                    work.send(Work(audioTimeMs, window))
                }
            } finally {
                work.close()
            }
        }
    }

    private suspend fun infer(work: Channel<Work>, droppedCount: () -> Long) {
        val stabilizer = DetectionStabilizer(config.stabilizer, config.hopMs)
        val floats = FloatArray(config.windowSamples)
        var inferences = 0L
        var totalMs = 0.0

        for (item in work) {
            val categories = if (item.window != null) {
                AudioPreprocessor.toFloat(item.window, floats)
                val start = System.nanoTime()
                val scores = classifier.classify(floats)
                val ms = (System.nanoTime() - start) / 1e6
                inferences++
                totalMs += ms

                val top = mapper.topLabels(scores, 3)
                val mapped = mapper.map(scores)
                Log.i(
                    YAMNET,
                    String.format(Locale.US, "t=%.1fs %.1f ms top3=[%s] categoría=%s", item.audioTimeMs / 1000.0, ms, top.joinToString { "${it.label} ${fmt(it.score)}" }, mapped.firstOrNull()?.let { "${it.category} ${fmt(it.score)}" } ?: "-"),
                )
                PipelineStatus.update { it.copy(topLabels = top, inferences = inferences, avgInferenceMs = totalMs / inferences, droppedWindows = droppedCount()) }
                mapped
            } else {
                emptyList()
            }

            for (event in stabilizer.update(categories, item.audioTimeMs)) {
                Log.i(EVENT, describe(event))
                PipelineStatus.update {
                    it.copy(lastEvent = event, lastEventWallClockMs = System.currentTimeMillis(), activeCategories = stabilizer.activeCategories)
                }
                onEvent(event)
            }
        }
    }

    companion object {
        const val GATE = "SA/Gate"
        const val YAMNET = "SA/YAMNet"
        const val EVENT = "SA/Event"

        private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

        fun describe(event: DetectionEvent): String = when (event) {
            is DetectionEvent.Started ->
                "START ${event.category} confidence=${fmt(event.confidence)} label=\"${event.label}\"" +
                    (if (event.fastPath) " (vía rápida)" else "") + " t=${event.atMs / 1000.0}s"
            is DetectionEvent.Ended ->
                "END ${event.category} peak=${fmt(event.peakConfidence)} duration=${event.durationMs / 1000.0}s t=${event.atMs / 1000.0}s"
        }
    }
}
