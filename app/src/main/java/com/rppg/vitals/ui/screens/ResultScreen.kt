package com.rppg.vitals.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rppg.vitals.domain.SignalQuality
import com.rppg.vitals.domain.VitalsResult
import com.rppg.vitals.ui.components.*
import com.rppg.vitals.ui.theme.*

@Composable
fun ResultScreen(
    result: VitalsResult,
    pulseSignal: List<Float>,
    onMeasureAgain: () -> Unit,
    onHome: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "result_pulse")
    val heartScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                (60000.0 / result.heartRate).toInt().coerceIn(400, 1200),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "result_heart"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDeep,
                        BackgroundCard.copy(alpha = 0.5f),
                        BackgroundDeep
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Success indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.12f))
                    .border(2.dp, AccentPrimary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Heart",
                    tint = AccentPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(heartScale)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Heart Rate",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Giant BPM
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.heartRate.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Thin,
                        color = TextPrimary,
                        fontSize = 112.sp
                    )
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AccentPrimary,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Signal quality
            SignalQualityBadge(quality = result.signalQuality)

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform
            if (pulseSignal.isNotEmpty()) {
                PulseWaveformCard(waveform = pulseSignal)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Metadata card
            MetadataCard(result = result)

            Spacer(modifier = Modifier.height(32.dp))

            // Actions
            Button(
                onClick = onMeasureAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BackgroundDeep
                )
            ) {
                Text(
                    text = "Measure Again",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onHome) {
                Text(
                    text = "Return to Home",
                    style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Research/demo measurement only.\nNot a medical device or clinical diagnosis.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextTertiary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MetadataCard(result: VitalsResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MEASUREMENT DETAILS",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextTertiary,
                    letterSpacing = 2.sp
                )
            )

            MetaRow(
                icon = Icons.Filled.Timer,
                label = "Duration",
                value = "${result.measurementDurationMs / 1000} sec"
            )
            HorizontalDivider(color = BackgroundElevated, thickness = 1.dp)
            MetaRow(
                icon = Icons.Filled.CameraAlt,
                label = "Method",
                value = "Camera rPPG • ${result.method}"
            )
            HorizontalDivider(color = BackgroundElevated, thickness = 1.dp)
            MetaRow(
                icon = Icons.Filled.Memory,
                label = "Processing",
                value = "On-device"
            )
            HorizontalDivider(color = BackgroundElevated, thickness = 1.dp)
            MetaRow(
                icon = Icons.Filled.Lock,
                label = "Privacy",
                value = "No data transmitted"
            )
        }
    }
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AccentPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
