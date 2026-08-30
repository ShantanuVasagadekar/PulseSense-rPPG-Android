package com.rppg.vitals.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rppg.vitals.ui.theme.*

@Composable
fun PermissionDeniedScreen(
    onGrantPermission: () -> Unit,
    isPermanentlyDenied: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Camera",
                    tint = AccentPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isPermanentlyDenied) {
                    "Camera permission was permanently denied.\nPlease enable it in your device Settings → Apps → PulseSense → Permissions."
                } else {
                    "PulseSense needs camera access to measure your heart rate using rPPG technology.\n\nNo video is saved or transmitted."
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )

            if (!isPermanentlyDenied) {
                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        contentColor = BackgroundDeep
                    )
                ) {
                    Text(
                        text = "Grant Camera Permission",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Text(
                text = "Your camera data never leaves this device.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
                textAlign = TextAlign.Center
            )
        }
    }
}
