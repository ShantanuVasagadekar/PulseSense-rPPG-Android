package com.rppg.vitals.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rppg.vitals.ui.components.OnDeviceBadge
import com.rppg.vitals.ui.theme.*

@Composable
fun HomeScreen(onStartMeasurement: () -> Unit) {
    // Background pulse ring animation
    val pulseAnim = rememberInfiniteTransition(label = "home_pulse")
    val ringScale by pulseAnim.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )
    val ringAlpha by pulseAnim.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentSubtle.copy(alpha = 0.3f),
                        BackgroundDeep
                    ),
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Top badge
            OnDeviceBadge()

            Spacer(modifier = Modifier.height(48.dp))

            // Central pulsing icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = ringAlpha))
                )
                // Middle ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.08f))
                        .border(1.dp, AccentPrimary.copy(alpha = 0.3f), CircleShape)
                )
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentSecondary, BackgroundCard)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulse line icon (draw a simple ECG-style wave)
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
                        val path = androidx.compose.ui.graphics.Path()
                        val w = size.width
                        val h = size.height
                        val mid = h * 0.5f
                        path.moveTo(0f, mid)
                        path.lineTo(w * 0.2f, mid)
                        path.lineTo(w * 0.35f, h * 0.15f)
                        path.lineTo(w * 0.5f, h * 0.85f)
                        path.lineTo(w * 0.65f, mid * 0.3f)
                        path.lineTo(w * 0.75f, mid)
                        path.lineTo(w, mid)
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App name
            Text(
                text = "AuraHealth",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Camera-Based Vital Estimation",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = TextSecondary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "rPPG • POS Algorithm • Edge-Native",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AccentPrimary.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Feature pills
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeaturePill(
                    icon = Icons.Filled.CameraAlt,
                    label = "Camera only",
                    description = "Uses front camera for rPPG signal"
                )
                FeaturePill(
                    icon = Icons.Filled.Memory,
                    label = "On-device processing",
                    description = "POS algorithm runs locally"
                )
                FeaturePill(
                    icon = Icons.Filled.WifiOff,
                    label = "No cloud required",
                    description = "Fully offline — no internet needed"
                )
                FeaturePill(
                    icon = Icons.Filled.Lock,
                    label = "Private by design",
                    description = "No data leaves your device"
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Primary CTA button
            Button(
                onClick = onStartMeasurement,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BackgroundDeep
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = "Start Measurement",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = BackgroundDeep
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Disclaimer
            Text(
                text = "Research/demo measurement only.\nNot a medical device or diagnosis.",
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
private fun FeaturePill(
    icon: ImageVector,
    label: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AccentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary)
            )
        }
    }
}
