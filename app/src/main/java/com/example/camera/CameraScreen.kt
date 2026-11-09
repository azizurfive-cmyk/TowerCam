package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.TelegramAuthManager
import com.example.auth.TelegramUser
import com.example.ui.theme.TowerAmber
import com.example.ui.theme.TowerCyan
import com.example.ui.theme.TowerCyanBright
import com.example.ui.theme.TowerDark
import com.example.ui.theme.TowerGreen
import com.example.ui.theme.TowerSlate
import com.example.version.VersionState
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    telegramAuthManager: TelegramAuthManager,
    telegramUser: TelegramUser?
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

    // Version 1.3 State
    val isShutterSoundEnabled by viewModel.isShutterSoundEnabled.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val isRecordingVideo by viewModel.isRecordingVideo.collectAsStateWithLifecycle()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val zoomLevel by viewModel.zoomLevel.collectAsStateWithLifecycle()
    val versionState by viewModel.versionState.collectAsStateWithLifecycle()
    val cachedSiteCount by viewModel.cachedSiteCount.collectAsStateWithLifecycle()

    var showEditSiteDialog by remember { mutableStateOf(false) }
    var showFullScreenGallery by remember { mutableStateOf(false) }
    var showProfileDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val anonymousId = remember { viewModel.getAnonymousTechnicianId() }

    // Shutter Sound player with preloaded sound assets & debounce
    val soundPlayer = remember {
        MediaActionSound().apply {
            try {
                load(MediaActionSound.SHUTTER_CLICK)
                load(MediaActionSound.START_VIDEO_RECORDING)
                load(MediaActionSound.STOP_VIDEO_RECORDING)
            } catch (e: Exception) {
                Log.w("CameraScreen", "MediaActionSound preload warning: ${e.message}")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                soundPlayer.release()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

    var lastSoundPlayTime by remember { mutableStateOf(0L) }
    val playSound = remember(isShutterSoundEnabled, soundPlayer) {
        { soundId: Int ->
            if (isShutterSoundEnabled) {
                val now = System.currentTimeMillis()
                if (now - lastSoundPlayTime > 300L) {
                    lastSoundPlayTime = now
                    try {
                        soundPlayer.play(soundId)
                    } catch (e: Exception) {
                        Log.w("CameraScreen", "Sound play exception caught: ${e.message}")
                    }
                }
            }
        }
    }

    // Tap-to-Focus Reticle State
    var focusPosition by remember { mutableStateOf<Offset?>(null) }
    var isFocusVisible by remember { mutableStateOf(false) }
    val focusScale = remember { Animatable(1.4f) }

    // CameraX references
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var activeRecording: Recording? by remember { mutableStateOf(null) }
    var cameraInstance: Camera? by remember { mutableStateOf(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(viewModel) {
        viewModel.startTrackingLocation()
        onDispose {
            viewModel.stopTrackingLocation()
            activeRecording?.stop()
            cameraExecutor.shutdown()
        }
    }

    // Audio record permission launcher for Video mode
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    // Toggle Torch when state changes
    LaunchedEffect(isTorchOn, cameraInstance) {
        cameraInstance?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isTorchOn)
            }
        }
    }

    // Bind / Rebind CameraX when front/back camera, mode, or previewView changes
    LaunchedEffect(isFrontCamera, captureMode, cameraProviderInstance, previewViewInstance) {
        val provider = cameraProviderInstance ?: return@LaunchedEffect
        val previewView = previewViewInstance ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            if (provider.hasCamera(cameraSelector)) {
                provider.unbindAll()

                if (captureMode == "PHOTO") {
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(85)
                        .build()
                    imageCapture = capture
                    videoCapture = null

                    cameraInstance = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        capture
                    )
                } else {
                    // Video Mode
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build()
                    val vidCap = VideoCapture.withOutput(recorder)
                    videoCapture = vidCap
                    imageCapture = null

                    cameraInstance = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        vidCap
                    )
                }

                // Restore zoom level
                cameraInstance?.cameraControl?.setZoomRatio(zoomLevel)

                // Restore torch if on back camera
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

    // Watch video recording duration limit (2 minutes = 120 seconds)
    LaunchedEffect(recordingDurationSeconds) {
        if (recordingDurationSeconds >= 120 && isRecordingVideo) {
            activeRecording?.stop()
            activeRecording = null
            viewModel.stopVideoRecordingTimer()
            playSound(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. CameraX Preview View + Gestures (Pinch to Zoom & Tap to Focus)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraInstance) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        val currentRatio = cameraInstance?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                        val maxRatio = cameraInstance?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 8f
                        val minRatio = cameraInstance?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
                        val targetZoom = (currentRatio * zoomChange).coerceIn(minRatio, minOf(8f, maxRatio))
                        viewModel.setZoomLevel(targetZoom)
                        cameraInstance?.cameraControl?.setZoomRatio(targetZoom)
                    }
                }
                .pointerInput(previewViewInstance, cameraInstance) {
                    detectTapGestures { offset ->
                        focusPosition = offset
                        isFocusVisible = true
                        scope.launch {
                            focusScale.snapTo(1.4f)
                            focusScale.animateTo(1.0f, tween(250, easing = FastOutSlowInEasing))
                            kotlinx.coroutines.delay(1500)
                            isFocusVisible = false
                        }

                        previewViewInstance?.let { pv ->
                            try {
                                val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                )
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                cameraInstance?.cameraControl?.startFocusAndMetering(action)
                            } catch (e: Exception) {
                                Log.w("TowerCamFocus", "FocusMetering failed: ${e.message}")
                            }
                        }
                    }
                }
        ) {
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
        }

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

        // 3. Dynamic Tap-to-Focus Box Indicator
        if (isFocusVisible && focusPosition != null) {
            val density = LocalDensity.current
            val boxSizeDp = 64.dp
            val boxSizePx = with(density) { boxSizeDp.toPx() }
            val xOffset = (focusPosition!!.x - boxSizePx / 2f).roundToInt()
            val yOffset = (focusPosition!!.y - boxSizePx / 2f).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(xOffset, yOffset) }
                    .size(boxSizeDp)
                    .graphicsLayer {
                        scaleX = focusScale.value
                        scaleY = focusScale.value
                    }
                    .border(1.5.dp, TowerCyanBright, RoundedCornerShape(8.dp))
            ) {
                // Focus center indicator dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(TowerAmber, CircleShape)
                        .align(Alignment.Center)
                )
            }
        }

        // 4. Quick Zoom Presets Pill (Floating on Viewfinder)
        Surface(
            color = Color(0x990F172A),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322D3EE)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .testTag("zoom_presets_pill")
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                listOf(1.0f, 2.0f, 3.0f).forEach { zoomTarget ->
                    val isSelected = kotlin.math.abs(zoomLevel - zoomTarget) < 0.2f
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) TowerCyanBright else Color.Transparent)
                            .clickable {
                                viewModel.setZoomLevel(zoomTarget)
                                cameraInstance?.cameraControl?.setZoomRatio(zoomTarget)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${zoomTarget.toInt()}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        // 5. Top Control Bar (GPS Status, Flashlight, Camera Flip, Settings)
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

            // Action Buttons (Torch, Camera Switch, Settings)
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
                        .size(42.dp)
                        .testTag("torch_button")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Torch",
                        tint = if (isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Switch Camera
                IconButton(
                    onClick = { viewModel.toggleCameraFacing() },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC0F172A)),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings & Options Dialog
                IconButton(
                    onClick = {
                        viewModel.refreshCacheCount()
                        showSettingsDialog = true
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC0F172A)),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "TowerCam Settings",
                        tint = TowerCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 6. Status Message Toast / Banner
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

        // 7. Dynamic Watermark Overlay (Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 148.dp, end = 16.dp)
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
                        // Site Code Header Row: Clickable anywhere to edit site code
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showEditSiteDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
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

        // 8. Bottom Controls: Mode Selector & Shutter Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
        ) {
            // Mode Selector Pill (PHOTO / VIDEO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x800F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4422D3EE))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo Mode Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (captureMode == "PHOTO") TowerCyanBright else Color.Transparent)
                                .clickable {
                                    if (!isRecordingVideo) {
                                        viewModel.setCaptureMode("PHOTO")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("mode_photo_button")
                        ) {
                            Text(
                                text = "ফটো (Photo)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (captureMode == "PHOTO") Color.Black else Color.White
                            )
                        }

                        // Video Mode Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (captureMode == "VIDEO") Color(0xFFEF4444) else Color.Transparent)
                                .clickable {
                                    if (!isRecordingVideo) {
                                        if (!hasAudioPermission) {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                        viewModel.setCaptureMode("VIDEO")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("mode_video_button")
                        ) {
                            Text(
                                text = "ভিডিও (Video)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Video Recording Timer HUD (Only visible in Video mode while recording)
            if (captureMode == "VIDEO" && isRecordingVideo) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                val minutes = recordingDurationSeconds / 60
                val seconds = recordingDurationSeconds % 60
                val timeFormatted = String.format("%02d:%02d", minutes, seconds)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REC $timeFormatted / 02:00",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFFEF4444)
                    )
                }
            }

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
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

                // Shutter / Record Button
                if (captureMode == "PHOTO") {
                    // PHOTO SHUTTER
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

                                playSound(MediaActionSound.SHUTTER_CLICK)

                                capture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val technicianIdentifier = telegramUser?.let { user ->
                                                val handle = if (!user.username.isNullOrBlank()) "@${user.username}" else "ID:${user.id}"
                                                "${user.displayName} ($handle)"
                                            }
                                            viewModel.processCapturedImage(image, technicianIdentifier)
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
                } else {
                    // VIDEO RECORD SHUTTER
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                val vidCap = videoCapture ?: return@clickable

                                if (isRecordingVideo) {
                                    // Stop Video Recording
                                    activeRecording?.stop()
                                    activeRecording = null
                                    viewModel.stopVideoRecordingTimer()
                                    playSound(MediaActionSound.STOP_VIDEO_RECORDING)
                                } else {
                                    // Start Video Recording
                                    val fileName = "TowerCam_VID_${siteCode}_${System.currentTimeMillis()}"
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/TowerCam")
                                    }

                                    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                                        context.contentResolver,
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    )
                                        .setContentValues(contentValues)
                                        .build()

                                    val pendingRecording = vidCap.output
                                        .prepareRecording(context, mediaStoreOutput)
                                        .apply {
                                            if (hasAudioPermission && ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                withAudioEnabled()
                                            }
                                        }

                                    playSound(MediaActionSound.START_VIDEO_RECORDING)

                                    viewModel.startVideoRecordingTimer()

                                    activeRecording = pendingRecording.start(
                                        ContextCompat.getMainExecutor(context)
                                    ) { recordEvent ->
                                        when (recordEvent) {
                                            is VideoRecordEvent.Finalize -> {
                                                viewModel.stopVideoRecordingTimer()
                                                if (!recordEvent.hasError()) {
                                                    Log.d("TowerCamVid", "Video saved: ${recordEvent.outputResults.outputUri}")
                                                } else {
                                                    Log.e("TowerCamVid", "Video error: ${recordEvent.error}")
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                            .testTag("video_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecordingVideo) {
                            // Square Stop Icon
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444))
                            )
                        } else {
                            // Red Solid Circle for Record Start
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }
                }

                // Profile Icon Button (Opens Right-Sliding Drawer)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (telegramUser != null) Color(0x33229ED9) else Color(0x22FFFFFF))
                        .border(
                            1.5.dp,
                            if (telegramUser != null) Color(0xFF229ED9) else Color(0x66FFFFFF),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { showProfileDrawer = true }
                        .testTag("profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (!telegramUser?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = telegramUser.photoUrl,
                            contentDescription = "Technician Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = if (telegramUser != null) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = "Profile Drawer",
                            tint = if (telegramUser != null) Color(0xFF229ED9) else Color.White,
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
                            .background(if (telegramUser != null) TowerGreen else TowerAmber)
                            .border(1.5.dp, Color.Black, CircleShape)
                    )
                }
            }
        }

        // 9. Full-Screen Swipeable Photo Gallery Viewer Overlay (with Zoom & Delete)
        if (showFullScreenGallery) {
            FullScreenGalleryViewer(
                photos = capturedPhotos,
                onDeletePhoto = { uriToDelete ->
                    viewModel.deletePhoto(uriToDelete)
                },
                onClose = { showFullScreenGallery = false }
            )
        }

        // 10. Right-Sliding Technician Profile Drawer Overlay
        AnimatedVisibility(
            visible = showProfileDrawer,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
        ) {
            ProfileSideDrawer(
                telegramAuthManager = telegramAuthManager,
                telegramUser = telegramUser,
                anonymousId = anonymousId,
                lastSubmittedCode = lastSubmittedCode,
                onClose = { showProfileDrawer = false }
            )
        }
    }

    // 11. Quick Site Code Input Dialog
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

    // 12. Settings & Version Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            isShutterSoundEnabled = isShutterSoundEnabled,
            cachedSiteCount = cachedSiteCount,
            onToggleShutterSound = { viewModel.setShutterSoundEnabled(it) },
            onClearCache = { viewModel.clearLocalCache() },
            onCheckForUpdates = { viewModel.checkForUpdates(forceCheck = true) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 13. Blocking Version Modal (When current app version is blocked in Firestore)
    if (versionState is VersionState.Blocked) {
        val blockedState = versionState as VersionState.Blocked
        VersionBlockedDialog(
            downloadUrl = blockedState.updateLink,
            context = context
        )
    }
}

