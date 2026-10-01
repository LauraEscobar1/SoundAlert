package com.soundalert.wear.pipeline

import com.soundalert.wear.classifier.LabelScore
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.detection.DetectionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Estado observable del pipeline (lo muestra la pantalla de diagnóstico). */
object PipelineStatus {

    enum class Phase { IDLE, STARTING, LISTENING, SILENCED, ERROR }

    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val error: String? = null,
        val dbfs: Float = Float.NaN,
        val noiseFloorDbfs: Float = Float.NaN,
        val topLabels: List<LabelScore> = emptyList(),
        val lastEvent: DetectionEvent? = null,
        val lastEventWallClockMs: Long = 0,
        val activeCategories: Set<SoundCategory> = emptySet(),
        val blocks: Long = 0,
        val inferences: Long = 0,
        val droppedWindows: Long = 0,
        val avgInferenceMs: Double = 0.0,
    )

    private val state = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = state

    fun update(transform: (Snapshot) -> Snapshot) = state.update(transform)
    fun reset(phase: Phase = Phase.IDLE, error: String? = null) = state.update { Snapshot(phase = phase, error = error) }
}
