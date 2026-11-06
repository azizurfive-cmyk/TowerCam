package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.performGoogleSignIn
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
    val capturedPhotos by viewModel.capturedPhotos.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val liveTimestamp by viewModel.liveTimestamp.collectAsStateWithLifecycle()

    var showEditSiteDialog by remember { mutableStateOf(false) }
    var showFullScreenGallery by remember { mutableStateOf(false) }
    var showProfileDrawer by remember { mutableStateOf(false) }
    val anonymousId = remember { viewModel.getAnonymousTechnicianId() }

    // CameraX references
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }
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

    // Bind / Rebind CameraX when front/back camera toggles or when provider/view is ready
    LaunchedEffect(isFrontCamera, cameraProviderInstance, previewViewInstance) {
        val provider = cameraProviderInstance ?: return@LaunchedEffect
        val previewView = previewViewInstance ?: return@LaunchedEffect

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
            if (provider.hasCamera(cameraSelector)) {
                provider.unbindAll()
                cameraInstance = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                if (!isFrontCamera && isTorchOn && cameraInstance?.cameraInfo?.hasFlashUnit() == true) {
                    cameraInstance?.cameraControl?.enableTorch(true)
                }
            } else {
                Log.w("TowerCamView", "CameraSelector $cameraSelector not available on device")
            }
        } catch (e: Exception) {
            Log.e("TowerCamView", "Camera binding failed", e)
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
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { view ->
                    previewViewInstance = view
                    cameraProviderFuture.addListener({
                        cameraProviderInstance = cameraProviderFuture.get()
                    }, ContextCompat.getMainExecutor(ctx))
                }
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
                            .height(82.dp)
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

                        // Brand Name Row
                        Text(
                            text = "TowerCam, Tower Location",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TowerAmber
                        )
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
                // Gallery Thumbnail / Full-Screen Viewer Button
                val photoCount = capturedPhotos.size
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x44FFFFFF))
                        .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.loadGalleryPhotos()
                            showFullScreenGallery = true
                        }
                        .testTag("gallery_thumbnail_button"),
                    contentAlignment = Alignment.Center
                ) {
                    val latestPhoto = capturedPhotos.firstOrNull() ?: lastCapturedUri
                    if (latestPhoto != null) {
                        AsyncImage(
                            model = latestPhoto,
                            contentDescription = "Photos Gallery ($photoCount photos)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Photo Count Badge
                        if (photoCount > 0) {
                            Surface(
                                color = Color(0xCC0F172A),
                                shape = RoundedCornerShape(topStart = 6.dp),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Text(
                                    text = "$photoCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
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

                // Profile Icon Button (Opens Right-Sliding Drawer)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentUser != null) Color(0x3322D3EE) else Color(0x22FFFFFF))
                        .border(
                            1.5.dp,
                            if (currentUser != null) TowerCyanBright else Color(0x66FFFFFF),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { showProfileDrawer = true }
                        .testTag("profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUser?.photoUrl != null) {
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = "Technician Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = "Profile Drawer",
                            tint = if (currentUser != null) TowerCyanBright else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Connected Status Indicator Dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (currentUser != null) TowerGreen else TowerAmber)
                            .border(1.5.dp, Color.Black, CircleShape)
                    )
                }
            }
        }

        // 7. Full-Screen Swipeable Photo Gallery Viewer Overlay
        if (showFullScreenGallery) {
            FullScreenGalleryViewer(
                photos = capturedPhotos,
                onClose = { showFullScreenGallery = false }
            )
        }

        // 8. Right-Sliding Technician Profile Drawer Overlay
        AnimatedVisibility(
            visible = showProfileDrawer,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
        ) {
            ProfileSideDrawer(
                currentUser = currentUser,
                anonymousId = anonymousId,
                lastSubmittedCode = lastSubmittedCode,
                onClose = { showProfileDrawer = false },
                onSignInSuccess = onSignInSuccess,
                onSignOut = onSignOut
            )
        }
    }

    // 9. Quick Site Code Input Dialog
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickEditSiteCodeDialog(
    currentSiteCode: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textInput by remember { mutableStateOf(currentSiteCode) }
    val quickPrefixes = listOf("DHBDD", "FNDGN", "TWR", "CELL", "SITE")

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
                    placeholder = { Text("e.g. DHBDD01") },
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

@Composable
fun FullScreenGalleryViewer(
    photos: List<Uri>,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    if (photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "কোনো ছবি তোলা হয়নি",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ক্যামেরা দিয়ে ফটো তুলুন, ওয়াটারমার্ক সহ এখানে সম্পূর্ণ গ্যালারি ক্রমানুসারে দেখতে পাবেন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = TowerCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ক্যামেরায় ফিরুন", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { photos.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("full_screen_gallery_viewer")
    ) {
        // Horizontal Pager: Page 0 is the latest photo.
        // Swiping left moves to older photos from the right.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photos[page],
                    contentDescription = "Photo ${page + 1} of ${photos.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Navigation Bar
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xE6000000), Color.Transparent)
                    )
                )
                .padding(top = topPadding + 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x800F172A)),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("gallery_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Camera",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "গ্যালারি (TowerCam)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = if (pagerState.currentPage == 0) "সবচেয়ে সাম্প্রতিক ফটো (Latest)" else "পূর্ববর্তী ফটো (Past photo)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pagerState.currentPage == 0) TowerCyanBright else Color(0xFF94A3B8)
                    )
                }
            }

            // Photo Index Pill
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322D3EE))
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TowerCyanBright,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Navigation Hints
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000))
                    )
                )
                .padding(bottom = navBarPadding + 16.dp, start = 16.dp, end = 16.dp, top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "লোকাল স্টোরেজ: Pictures/TowerCam",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF94A3B8)
                )
                if (photos.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "← বামে সোয়াইপ করে পুরাতন ফটো দেখুন →",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TowerCyanBright
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSideDrawer(
    currentUser: com.google.firebase.auth.FirebaseUser?,
    anonymousId: String,
    lastSubmittedCode: String?,
    onClose: () -> Unit,
    onSignInSuccess: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_drawer_container")
    ) {
        // Semi-transparent scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onClose)
        )

        // Slide drawer surface on the right
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .widthIn(max = 360.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* absorb inner clicks */ }
                )
                .testTag("profile_drawer_sheet"),
            color = TowerDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322D3EE)),
            shadowElevation = 16.dp
        ) {
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusBarPadding + 16.dp,
                        bottom = navBarPadding + 16.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
                    .verticalScroll(rememberScrollState())
            ) {
                // Drawer Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x2206B6D4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TowerCyanBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "প্রোফাইল মেনু",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_profile_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Condition 1: User is NOT signed in
                if (currentUser == null) {
                    // Anonymous / Device ID Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TowerSlate),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = TowerAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ডিভাইস টেকনিশিয়ান আইডি:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = anonymousId,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                ),
                                color = TowerCyanBright,
                                modifier = Modifier.testTag("anonymous_technician_id_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notice Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66F59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = TowerAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "অননুমোদিত গেস্ট মোড",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TowerAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "আপনি সাইন ইন করেননি, দয়া করে সাইন ইন করে নিন",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "জিমেইল দিয়ে সাইন ইন করলে প্রতিটি টাওয়ারের সাইট কোড ও জিপিএস সেন্ট্রাল ডাটাবেজে আপনার অ্যাকাউন্টের অধীনে নিশ্চিতভাবে সিঙ্ক হবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (signInError != null) {
                        Text(
                            text = signInError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Sign In With Google Button
                    Button(
                        onClick = {
                            isSigningIn = true
                            signInError = null
                            performGoogleSignIn(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onSuccess = {
                                    isSigningIn = false
                                    onSignInSuccess()
                                    onClose()
                                },
                                onError = { err ->
                                    isSigningIn = false
                                    signInError = err
                                },
                                onCancelled = {
                                    isSigningIn = false
                                }
                            )
                        },
                        enabled = !isSigningIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TowerCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drawer_google_sign_in_button")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Zero-friction reminder
                    Text(
                        text = "💡 সাইন ইন বাধ্যতামূলক নয়: যে কোনো টেকনিশিয়ান সাইন ইন না করলেও সরাসরি ক্যামেরা দিয়ে সব ফটো তুলতে পারবেন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    // Condition 2: User IS signed in
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TowerSlate),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4422D3EE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar
                            if (currentUser.photoUrl != null) {
                                AsyncImage(
                                    model = currentUser.photoUrl,
                                    contentDescription = "User Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, TowerCyanBright, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color(0x2206B6D4), CircleShape)
                                        .border(2.dp, TowerCyanBright, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (currentUser.displayName?.take(1) ?: currentUser.email?.take(1) ?: "T").uppercase(),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = TowerCyanBright
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Name
                            Text(
                                text = currentUser.displayName ?: "টেকনিশিয়ান (Technician)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            // Email
                            Text(
                                text = currentUser.email ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Connected Badge
                            Surface(
                                color = Color(0x2210B981),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TowerGreen)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = TowerGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "গুগল সাইন ইন সক্রিয়",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TowerGreen
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Database Information
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "সেন্ট্রাল ডাটাবেজ তথ্য:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "• DB: TowerCamdatabase",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.White
                            )
                            Text(
                                text = "• Collection: towergpslocation",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.White
                            )
                            if (lastSubmittedCode != null) {
                                Text(
                                    text = "• সর্বশেষ রেকর্ডকৃত সাইট: $lastSubmittedCode",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TowerCyanBright
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sign Out Button
                    OutlinedButton(
                        onClick = {
                            performSignOut(context, credentialManager, scope) {
                                onSignOut()
                                onClose()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66EF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("drawer_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "সাইন আউট (Sign Out)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.height(24.dp))

                // Footer
                Text(
                    text = "TowerCam v1.0 • Azizur Rahman Central DB",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
