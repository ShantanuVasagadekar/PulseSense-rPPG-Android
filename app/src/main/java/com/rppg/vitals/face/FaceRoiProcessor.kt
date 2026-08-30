package com.rppg.vitals.face

import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.rppg.vitals.domain.FaceRect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Fast ML Kit Face Detection for real-time tracking on Poco F5.
 */
class FaceRoiProcessor {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.18f)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    private val facePositionHistory = ArrayDeque<FaceRect>(10)
    private val MAX_HISTORY = 10
    private val MOTION_THRESHOLD = 0.035f  // normalized coordinates

    /**
     * Asynchronously detect faces in an InputImage.
     */
    suspend fun detectFace(
        image: InputImage,
        imageWidth: Int,
        imageHeight: Int
    ): FaceDetectionResult = suspendCancellableCoroutine { cont ->
        detector.process(image)
            .addOnSuccessListener { faces ->
                val result = when {
                    faces.isEmpty() -> FaceDetectionResult.NoFace
                    faces.size > 1 -> FaceDetectionResult.MultipleFaces
                    else -> {
                        val face = faces[0]
                        val faceRect = normalizeFaceRect(face.boundingBox, imageWidth, imageHeight, face.trackingId)
                        val motion = computeMotion(faceRect)
                        updatePositionHistory(faceRect)
                        FaceDetectionResult.Found(faceRect, motion)
                    }
                }
                if (cont.isActive) cont.resume(result)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resume(FaceDetectionResult.Error("Detection failed: ${e.message}"))
            }
    }

    private fun normalizeFaceRect(
        rect: Rect, imageWidth: Int, imageHeight: Int, trackingId: Int?
    ): FaceRect {
        return FaceRect(
            left = (rect.left.toFloat() / imageWidth).coerceIn(0f, 1f),
            top = (rect.top.toFloat() / imageHeight).coerceIn(0f, 1f),
            right = (rect.right.toFloat() / imageWidth).coerceIn(0f, 1f),
            bottom = (rect.bottom.toFloat() / imageHeight).coerceIn(0f, 1f),
            trackingId = trackingId
        )
    }

    private fun computeMotion(current: FaceRect): Float {
        if (facePositionHistory.isEmpty()) return 0f
        val last = facePositionHistory.last()
        val dx = abs(current.centerX - last.centerX)
        val dy = abs(current.centerY - last.centerY)
        return dx + dy
    }

    private fun updatePositionHistory(faceRect: FaceRect) {
        if (facePositionHistory.size >= MAX_HISTORY) facePositionHistory.removeFirst()
        facePositionHistory.addLast(faceRect)
    }

    fun isExcessiveMotion(motion: Float) = motion > MOTION_THRESHOLD

    fun close() {
        detector.close()
    }
}

sealed class FaceDetectionResult {
    object NoFace : FaceDetectionResult()
    object MultipleFaces : FaceDetectionResult()
    data class Found(val faceRect: FaceRect, val motion: Float) : FaceDetectionResult()
    data class Error(val message: String) : FaceDetectionResult()
}
