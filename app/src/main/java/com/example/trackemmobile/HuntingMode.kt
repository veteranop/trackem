package com.veteranop.trackem.ui.hunting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun HuntingModeScreen(
    targetMac: String,
    viewModel: HuntingViewModel = viewModel(),
    onBack: () -> Unit
) {
    val samples by viewModel.samples.collectAsState()
    val estimatedPos by viewModel.estimatedPosition.collectAsState()
    val radius by viewModel.confidenceRadius.collectAsState()

    // THIS IS THE FIX — isolated in its own Composable
    val cameraPositionState by rememberCameraPositionStateInSeparateComposable()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true)
        ) {
            samples.forEach { s ->
                val intensity = (s.rssi + 100f) / 60f.coerceIn(0f, 1f)
                Marker(
                    state = rememberMarkerState(position = LatLng(s.lat, s.lng)),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    alpha = 0.5f + 0.5f * intensity
                )
            }

            estimatedPos?.let { pos ->
                Marker(
                    state = rememberMarkerState(position = pos),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    title = "Target"
                )
            }

            estimatedPos?.let { pos ->
                radius?.let { r ->
                    Circle(
                        center = pos,
                        radius = r,
                        fillColor = Color(0x4080FF),
                        strokeColor = Color(0xFF80FF),
                        strokeWidth = 4f
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Text("HUNTING → $targetMac", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("${samples.size} samples • ${radius?.toInt()?.let { "$it m" } ?: "Acquiring..."}", color = Color.Cyan)
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }

    LaunchedEffect(Unit) {
        HuntingViewModel.startHunting(targetMac, viewModel)
    }

    DisposableEffect(Unit) {
        onDispose { HuntingViewModel.stopHunting() }
    }
}

// THIS IS THE MAGIC — isolates the remember call so the compiler doesn't choke
@Composable
private fun rememberCameraPositionStateInSeparateComposable(): State<CameraPositionState> {
    return remember { mutableStateOf(CameraPositionState()) }
}