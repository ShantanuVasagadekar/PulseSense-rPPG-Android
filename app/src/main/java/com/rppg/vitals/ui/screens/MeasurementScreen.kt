package com.rppg.vitals.ui.screens

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rppg.vitals.VitalsViewModel
import com.rppg.vitals.domain.*
import com.rppg.vitals.ui.components.*
import com.rppg.vitals.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    viewModel: VitalsViewModel,
    onBack: () -> Unit,
    onResult: (VitalsResult) -> Unit
) {
    val measurementState by viewModel.measurementState.collectAsState()
    val pulseWaveform by viewModel.pulseWaveform.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Provide surface provider to ViewModel once camera is ready
    var surfaceProvider by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }

    LaunchedEffect(surfaceProvider) {
        surfaceProvider?.let { provider ->
            viewModel.startCamera(lifecycleOwner, provider)
        }
    }

    // Navigate to result when complete
    LaunchedEffect(measurementState) {
        if (measurementState is MeasurementState.Result) {
            onResult((measurementState as MeasurementState.Result).result)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Bar ──
            TopBar(onBack = onBack)

            // ── Camera Preview (large, rounded card) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clipToBounds()
            ) {
                // CameraX PreviewView
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        if (surfaceProvider == null) {
                            surfaceProvider = previewView.surfaceProvider
                        }
                    }
                )

                // Face guide overlay drawn on top
                val faceRect = when (val s = measurementState) {
                    is MeasurementState.FaceDetected -> s.faceRect
                    is MeasurementState.Collecting -> s.faceRect
                    is MeasurementState.Measuring -> s.faceRect
                    is MeasurementState.SignalWeak -> s.faceRect
                    is MeasurementState.Analyzing -> s.faceRect
                    else -> null
                }
                val faceDetected = faceRect != null && measurementState !is MeasurementState.NoFace

                FaceGuideOverlay(
                    faceDetected = faceDetected,
                    faceRect = faceRect,
                    modifier = Modifier.fillMaxSize()
                )

                // Status message overlay at bottom of camera
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    StatusOverlayMessage(state = measurementState)
                }
            }

            // ── Bottom Panel ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // BPM + Quality Card
                val (bpm, quality) = when (val s = measurementState) {
                    is MeasurementState.Measuring -> Pair(s.bpm, s.signalQuality)
                    is MeasurementState.Result -> Pair(s.result.heartRate, s.result.signalQuality)
                    else -> Pair(null, SignalQuality.NO_SIGNAL)
                }
                BpmCard(
                    bpm = bpm,
                    quality = quality,
                    isVisible = true
                )

                // Progress bar
                val (elapsed, target) = when (val s = measurementState) {
                    is MeasurementState.Collecting -> Pair(s.elapsedMs, s.targetMs)
                    is MeasurementState.Measuring -> Pair(s.elapsedMs, s.targetMs)
                    else -> Pair(0L, 30_000L)
                }
                MeasurementProgressBar(elapsedMs = elapsed, targetMs = target)

                // Diagnostics Info Pill
                val diag by viewModel.latestDiagnostics.collectAsState()
                if (diag != null) {
                    val d = diag!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FPS: ${"%.1f".format(d.effectiveFps)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentPrimary)
                        )
                        Text(
                            text = "Peak: ${"%.2f".format(d.fundamentalFreqHz)}Hz",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = if (d.isHarmonicCorrected) "Harmonic: 2x -> 1x" else "Fund: 1x",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (d.isHarmonicCorrected) AccentGlow else TextTertiary)
                        )
                        Text(
                            text = "SNR: ${"%.1f".format(d.snrDb)}dB",
                            style = MaterialTheme.typography.labelSmall.copy(color = StatusGood)
                        )
                    }
                }

                // Live waveform
                PulseWaveformCard(waveform = pulseWaveform)

                // Bottom action
                OutlinedButton(
                    onClick = {
                        viewModel.startNewMeasurement()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentPrimary
                    ),
                    border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Start New Scan",
                        style = MaterialTheme.typography.labelLarge.copy(color = AccentPrimary)
                    )
                }

                // Disclaimer
                Text(
                    text = "Research/demo only. Not a medical device.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "rPPG VITALS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    letterSpacing = 1.5.sp
                )
            )
            Text(
                text = "Edge Health Monitor",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextTertiary,
                    letterSpacing = 0.5.sp
                )
            )
        }

        OnDeviceBadge()
    }
}

@Composable
private fun StatusOverlayMessage(state: MeasurementState) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        },
        label = "status_message"
    ) { s ->
        when (s) {
            is MeasurementState.NoFace, is MeasurementState.CameraStarting ->
                StatusMessage(
                    message = "Position your face inside the frame",
                    isWarning = false
                )
            is MeasurementState.FaceDetected ->
                StatusMessage(message = "Face detected • Hold still")
            is MeasurementState.Collecting ->
                StatusMessage(message = "Collecting pulse signal…")
            is MeasurementState.Measuring ->
                StatusMessage(message = "Analyzing pulse…")
            is MeasurementState.SignalWeak ->
                StatusMessage(
                    message = s.reason.ifBlank { "Hold still" },
                    isWarning = true
                )
            is MeasurementState.Analyzing ->
                StatusMessage(message = "Processing…")
            is MeasurementState.Error ->
                StatusMessage(message = s.message, isWarning = true)
            else -> {}
        }
    }
}
