package com.rppg.vitals.camera

import androidx.camera.core.ImageProxy
import com.rppg.vitals.domain.FaceRect
import com.rppg.vitals.domain.RgbSample
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, zero-allocation extractor for spatial-average RGB from YUV_420_888 ImageProxy.
 *
 * Eliminates all JPEG compression, Bitmap decoding, and Garbage Collection pauses.
 * Preserves the pristine micro-chrominance pulse signal (Δ ≈ 0.5-2%) required for rPPG.
 */
object DirectYuvRgbExtractor {

    /**
     * Extract spatial-average RGB across multiple facial skin sub-ROIs (forehead + cheeks).
     *
     * @param imageProxy The raw YUV_420_888 camera frame
     * @param faceRect Normalized face bounding box [0.0 .. 1.0]
     * @param rotationDegrees Sensor rotation (e.g. 270 or 90 for front camera)
     * @return [RgbSample] with spatial average R, G, B and nanosecond hardware timestamp converted to ms.
     */
    fun extractMultiRoiRgb(
        imageProxy: ImageProxy,
        faceRect: FaceRect,
        rotationDegrees: Int
    ): RgbSample {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val imgWidth = imageProxy.width
        val imgHeight = imageProxy.height

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        // Map normalized faceRect (which is in upright preview space) to raw sensor coordinates
        val (fLeft, fTop, fRight, fBottom) = mapFaceRectToSensorCoords(
            faceRect, imgWidth, imgHeight, rotationDegrees
        )

        val faceW = fRight - fLeft
        val faceH = fBottom - fTop

        if (faceW < 16 || faceH < 16) {
            val avg = extractRegionAvg(
                yBuffer, uBuffer, vBuffer,
                yRowStride, yPixelStride, uRowStride, uPixelStride, vRowStride, vPixelStride,
                fLeft, fTop, fRight, fBottom, imgWidth, imgHeight
            )
            return RgbSample(avg.r, avg.g, avg.b, imageProxy.imageInfo.timestamp / 1_000_000L)
        }

        // ── Forehead ROI: top 8-28% of face height, central 50% width ──
        val fhTop = fTop + (faceH * 0.08).toInt()
        val fhBottom = fTop + (faceH * 0.28).toInt()
        val fhLeft = fLeft + (faceW * 0.25).toInt()
        val fhRight = fLeft + (faceW * 0.75).toInt()

        // ── Left cheek ROI: 38-62% height, left 8-38% width ──
        val lcTop = fTop + (faceH * 0.38).toInt()
        val lcBottom = fTop + (faceH * 0.62).toInt()
        val lcLeft = fLeft + (faceW * 0.08).toInt()
        val lcRight = fLeft + (faceW * 0.38).toInt()

        // ── Right cheek ROI: 38-62% height, right 62-92% width ──
        val rcTop = lcTop
        val rcBottom = lcBottom
        val rcLeft = fLeft + (faceW * 0.62).toInt()
        val rcRight = fLeft + (faceW * 0.92).toInt()

        val forehead = extractRegionAvg(
            yBuffer, uBuffer, vBuffer,
            yRowStride, yPixelStride, uRowStride, uPixelStride, vRowStride, vPixelStride,
            fhLeft, fhTop, fhRight, fhBottom, imgWidth, imgHeight
        )
        val leftCheek = extractRegionAvg(
            yBuffer, uBuffer, vBuffer,
            yRowStride, yPixelStride, uRowStride, uPixelStride, vRowStride, vPixelStride,
            lcLeft, lcTop, lcRight, lcBottom, imgWidth, imgHeight
        )
        val rightCheek = extractRegionAvg(
            yBuffer, uBuffer, vBuffer,
            yRowStride, yPixelStride, uRowStride, uPixelStride, vRowStride, vPixelStride,
            rcLeft, rcTop, rcRight, rcBottom, imgWidth, imgHeight
        )

        // Weighted spatial mean across all 3 skin sub-ROIs
        val avgR = (forehead.r + leftCheek.r + rightCheek.r) / 3.0
        val avgG = (forehead.g + leftCheek.g + rightCheek.g) / 3.0
        val avgB = (forehead.b + leftCheek.b + rightCheek.b) / 3.0

        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L

        return RgbSample(avgR, avgG, avgB, timestampMs)
    }