@Composable
fun SettingsDialog(
    isShutterSoundEnabled: Boolean,
    cachedSiteCount: Int,
    onToggleShutterSound: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TowerCyanBright,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TowerCam সেটিংস",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Shutter Sound Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isShutterSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (isShutterSoundEnabled) TowerCyanBright else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ক্যামেরা শাটার সাউন্ড",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                                Text(
                                    text = if (isShutterSoundEnabled) "ছবি ও ভিডিওতে ক্লিক সাউন্ড হবে" else "নীরব মোড (Mute)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Switch(
                            checked = isShutterSoundEnabled,
                            onCheckedChange = onToggleShutterSound,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = TowerCyanBright,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("shutter_sound_switch")
                        )
                    }
                }

                // 2. Room Local Site Cache Manager
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "স্মার্ট ক্যাশিং (Smart Caching)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "লোকাল মেমোরিতে $cachedSiteCount টি সাইট সংরক্ষিত আছে। ক্যাশে থাকলে অনলাইন কুয়েরি না করে তাৎক্ষণিক ভেরিফিকেশন পাওয়া যায়।",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66EF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("লোকাল ক্যাশ সম্পূর্ণ পরিষ্কার করুন")
                        }
                    }
                }

                // 3. Version & Firestore Check Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "অ্যাপ ভার্সন:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                            Surface(
                                color = Color(0x2222D3EE),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TowerCyanBright)
                            ) {
                                Text(
                                    text = "v1.3 (Active)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = TowerCyanBright,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "র‌্যান্ডম ইন্টারভাল পলিসির মাধ্যমে ফায়ারস্টোর রিড অপ্টিমাইজড রাখা হয়।",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )

                        Button(
                            onClick = onCheckForUpdates,
                            colors = ButtonDefaults.buttonColors(containerColor = TowerCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নতুন ভার্সন চেক করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TowerCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("settings_done_button")
            ) {
                Text("ঠিক আছে", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = TowerSlate
    )

    // Clear Cache confirmation alert
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("ক্যাশ মুছে ফেলার নিশ্চয়তা", color = Color.White) },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত লোকাল ডাটাবেজের সমস্ত সাইট কোড ক্যাশ মুছে ফেলতে চান? অনলাইন সার্ভার থেকে পুনরায় তথ্য ডাউনলোড করতে হবে।",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearCache()
                        showClearCacheConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = TowerSlate
        )
    }
}

