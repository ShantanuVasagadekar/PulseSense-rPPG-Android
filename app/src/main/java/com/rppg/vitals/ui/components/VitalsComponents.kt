package com.rppg.vitals.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rppg.vitals.domain.FaceRect
import com.rppg.vitals.domain.SignalQuality
import com.rppg.vitals.ui.theme.*
import kotlin.math.sin

// ──────────────────────────────────────────────────────────────────────
//  Face Guide Overlay
// ──────────────────────────────────────────────────────────────────────

@Composable
fun FaceGuideOverlay(
    faceDetected: Boolean,
    faceRect: FaceRect?,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "guide_pulse")
    val alpha by pulseAnim.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guide_alpha"
    )

    val strokeColor = if (faceDetected) AccentPrimary else TextSecondary.copy(alpha = 0.6f)
    val effectiveAlpha = if (faceDetected) alpha else 0.6f

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.42f
        val guideW = size.width * 0.6f
        val guideH = guideW * 1.35f  // taller oval for face

        // Draw the face guide oval
        drawOval(
            color = strokeColor.copy(alpha = effectiveAlpha),
            topLeft = Offset(centerX - guideW / 2, centerY - guideH / 2),
            size = Size(guideW, guideH),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Corner accent marks (premium look)
        val cornerRadius = 20.dp.toPx()
        val cornerLen = 30.dp.toPx()
        val left = centerX - guideW / 2
        val top = centerY - guideH / 2
        val right = centerX + guideW / 2
        val bottom = centerY + guideH / 2

        val cornerPaint = strokeColor.copy(alpha = 1f)
        val cornerStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

        // Top-left
        drawArc(
            color = cornerPaint,
            startAngle = 180f, sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = cornerStroke
        )
        // Top-right
        drawArc(
            color = cornerPaint,
            startAngle = 270f, sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(right - cornerRadius * 2, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = cornerStroke
        )

        // If face detected, draw ROI boxes subtly
        if (faceDetected && faceRect != null) {
            val fLeft = faceRect.left * size.width
            val fTop = faceRect.top * size.height
            val fW = faceRect.width * size.width
            val fH = faceRect.height * size.height

            // Forehead ROI
            val fhTop = fTop + fH * 0.08f
            val fhBottom = fTop + fH * 0.28f
            val fhLeft = fLeft + fW * 0.25f
            val fhRight = fLeft + fW * 0.75f
            drawRect(
                color = AccentPrimary.copy(alpha = 0.3f * effectiveAlpha),
                topLeft = Offset(fhLeft, fhTop),
                size = Size(fhRight - fhLeft, fhBottom - fhTop),
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // Left cheek ROI
            val ckTop = fTop + fH * 0.38f
            val ckBottom = fTop + fH * 0.62f
            drawRect(
                color = AccentPrimary.copy(alpha = 0.25f * effectiveAlpha),
                topLeft = Offset(fLeft + fW * 0.08f, ckTop),
                size = Size(fW * 0.30f, ckBottom - ckTop),
                style = Stroke(width = 1.5f.dp.toPx())
            )
            // Right cheek ROI
            drawRect(
                color = AccentPrimary.copy(alpha = 0.25f * effectiveAlpha),
                topLeft = Offset(fLeft + fW * 0.62f, ckTop),
                size = Size(fW * 0.30f, ckBottom - ckTop),
                style = Stroke(width = 1.5f.dp.toPx())
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  BPM Display Card
// ──────────────────────────────────────────────────────────────────────

@Composable
fun BpmCard(
    bpm: Double?,
    quality: SignalQuality,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val bpmText = if (bpm != null && bpm > 30) bpm.toInt().toString() else "--"

    // Heart pulse animation
    val heartAnim = rememberInfiniteTransition(label = "heart_pulse")
    val heartScale by heartAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (bpm != null && bpm > 30) 1.15f else 1f,
        animationSpec = if (bpm != null && bpm > 30) {
            infiniteRepeatable(
                animation = tween((60000 / bpm).toInt().coerceIn(400, 1500), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            infiniteRepeatable(tween(1000))
        },
        label = "heart_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Heart icon with pulse animation
            Icon(
                imageVector = if (bpm != null && bpm > 30) Icons.Filled.Favorite
                              else Icons.Filled.FavoriteBorder,
                contentDescription = "Heart rate",
                modifier = Modifier
                    .size(28.dp)
                    .scale(heartScale),
                tint = if (bpm != null && bpm > 30) AccentPrimary else TextTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // BPM number – the star of the show
            Text(
                text = bpmText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Thin,
                    color = if (bpm != null && bpm > 30) TextPrimary else TextTertiary
                )
            )

            Text(
                text = "BPM",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    color = AccentPrimary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Heart Rate",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Signal quality badge
            SignalQualityBadge(quality = quality)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Signal Quality Badge
// ──────────────────────────────────────────────────────────────────────

@Composable
fun SignalQualityBadge(quality: SignalQuality, modifier: Modifier = Modifier) {
    val color = when (quality) {
        SignalQuality.GOOD -> StatusGood
        SignalQuality.FAIR -> StatusFair
        SignalQuality.POOR -> StatusPoor
        SignalQuality.NO_SIGNAL -> StatusNoSignal
    }

    val dotAnim = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by dotAnim.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = color.copy(alpha = if (quality != SignalQuality.NO_SIGNAL) dotAlpha else 0.4f),
                    shape = CircleShape
                )
        )
        Text(
            text = quality.label(),
            style = MaterialTheme.typography.labelMedium.copy(
                color = color,
                letterSpacing = 1.sp
            )
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Live Pulse Waveform
// ──────────────────────────────────────────────────────────────────────

@Composable
fun PulseWaveformCard(
    waveform: List<Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE PULSE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = TextTertiary
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentSubtle)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                if (waveform.isEmpty()) {
                    // Show flat line when no signal
                    drawLine(
                        color = TextTertiary.copy(alpha = 0.3f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                    return@Canvas
                }

                val displayPoints = waveform.takeLast(150)
                val stepX = size.width / (displayPoints.size - 1).coerceAtLeast(1).toFloat()
                val padding = size.height * 0.1f

                // Draw gradient fill below the curve
                val path = Path().apply {
                    moveTo(0f, size.height)
                    displayPoints.forEachIndexed { i, v ->
                        val x = i * stepX
                        val y = size.height - padding - (v * (size.height - 2 * padding))
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentPrimary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )

                // Draw the waveform line
                val linePath = Path()
                displayPoints.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - padding - (v * (size.height - 2 * padding))
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(
                    path = linePath,
                    color = AccentPrimary,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Glow dot at end
                if (displayPoints.isNotEmpty()) {
                    val lastX = (displayPoints.size - 1) * stepX
                    val lastY = size.height - padding - (displayPoints.last() * (size.height - 2 * padding))
                    drawCircle(
                        color = AccentGlow.copy(alpha = 0.6f),
                        radius = 6.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = AccentGlow,
                        radius = 3.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Measurement Progress
// ──────────────────────────────────────────────────────────────────────

@Composable
fun MeasurementProgressBar(
    elapsedMs: Long,
    targetMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = (elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    val elapsedSec = elapsedMs / 1000
    val targetSec = targetMs / 1000
    val elapsedStr = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    val targetStr = "%02d:%02d".format(targetSec / 60, targetSec % 60)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CAPTURING",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = TextTertiary
                )
            )
            Text(
                text = "$elapsedStr / $targetStr",
                style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Thin progress bar with glow effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AccentSecondary, AccentGlow)
                        )
                    )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Status Message
// ──────────────────────────────────────────────────────────────────────

@Composable
fun StatusMessage(
    message: String,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = if (isWarning) StatusFair else TextSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isWarning) StatusFair.copy(alpha = 0.1f) else BackgroundElevated)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isWarning) {
            val warningAnim = rememberInfiniteTransition(label = "warning")
            val warningAlpha by warningAnim.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "w_alpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(StatusFair.copy(alpha = warningAlpha), CircleShape)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(color = color),
            textAlign = TextAlign.Center
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  On-Device Badge
// ──────────────────────────────────────────────────────────────────────

@Composable
fun OnDeviceBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(BadgeBg)
            .border(1.dp, BadgeGreen.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(BadgeGreen, CircleShape)
        )
        Text(
            text = "DEVICE LOCAL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = BadgeGreen,
                letterSpacing = 1.sp
            )
        )
    }
}
