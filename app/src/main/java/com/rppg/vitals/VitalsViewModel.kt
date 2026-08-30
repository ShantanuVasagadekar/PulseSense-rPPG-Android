package com.rppg.vitals

import android.app.Application
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.rppg.vitals.camera.CameraController
import com.rppg.vitals.camera.CameraFrameResult
import com.rppg.vitals.domain.*
import com.rppg.vitals.face.FaceDetectionResult
import com.rppg.vitals.face.FaceRoiProcessor
import com.rppg.vitals.signal.PosRppgEngine
import com.rppg.vitals.signal.RgbSignalBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Central ViewModel orchestrating:
 *   Continuous 30 FPS Camera -> Face Detection -> Direct YUV-RGB ROI -> Rolling Buffer -> POS rPPG Engine -> UI State
 */
class VitalsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RPPG_DIAG"
        private const val TARGET_DURATION_MS = 25_000L   // 25-second measurement target
        private const val MIN_BPM_DURATION_MS = 6_000L   // Show stable preliminary BPM after 6 seconds
        private const val SIGNAL_UPDATE_INTERVAL_MS = 500L // 2 Hz signal analysis refresh
        private const val MAX_BUFFER_SAMPLES = 1000      // ~33 seconds at 30 fps
        private const val SIGNAL_PROCESS_WINDOW_S = 18   // Use last 18 seconds for POS analysis
    }

    // ──────────────────── State ────────────────────
    private val _measurementState = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
    val measurementState: StateFlow<MeasurementState> = _measurementState.asStateFlow()

    private val _pulseWaveform = MutableStateFlow<List<Float>>(emptyList())
    val pulseWaveform: StateFlow<List<Float>> = _pulseWaveform.asStateFlow()

    private val _latestDiagnostics = MutableStateFlow<PosRppgEngine.Diagnostics?>(null)
    val latestDiagnostics: StateFlow<PosRppgEngine.Diagnostics?> = _latestDiagnostics.asStateFlow()

    // ──────────────────── Components ────────────────────
    private val faceProcessor = FaceRoiProcessor()
    private val rgbBuffer = RgbSignalBuffer(MAX_BUFFER_SAMPLES)
    private val posEngine = PosRppgEngine()
    private var cameraController: CameraController? = null

    private var measurementStartMs = 0L
    private var signalProcessingJob: Job? = null
    private var isRunning = false
    private var latestEffectiveFps = 30.0

    // ──────────────────── Camera Lifecycle ────────────────────

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        if (isRunning) return
        isRunning = true

        _measurementState.value = MeasurementState.CameraStarting

        cameraController = CameraController(
            context = getApplication<Application>().applicationContext,
            faceProcessor = faceProcessor,
            onFrameResult = { frameResult -> onFrameResult(frameResult) },
            onError = { msg ->
                _measurementState.value = MeasurementState.Error(msg)
            }
        )

        cameraController?.startCamera(lifecycleOwner, surfaceProvider)
        measurementStartMs = System.currentTimeMillis()
        startSignalProcessingLoop()
    }

    fun stopCamera() {
        cameraController?.stopCamera()
        signalProcessingJob?.cancel()
        isRunning = false
    }

    // ──────────────────── Frame Processing ────────────────────

    private fun onFrameResult(result: CameraFrameResult) {
        val elapsedMs = System.currentTimeMillis() - measurementStartMs
        latestEffectiveFps = result.effectiveFps

        when (val faceDetection = result.faceDetection) {
            is FaceDetectionResult.NoFace -> {
                val current = _measurementState.value
                if (current !is MeasurementState.Measuring &&
                    current !is MeasurementState.Result) {
                    _measurementState.value = MeasurementState.NoFace
                }
            }
            is FaceDetectionResult.MultipleFaces -> {
                _measurementState.value = MeasurementState.SignalWeak(
                    faceRect = result.faceRect ?: FaceRect(0f, 0f, 1f, 1f),
                    reason = "Multiple faces detected"
                )
            }
            is FaceDetectionResult.Found -> {
                val faceRect = result.faceRect ?: faceDetection.faceRect
                val motion = result.motion

                if (faceProcessor.isExcessiveMotion(motion)) {
                    _measurementState.value = MeasurementState.SignalWeak(
                        faceRect = faceRect,
                        reason = "Hold still"
                    )
                } else {
                    // Add valid direct YUV-RGB sample to rolling buffer
                    val sample = result.rgbSample
                    if (sample != null) {
                        rgbBuffer.add(sample)
                    }

                    val currentBpm = getCurrentBpm()
                    val bufferDuration = rgbBuffer.durationMs()

                    _measurementState.value = if (currentBpm != null && bufferDuration >= MIN_BPM_DURATION_MS) {
                        MeasurementState.Measuring(
                            faceRect = faceRect,
                            bpm = currentBpm.first,
                            signalQuality = currentBpm.second,
                            elapsedMs = elapsedMs,
                            targetMs = TARGET_DURATION_MS,
                            pulseSignal = _pulseWaveform.value
                        )
                    } else {
                        MeasurementState.Collecting(
                            faceRect = faceRect,
                            elapsedMs = elapsedMs,
                            targetMs = TARGET_DURATION_MS,
                            pulseSignal = _pulseWaveform.value
                        )
                    }
                }
            }
            is FaceDetectionResult.Error -> {
                // Non-fatal
            }
        }
    }

    // ──────────────────── Signal Processing Loop ────────────────────

    private var lastBpmResult: Pair<Double, SignalQuality>? = null

    private fun startSignalProcessingLoop() {
        signalProcessingJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(SIGNAL_UPDATE_INTERVAL_MS)
                processSignal()
            }
        }
    }

    private fun processSignal() {
        val measuredFps = rgbBuffer.computeFps()
        val fps = if (measuredFps in 15.0..60.0) measuredFps else latestEffectiveFps
        val windowSamples = (SIGNAL_PROCESS_WINDOW_S * fps).toInt()
        val samples = rgbBuffer.lastN(windowSamples)

        if (samples.size < PosRppgEngine.MIN_SAMPLES_FOR_BPM) return

        try {
            val result = posEngine.process(samples, fps) ?: return
            lastBpmResult = Pair(result.bpm, result.signalQuality)
            _latestDiagnostics.value = result.diagnostics

            // Update waveform
            val waveformSamples = result.pulseSignal.takeLast(250)
            val floatWaveform = normalizeWaveform(waveformSamples)
            _pulseWaveform.value = floatWaveform

            // Check if full 25s measurement complete
            val elapsedMs = System.currentTimeMillis() - measurementStartMs
            if (elapsedMs >= TARGET_DURATION_MS && result.signalQuality.isDisplayable() && result.bpm > 30) {
                val vitalsResult = VitalsResult(
                    heartRate = result.bpm,
                    signalQuality = result.signalQuality,
                    measurementDurationMs = elapsedMs,
                    method = "POS (Harmonic Corrected)"
                )
                _measurementState.value = MeasurementState.Result(
                    result = vitalsResult,
                    pulseSignal = floatWaveform
                )
                signalProcessingJob?.cancel()
                Log.i(TAG, "[MeasurementCompleted] Final Heart Rate: ${result.bpm.toInt()} BPM, Quality=${result.signalQuality}, SNR=${"%.1f".format(result.diagnostics.snrDb)}dB")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[SignalProcessWarn] ${e.message}")
        }
    }

    private fun getCurrentBpm(): Pair<Double, SignalQuality>? = lastBpmResult

    private fun normalizeWaveform(signal: List<Double>): List<Float> {
        if (signal.isEmpty()) return emptyList()
        val min = signal.min()
        val max = signal.max()
        val range = max - min
        return if (range < 1e-10) {
            signal.map { 0.5f }
        } else {
            signal.map { ((it - min) / range).toFloat() }
        }
    }

    // ──────────────────── Public Controls ────────────────────

    fun startNewMeasurement() {
        signalProcessingJob?.cancel()
        rgbBuffer.clear()
        posEngine.reset()
        lastBpmResult = null
        _pulseWaveform.value = emptyList()
        _latestDiagnostics.value = null
        measurementStartMs = System.currentTimeMillis()
        _measurementState.value = MeasurementState.NoFace
        startSignalProcessingLoop()
    }

    override fun onCleared() {
        super.onCleared()
        stopCamera()
        faceProcessor.close()
    }
}