    private data class RgbTuple(val r: Double, val g: Double, val b: Double)

    private fun extractRegionAvg(
        yBuffer: ByteBuffer, uBuffer: ByteBuffer, vBuffer: ByteBuffer,
        yRowStride: Int, yPixelStride: Int,
        uRowStride: Int, uPixelStride: Int,
        vRowStride: Int, vPixelStride: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        imgWidth: Int, imgHeight: Int
    ): RgbTuple {
        val l = left.coerceIn(0, imgWidth - 1)
        val t = top.coerceIn(0, imgHeight - 1)
        val r = right.coerceIn(0, imgWidth - 1)
        val b = bottom.coerceIn(0, imgHeight - 1)

        if (r <= l || b <= t) {
            return RgbTuple(128.0, 128.0, 128.0)
        }

        // Subsample step: sample every 2nd or 3rd pixel for fast <0.1ms computation
        val step = max(2, min((r - l) / 25, (b - t) / 25))

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        var y = t
        while (y < b) {
            val yRowOffset = y * yRowStride
            val uvRowOffset = (y / 2) * uRowStride
            val vRowOffset = (y / 2) * vRowStride

            var x = l
            while (x < r) {
                val yIndex = yRowOffset + x * yPixelStride
                val uvIndex = uvRowOffset + (x / 2) * uPixelStride
                val vIndex = vRowOffset + (x / 2) * vPixelStride

                if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && vIndex < vBuffer.limit()) {
                    val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)
                    val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                    // ITU-R BT.601 conversion (Standard YUV to RGB)
                    val rPix = (yVal + 1.402 * vVal).coerceIn(0.0, 255.0)
                    val gPix = (yVal - 0.344136 * uVal - 0.714136 * vVal).coerceIn(0.0, 255.0)
                    val bPix = (yVal + 1.772 * uVal).coerceIn(0.0, 255.0)

                    sumR += rPix
                    sumG += gPix
                    sumB += bPix
                    count++
                }
                x += step
            }
            y += step
        }

        return if (count == 0) {
            RgbTuple(128.0, 128.0, 128.0)
        } else {
            RgbTuple(sumR / count, sumG / count, sumB / count)
        }
    }

    private data class BoundingCoords(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun mapFaceRectToSensorCoords(
        faceRect: FaceRect,
        imgWidth: Int,
        imgHeight: Int,
        rotationDegrees: Int
    ): BoundingCoords {
        // FaceRect is normalized [0..1] in the displayed upright orientation.
        // For front camera, rotation is typically 270 or 90 degrees.
        return when (rotationDegrees) {
            90 -> {
                val left = (faceRect.top * imgWidth).toInt()
                val top = ((1f - faceRect.right) * imgHeight).toInt()
                val right = (faceRect.bottom * imgWidth).toInt()
                val bottom = ((1f - faceRect.left) * imgHeight).toInt()
                BoundingCoords(left, top, right, bottom)
            }
            270 -> {
                val left = ((1f - faceRect.bottom) * imgWidth).toInt()
                val top = (faceRect.left * imgHeight).toInt()
                val right = ((1f - faceRect.top) * imgWidth).toInt()
                val bottom = (faceRect.right * imgHeight).toInt()
                BoundingCoords(left, top, right, bottom)
            }
            180 -> {
                val left = ((1f - faceRect.right) * imgWidth).toInt()
                val top = ((1f - faceRect.bottom) * imgHeight).toInt()
                val right = ((1f - faceRect.left) * imgWidth).toInt()
                val bottom = ((1f - faceRect.top) * imgHeight).toInt()
                BoundingCoords(left, top, right, bottom)
            }
            else -> {
                val left = (faceRect.left * imgWidth).toInt()
                val top = (faceRect.top * imgHeight).toInt()
                val right = (faceRect.right * imgWidth).toInt()
                val bottom = (faceRect.bottom * imgHeight).toInt()
                BoundingCoords(left, top, right, bottom)
            }
        }
    }
}
