package com.rppg.vitals

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rppg.vitals.domain.VitalsResult
import com.rppg.vitals.ui.screens.*
import com.rppg.vitals.ui.theme.RPPGVitalsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: VitalsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPPGVitalsTheme {
                PulseSenseApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PulseSenseApp(viewModel: VitalsViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Track last result for navigation to result screen
    var lastResult by remember { mutableStateOf<VitalsResult?>(null) }
    var lastPulseSignal by remember { mutableStateOf<List<Float>>(emptyList()) }

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionPermanentlyDenied = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D0D)),
        enterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
        },
        exitTransition = {
            fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 }
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 }
        },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 4 }
        }
    ) {
        // ── Home ──
        composable("home") {
            HomeScreen(
                onStartMeasurement = {
                    if (hasCameraPermission) {
                        navController.navigate("measurement")
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }

        // ── Permission Denied ──
        composable("permission") {
            PermissionDeniedScreen(
                onGrantPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                isPermanentlyDenied = permissionPermanentlyDenied
            )
        }

        // ── Measurement ──
        composable("measurement") {
            if (!hasCameraPermission) {
                PermissionDeniedScreen(
                    onGrantPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    isPermanentlyDenied = permissionPermanentlyDenied
                )
            } else {
                val measurementState by viewModel.measurementState.collectAsState()
                val pulseWaveform by viewModel.pulseWaveform.collectAsState()

                // Watch for result state
                LaunchedEffect(measurementState) {
                    val state = measurementState
                    if (state is com.rppg.vitals.domain.MeasurementState.Result) {
                        lastResult = state.result
                        lastPulseSignal = state.pulseSignal
                        navController.navigate("result") {
                            launchSingleTop = true
                        }
                    }
                }

                MeasurementScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.stopCamera()
                        navController.popBackStack()
                    },
                    onResult = { result ->
                        lastResult = result
                        lastPulseSignal = pulseWaveform
                    }
                )
            }
        }

        // ── Result ──
        composable("result") {
            val result = lastResult
            if (result != null) {
                ResultScreen(
                    result = result,
                    pulseSignal = lastPulseSignal,
                    onMeasureAgain = {
                        viewModel.stopCamera()
                        viewModel.startNewMeasurement()
                        navController.navigate("measurement") {
                            popUpTo("measurement") { inclusive = true }
                        }
                    },
                    onHome = {
                        viewModel.stopCamera()
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            } else {
                // Fallback: go back to measurement
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }

    // Handle permission result
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && navController.currentDestination?.route == "permission") {
            navController.navigate("measurement") {
                popUpTo("permission") { inclusive = true }
            }
        }
    }
}
