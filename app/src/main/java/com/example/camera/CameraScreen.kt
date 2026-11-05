package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.performSignOut
import com.example.ui.theme.TowerAmber
import com.example.ui.theme.TowerCyan
import com.example.ui.theme.TowerCyanBright
import com.example.ui.theme.TowerDark
import com.example.ui.theme.TowerGreen
import com.example.ui.theme.TowerSlate
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    currentUser: com.google.firebase.auth.FirebaseUser? = null,
    onSignInSuccess: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val siteCode by viewModel.siteCode.collectAsStateWithLifecycle()
    val lastSubmittedCode by viewModel.lastSubmittedSiteCode.collectAsStateWithLifecycle()
    val gpsData by viewModel.gpsLocation.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val lastCapturedUri by viewModel.lastCapturedUri.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val liveTimestamp by viewModel.liveTimestamp.collectAsStateWithLifecycle()

    var showEditSiteDialog by remember { mutableStateOf(false) }
    var previewPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // CameraX references
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraInstance: Camera? by remember { mutableStateOf(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(viewModel) {
        viewModel.startTrackingLocation()
        onDispose {
            viewModel.stopTrackingLocation()
            cameraExecutor.shutdown()
        }
    }

    // Toggle Torch when state changes
    LaunchedEffect(isTorchOn, cameraInstance) {
        cameraInstance?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isTorchOn)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. CameraX Preview View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    imageCapture = capture

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraInstance = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        Log.e("TowerCamView", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Viewfinder Reticle (Telecom Tower Crosshair HUD)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val reticleRadius = 48.dp.toPx()
            val reticleLine = 24.dp.toPx()
            val strokeWidth = 1.5.dp.toPx()
            val reticleColor = Color(0x6622D3EE)

            // Center circle
            drawCircle(
                color = reticleColor,
                radius = reticleRadius,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            drawCircle(
                color = reticleColor,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )

            // Crosshair ticks
            drawLine(reticleColor, Offset(cx - reticleRadius - reticleLine, cy), Offset(cx - reticleRadius + 6.dp.toPx(), cy), strokeWidth)
            drawLine(reticleColor, Offset(cx + reticleRadius - 6.dp.toPx(), cy), Offset(cx + reticleRadius + reticleLine, cy), strokeWidth)
            drawLine(reticleColor, Offset(cx, cy - reticleRadius - reticleLine), Offset(cx, cy - reticleRadius + 6.dp.toPx()), strokeWidth)
            drawLine(reticleColor, Offset(cx, cy + reticleRadius - 6.dp.toPx()), Offset(cx, cy + reticleRadius + reticleLine), strokeWidth)
        }

        // 3. Top Control Bar (GPS Status, Flashlight, Camera Flip, Sign Out)
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding + 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GPS Status Pill
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322D3EE)),
                modifier = Modifier.testTag("gps_status_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (gpsData.isLocked) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = "GPS Lock Status",
                        tint = if (gpsData.isLocked) TowerGreen else TowerAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (gpsData.isLocked) "GPS: ${gpsData.formattedAccuracy}" else "ACQUIRING GPS...",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Torch Toggle
                IconButton(
                    onClick = { viewModel.toggleTorch() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isTorchOn) TowerAmber else Color(0xCC0F172A)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("torch_button")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Torch",
                        tint = if (isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Switch Camera
                IconButton(
                    onClick = { viewModel.toggleCameraFacing() },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC0F172A)),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Central Database connection badge
                Surface(
                    color = Color(0xCC0F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3306B6D4)),
                    modifier = Modifier.testTag("database_status_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Central DB Connected",
                            tint = TowerGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Central DB",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 4. Status Message Toast / Banner
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarPadding + 64.dp, start = 24.dp, end = 24.dp)
        ) {
            Surface(
                color = Color(0xE60F172A),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TowerCyanBright),
                shadowElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = TowerCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                }
            }
        }

        // 5. Dynamic Watermark Overlay (Bottom-Left)
        // Displays: Latitude & Longitude, Timestamp, Custom Text / Site Code + Edit Icon beside it
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 110.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD0F172A)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x8006B6D4)),
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .testTag("watermark_overlay_card")
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    // Left cyan indicator accent bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(86.dp)
                            .background(TowerCyanBright, RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Site Code Header Row with Edit Icon right beside it
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SITE: ",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = siteCode,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    ),
                                    color = TowerCyanBright,
                                    modifier = Modifier.testTag("current_site_code_text")
                                )
                            }

                            // Small Edit Icon beside Site Code
                            IconButton(
                                onClick = { showEditSiteDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("edit_site_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Site Code",
                                    tint = TowerAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // GPS Coordinates Row
                        Text(
                            text = gpsData.formattedCoordinates,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFFF1F5F9)
                        )

                        // Live Timestamp Row
                        Text(
                            text = liveTimestamp,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFFCBD5E1)
                        )

                        // Cloud dedup indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isLogged = lastSubmittedCode?.equals(siteCode, ignoreCase = true) == true
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isLogged) TowerGreen else TowerAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLogged) "Central DB: Recorded ✓" else "Central DB: Auto-logging on capture",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isLogged) TowerGreen else TowerAmber
                            )
                        }
                    }
                }
            }
        }

        // 6. Bottom Shutter & Gallery Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail / Preview
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x44FFFFFF))
                        .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                        .clickable(enabled = lastCapturedUri != null) {
                            previewPhotoUri = lastCapturedUri
                        }
                        .testTag("gallery_thumbnail_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        AsyncImage(
                            model = lastCapturedUri,
                            contentDescription = "Last captured photo thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "No photos yet",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !isCapturing && imageCapture != null) {
                            val capture = imageCapture ?: return@clickable
                            viewModel.clearStatusMessage()

                            capture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        viewModel.processCapturedImage(image)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("TowerCamCapture", "Image capture failed", exception)
                                    }
                                }
                            )
                        }
                        .testTag("shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = TowerCyan,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(TowerDark)
                                .border(2.dp, TowerCyanBright, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capture photo",
                                tint = TowerCyanBright,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Spacer / Quick Site Badge
                Surface(
                    color = Color(0x3322D3EE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { showEditSiteDialog = true }
                        .testTag("site_code_quick_pill")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "SITE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = siteCode.take(7),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TowerCyanBright
                        )
                    }
                }
            }
        }
    }

    // 7. Quick Site Code Input Dialog
    if (showEditSiteDialog) {
        QuickEditSiteCodeDialog(
            currentSiteCode = siteCode,
            onDismiss = { showEditSiteDialog = false },
            onSave = { newCode ->
                viewModel.updateSiteCode(newCode)
                showEditSiteDialog = false
            }
        )
    }

    // 8. Full-screen Photo Preview Dialog
    if (previewPhotoUri != null) {
        AlertDialog(
            onDismissRequest = { previewPhotoUri = null },
            confirmButton = {
                Button(
                    onClick = { previewPhotoUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = TowerCyan)
                ) {
                    Text("Close", color = Color.Black)
                }
            },
            title = {
                Text("Watermarked Inspection Photo", color = Color.White)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = previewPhotoUri,
                        contentDescription = "Captured Inspection Photo with Watermark",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Saved in device Gallery under Pictures/TowerCam",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            containerColor = TowerSlate
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickEditSiteCodeDialog(
    currentSiteCode: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textInput by remember { mutableStateOf(currentSiteCode) }
    val quickPrefixes = listOf("FNDGN", "TWR", "CELL", "MW", "SITE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = TowerCyanBright,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Update Site Code",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Applies instantly to the live watermark and all subsequent photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it.uppercase().take(32) },
                    label = { Text("Site Code Identifier") },
                    placeholder = { Text("e.g. FNDGN27") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (textInput.isNotBlank()) onSave(textInput)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TowerCyanBright,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = TowerCyanBright,
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_code_input_field")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Quick pre-fills:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPrefixes.forEach { prefix ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.clickable {
                                textInput = "${prefix}${if (textInput.contains(Regex("[0-9]+"))) textInput.filter { it.isDigit() } else "01"}"
                            }
                        ) {
                            Text(
                                text = prefix,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = TowerCyanBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSave(textInput)
                    }
                },
                enabled = textInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TowerCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("save_site_code_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = TowerSlate
    )
}