@Composable
fun VersionBlockedDialog(
    downloadUrl: String?,
    context: Context
) {
    val targetUrl = downloadUrl ?: "https://t.me/towercamera"

    AlertDialog(
        onDismissRequest = { /* Non-dismissible */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TowerAmber,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "অ্যাপ আপডেট প্রয়োজন (v1.3)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "আপনার ব্যবহৃত TowerCam সংস্করণটি কেন্দ্রীয়ভাবে বন্ধ করা হয়েছে।",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    text = "নতুন ফিচার, উচ্চতর স্পিড এবং সেন্ট্রাল ডাটাবেজ সুরক্ষার জন্য টেলিগ্রাম চ্যানেল থেকে সর্বশেষ আপডেট ইনস্টল করে নিন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ডাউনলোড লিঙ্ক:\n$targetUrl",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TowerCyanBright,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("TowerCamUpdate", "Failed to launch update URL", e)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TowerCyan, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_now_button")
            ) {
                Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("এখনই আপডেট করুন (Update Now)", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = TowerSlate
    )
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
    onDeletePhoto: (Uri) -> Unit,
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

    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isCurrentPhotoZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("full_screen_gallery_viewer")
    ) {
        // Horizontal Pager: Page 0 is latest photo.
        // Pager scrolling is allowed when photo is at normal scale (scale <= 1.05f).
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPhotoZoomed
        ) { page ->
            var scale by remember(page) { mutableFloatStateOf(1.0f) }
            var offsetX by remember(page) { mutableFloatStateOf(0f) }
            var offsetY by remember(page) { mutableFloatStateOf(0f) }

            // Sync zoomed state to pager
            LaunchedEffect(scale, pagerState.currentPage) {
                if (pagerState.currentPage == page) {
                    isCurrentPhotoZoomed = (scale > 1.05f)
                }
            }

            // When page changes, reset zoom
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page && scale > 1.05f) {
                    scale = 1.0f
                    offsetX = 0f
                    offsetY = 0f
                }
            }

            var lastTapTime by remember(page) { mutableStateOf(0L) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var moved = false
                            var totalPanX = 0f
                            var totalPanY = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size

                                if (pointerCount >= 2) {
                                    moved = true
                                    val rawZoom = event.calculateZoom()
                                    val pan = event.calculatePan()

                                    // Accelerated inward pinch: When pinching in (rawZoom < 1.0f),
                                    // accelerate zoom reduction so user can easily collapse zoom in a single stroke!
                                    val effectiveZoom = if (rawZoom < 1.0f) {
                                        1.0f - (1.0f - rawZoom) * 2.2f
                                    } else {
                                        rawZoom
                                    }

                                    val newScale = (scale * effectiveZoom).coerceIn(1.0f, 4.5f)

                                    // Auto-snap to 1.0x if pinched close to normal scale
                                    if (newScale < 1.15f) {
                                        scale = 1.0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = newScale
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pointerCount == 1 && scale > 1.05f) {
                                    val pan = event.calculatePan()
                                    totalPanX += kotlin.math.abs(pan.x)
                                    totalPanY += kotlin.math.abs(pan.y)

                                    if (totalPanX > 6f || totalPanY > 6f) {
                                        moved = true
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            val upTime = System.currentTimeMillis()
                            val pressDuration = upTime - downTime

                            // Double-tap detector: Tap without drag, duration < 350ms
                            if (!moved && pressDuration < 350L) {
                                val timeSinceLastTap = upTime - lastTapTime
                                if (timeSinceLastTap < 380L) {
                                    // DOUBLE-TAP TRIGGERED!
                                    if (scale > 1.05f) {
                                        // Instant zoom-out to normal 1.0x
                                        scale = 1.0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        // Zoom in to 2.5x
                                        scale = 2.5f
                                    }
                                    lastTapTime = 0L
                                } else {
                                    lastTapTime = upTime
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photos[page],
                    contentDescription = "Photo ${page + 1} of ${photos.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )

                // Quick Floating 1.0x Reset Button when zoomed in
                AnimatedVisibility(
                    visible = scale > 1.05f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 96.dp, end = 16.dp)
                ) {
                    Surface(
                        color = Color(0xDD0F172A),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TowerCyanBright),
                        modifier = Modifier.clickable {
                            scale = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Zoom",
                                tint = TowerCyanBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "১.০x রিসেট",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Left Navigation Arrow (Newer photo)
        if (photos.size > 1 && pagerState.currentPage > 0) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x990F172A)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(46.dp)
                    .testTag("gallery_prev_arrow")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Photo",
                    tint = Color.White
                )
            }
        }

        // Right Navigation Arrow (Older photo)
        if (photos.size > 1 && pagerState.currentPage < photos.size - 1) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x990F172A)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(46.dp)
                    .testTag("gallery_next_arrow")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Photo",
                    tint = Color.White
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
                        text = if (pagerState.currentPage == 0) "সবচেয়ে সাম্প্রতিক ফটো (Latest)" else "পূর্ববর্তী ফটো #${pagerState.currentPage + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pagerState.currentPage == 0) TowerCyanBright else Color(0xFF94A3B8)
                    )
                }
            }

            // Right Actions: Photo Index Pill & Delete Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                // Delete Photo Button
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x80EF4444)),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("delete_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Navigation Bar with Thumbnails & Hint
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
                    )
                )
                .padding(bottom = navBarPadding + 12.dp, start = 12.dp, end = 12.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal Thumbnail Strip (Click to jump to photo)
            if (photos.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    itemsIndexed(photos) { index, uri ->
                        val isSelected = (index == pagerState.currentPage)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) TowerCyanBright else Color(0x44FFFFFF),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Photo $index",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Text(
                text = "← বামে সোয়াইপ বা তীরে ট্যাপ করে পুরাতন ফটো দেখুন • ডাবল ট্যাপে জুম →",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = TowerCyanBright,
                textAlign = TextAlign.Center
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ছবি ডিলিট করতে চান?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "এই ছবিটি ডিভাইস মেমোরি (Pictures/TowerCam) থেকে স্থায়ীভাবে মুছে যাবে। আপনি কি নিশ্চিত?",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentUri = photos.getOrNull(pagerState.currentPage)
                        if (currentUri != null) {
                            onDeletePhoto(currentUri)
                        }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_photo_button")
                ) {
                    Text("মুছে ফেলুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = TowerSlate
        )
    }
}

