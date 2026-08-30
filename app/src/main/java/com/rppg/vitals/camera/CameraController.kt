package com.rppg.vitals.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.rppg.vitals.domain.FaceRect
import com.rppg.vitals.domain.RgbSample
import com.rppg.vitals.face.FaceDetectionResult
import com.rppg.vitals.face.FaceRoiProcessor
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Optimized CameraX pipeline specifically tuned for Poco F5 and Snapdragon devices:
 *   - Continuous 30.0 FPS ImageAnalysis at 640x480 / 720p
 *   - Direct, lossless YUV_420_888 -> RGB skin extraction (zero JPEG, zero GC)
 *   - Hardware monotonic sensor timestamps (imageInfo.timestamp in nanoseconds)
 *   - Asynchronous non-blocking ML Kit face tracking
 *   - Comprehensive Logcat diagnostics
 */
class CameraController(
    private val context: Context,
    private val faceProcessor: FaceRoiProcessor,
    private val onFrameResult: (CameraFrameResult) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "RPPG_DIAG"
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val faceDetectScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    // Tracked face state (updated asynchronously by ML Kit)
    @Volatile private var latestFaceRect: FaceRect? = null
    @Volatile private var latestMotion: Float = 0f
    @Volatile private var faceDetectionStatus: FaceDetectionResult = FaceDetectionResult.NoFace

    // Face detection concurrency lock (never blocks the 30fps camera stream)
    private val isDetectingFace = AtomicBoolean(false)
    private var frameCounter = 0L
    private var acceptedFramesCount = 0L

    // FPS estimation based on hardware sensor timestamps
    private var firstFrameTimestampNs = 0L
    private var lastFrameTimestampNs = 0L
    private var rollingFps = 30.0

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewSurfaceProvider: Preview.SurfaceProvider
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewSurfaceProvider }

                // Optimal resolution for Poco F5 front camera: 640x480 provides fast 30fps and large ROI
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i(TAG, "[CameraInit] Bound CameraX front camera with 640x480 target resolution on Poco F5.")
            } catch (e: Exception) {
                Log.e(TAG, "[CameraInitError] ${e.message}", e)
                onError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val frameTimestampNs = imageProxy.imageInfo.timestamp
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val imgWidth = imageProxy.width
            val imgHeight = imageProxy.height

            if (firstFrameTimestampNs == 0L) {
                firstFrameTimestampNs = frameTimestampNs
            }

            // Calculate instantaneous & rolling FPS from true camera hardware clock
            if (lastFrameTimestampNs > 0L) {
                val deltaNs = frameTimestampNs - lastFrameTimestampNs
                if (deltaNs > 0) {
                    val instantFps = 1_000_000_000.0 / deltaNs
                    rollingFps = 0.9 * rollingFps + 0.1 * instantFps
                }
            }
            lastFrameTimestampNs = frameTimestampNs
            frameCounter++

            // Periodically trigger face detection if worker is free
            val mediaImage = imageProxy.image
            if (mediaImage != null && isDetectingFace.compareAndSet(false, true)) {
                val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                faceDetectScope.launch {
                    try {
                        val result = faceProcessor.detectFace(inputImage, imgWidth, imgHeight)
                        faceDetectionStatus = result
                        if (result is FaceDetectionResult.Found) {
                            latestFaceRect = result.faceRect
                            latestMotion = result.motion
                        } else if (result is FaceDetectionResult.NoFace) {
                            latestFaceRect = null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[FaceDetectWarn] ${e.message}")
                    } finally {
                        isDetectingFace.set(false)
                    }
                }
            }

            // Extract direct lossless YUV -> RGB if face is tracked
            val currentFace = latestFaceRect
            val currentMotion = latestMotion
            val timestampMs = frameTimestampNs / 1_000_000L

            var rgbSample: RgbSample? = null
            if (currentFace != null && !faceProcessor.isExcessiveMotion(currentMotion)) {
                rgbSample = DirectYuvRgbExtractor.extractMultiRoiRgb(
                    imageProxy = imageProxy,
                    faceRect = currentFace,
                    rotationDegrees = rotationDegrees
                )
                acceptedFramesCount++
            }

            if (frameCounter % 60 == 0L) {
                Log.d(TAG, "[CameraDiag] Dimensions=${imgWidth}x${imgHeight}, Rot=$rotationDegrees, HW_FPS=${"%.1f".format(rollingFps)}, AcceptedFrames=$acceptedFramesCount, FaceTracked=${currentFace != null}, Motion=${"%.3f".format(currentMotion)}")
            }

            onFrameResult(
                CameraFrameResult(
                    faceDetection = faceDetectionStatus,
                    faceRect = currentFace,
                    rgbSample = rgbSample,
                    motion = currentMotion,
                    frameWidth = imgWidth,
                    frameHeight = imgHeight,
                    timestampMs = timestampMs,
                    effectiveFps = rollingFps
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[FrameError] ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        faceDetectScope.cancel()
        cameraExecutor.shutdown()
    }
}

data class CameraFrameResult(
    val faceDetection: FaceDetectionResult,
    val faceRect: FaceRect?,
    val rgbSample: RgbSample?,
    val motion: Float,
    val frameWidth: Int,
    val frameHeight: Int,
    val timestampMs: Long,
    val effectiveFps: Double
)
