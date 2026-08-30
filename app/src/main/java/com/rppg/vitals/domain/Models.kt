package com.rppg.vitals.domain

/**
 * Represents all the vital measurements produced by the rPPG pipeline.
 * Structured for future extensibility (respiratory rate, HRV, etc.)
 */
data class VitalsResult(
    val heartRate: Double,           // BPM
    val signalQuality: SignalQuality,
    val timestamp: Long = System.currentTimeMillis(),
    val measurementDurationMs: Long = 0,
    val method: String = "POS",
    // Future fields (optional, null when not computed)
    val respiratoryRate: Double? = null,
    val hrv: Double? = null
)

enum class SignalQuality {
    GOOD,
    FAIR,
    POOR,
    NO_SIGNAL;

    fun label(): String = when (this) {
        GOOD -> "GOOD"
        FAIR -> "FAIR"
        POOR -> "POOR"
        NO_SIGNAL -> "NO SIGNAL"
    }

    fun isDisplayable(): Boolean = this == GOOD || this == FAIR
}

/**
 * App measurement state machine.
 */
sealed class MeasurementState {
    object Idle : MeasurementState()
    object CameraStarting : MeasurementState()
    object NoFace : MeasurementState()
    data class FaceDetected(val faceRect: FaceRect) : MeasurementState()
    data class Collecting(
        val faceRect: FaceRect,
        val elapsedMs: Long,
        val targetMs: Long,
        val pulseSignal: List<Float>
    ) : MeasurementState()
    data class SignalWeak(val faceRect: FaceRect, val reason: String) : MeasurementState()
    data class Analyzing(val faceRect: FaceRect, val pulseSignal: List<Float>) : MeasurementState()
    data class Measuring(
        val faceRect: FaceRect,
        val bpm: Double,
        val signalQuality: SignalQuality,
        val elapsedMs: Long,
        val targetMs: Long,
        val pulseSignal: List<Float>
    ) : MeasurementState()
    data class Result(val result: VitalsResult, val pulseSignal: List<Float>) : MeasurementState()
    data class Error(val message: String) : MeasurementState()
}

/**
 * Normalized face rectangle coordinates [0,1].
 */
data class FaceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val trackingId: Int? = null
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * A single spatial-average RGB sample from the facial ROI.
 */
data class RgbSample(
    val r: Double,
    val g: Double,
    val b: Double,
    val timestampMs: Long
)