@Composable
fun ProfileSideDrawer(
    telegramAuthManager: TelegramAuthManager,
    telegramUser: TelegramUser?,
    anonymousId: String,
    lastSubmittedCode: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isWaitingApproval by telegramAuthManager.isWaitingApproval.collectAsStateWithLifecycle()
    val authStatusMessage by telegramAuthManager.authStatusMessage.collectAsStateWithLifecycle()
    val timeoutSeconds by telegramAuthManager.timeoutSecondsRemaining.collectAsStateWithLifecycle()
    val formattedTime = remember(timeoutSeconds) {
        val m = timeoutSeconds / 60
        val s = timeoutSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    val telegramBlue = Color(0xFF229ED9)

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

                // Condition 1: User is NOT logged in with Telegram
                if (telegramUser == null) {
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

                    // Telegram Bot Optional Benefit Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x1A229ED9)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55229ED9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(telegramBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✈",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "টেলিগ্রাম লগইন (ঐচ্ছিক)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = telegramBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "লগইন সম্পূর্ণ অপশনাল! লগইন ছাড়াও আপনি ক্যামেরার সব ছবি ও ভিডিও ফ্রিলি তুলতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "যাদের টেলিগ্রাম রয়েছে তারা লগইন করলে টেলিগ্রাম টাওয়ার লোকেশন বটের (@TowerCam_Login_bot) অতিরিক্ত ক্রেডিট সুবিধা উপভোগ করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (authStatusMessage != null) {
                        Text(
                            text = authStatusMessage!!,
                            color = TowerCyanBright,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Telegram Waiting or Action State
                    if (isWaitingApproval) {
                        // Countdown & Approval Waiting Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = telegramBlue,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "টেলিগ্রামে অনুমোদনের অপেক্ষায়...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "বট (@TowerCam_Login_bot) এ কনফার্ম করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0x33229ED9),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "সময় বাকি: $formattedTime",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = TowerCyanBright,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { telegramAuthManager.cancelWaiting() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("বাতিল করুন", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        // 1-Click Login with Telegram Button
                        Button(
                            onClick = {
                                telegramAuthManager.startTelegramLogin(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = telegramBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("drawer_telegram_login_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✈",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Login with Telegram",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Zero-barrier reminder
                    Text(
                        text = "💡 মনে রাখবেন: লগইন একদমই বাধ্যতামূলক নয়। ক্যামেরার ফটো, ভিডিও এবং সেন্ট্রাল ডাটাবেজে সাইট রেকর্ড আপনি লগইন ছাড়াই করতে পারবেন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    // Condition 2: User IS logged in with Telegram
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TowerSlate),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66229ED9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Telegram Avatar
                            if (!telegramUser.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = telegramUser.photoUrl,
                                    contentDescription = "Telegram Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, telegramBlue, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color(0x33229ED9), CircleShape)
                                        .border(2.dp, telegramBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (telegramUser.displayName.take(1)).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = telegramBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Name
                            Text(
                                text = telegramUser.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            // Username / ID
                            Text(
                                text = if (!telegramUser.username.isNullOrBlank()) "@${telegramUser.username}" else "Telegram ID: ${telegramUser.id}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = telegramBlue
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Connected Badge with Credit Benefit
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
                                        text = "টেলিগ্রাম বট ক্রেডিট সুবিধা সক্রিয় ✓",
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
                            telegramAuthManager.logout()
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
                            text = "টেলিগ্রাম লগআউট (Sign Out)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.height(24.dp))

                // Footer
                Text(
                    text = "TowerCam v1.3 • Azizur Rahman Central DB",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
