package com.example.camera

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.TowerLocationRepository
import com.example.location.GpsLocationData
import com.example.location.LocationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(
    application: Application,
    private val repository: TowerLocationRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        TowerLocationRepository(application)
    )

    private val locationHelper = LocationHelper(application)
    val gpsLocation: StateFlow<GpsLocationData> = locationHelper.locationData

    private val prefs = application.getSharedPreferences("tower_cam_prefs", Context.MODE_PRIVATE)

    private val _siteCode = MutableStateFlow(
        prefs.getString(KEY_CURRENT_SITE_CODE, null)?.let { saved ->
            if (saved == "FNDGN27" || saved.isBlank()) "DHBDD01" else saved
        } ?: "DHBDD01"
    )
    val siteCode: StateFlow<String> = _siteCode.asStateFlow()

    private val _lastSubmittedSiteCode = MutableStateFlow<String?>(
        repository.getLastSubmittedSiteCode()
    )
    val lastSubmittedSiteCode: StateFlow<String?> = _lastSubmittedSiteCode.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _lastCapturedUri = MutableStateFlow<Uri?>(null)
    val lastCapturedUri: StateFlow<Uri?> = _lastCapturedUri.asStateFlow()

    private val _capturedPhotos = MutableStateFlow<List<Uri>>(emptyList())
    val capturedPhotos: StateFlow<List<Uri>> = _capturedPhotos.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _liveTimestamp = MutableStateFlow(getFormattedTimestamp())
    val liveTimestamp: StateFlow<String> = _liveTimestamp.asStateFlow()

    init {
        // Load initial photos saved in Pictures/TowerCam
        loadGalleryPhotos()

        // Clock ticker for live HUD timestamp
        viewModelScope.launch {
            while (isActive) {
                _liveTimestamp.value = getFormattedTimestamp()
                delay(1000L)
            }
        }
    }

    fun getAnonymousTechnicianId(): String {
        return repository.getAnonymousTechnicianId()
    }

    fun loadGalleryPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = WatermarkHelper.loadSavedPhotos(getApplication())
            withContext(Dispatchers.Main) {
                val current = _capturedPhotos.value
                val merged = (current + list).distinct()
                _capturedPhotos.value = merged
                if (merged.isNotEmpty() && _lastCapturedUri.value == null) {
                    _lastCapturedUri.value = merged.first()
                }
            }
        }
    }

    fun startTrackingLocation() {
        locationHelper.startLocationUpdates()
    }

    fun stopTrackingLocation() {
        locationHelper.stopLocationUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        locationHelper.stopLocationUpdates()
    }

    fun updateSiteCode(newCode: String) {
        val sanitized = newCode.trim().uppercase()
        if (sanitized.isNotBlank()) {
            _siteCode.value = sanitized
            prefs.edit().putString(KEY_CURRENT_SITE_CODE, sanitized).apply()
            Log.d("TowerCamVM", "Site Code updated to: $sanitized")
            showStatus("Site Code set to $sanitized")
        }
    }

    fun toggleTorch() {
        _isTorchOn.value = !_isTorchOn.value
    }

    fun toggleCameraFacing() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun showStatus(msg: String) {
        _statusMessage.value = msg
        viewModelScope.launch {
            delay(3500L)
            if (_statusMessage.value == msg) {
                _statusMessage.value = null
            }
        }
    }

    fun processCapturedImage(imageProxy: ImageProxy) {
        val currentSite = _siteCode.value
        val currentGps = gpsLocation.value
        val captureTimestamp = getFormattedTimestamp()

        _isCapturing.value = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Convert ImageProxy to Rotated Bitmap
                val rotatedBitmap = WatermarkHelper.imageProxyToRotatedBitmap(imageProxy)
                imageProxy.close()

                // 2. Bake watermark overlay directly onto the bitmap
                val watermarked = WatermarkHelper.applyWatermark(
                    sourceBitmap = rotatedBitmap,
                    siteCode = currentSite,
                    location = currentGps,
                    timestampString = captureTimestamp
                )

                // 3. Save locally to Gallery (DO NOT upload image to Firebase)
                val saveResult = WatermarkHelper.saveBitmapToGallery(
                    context = getApplication(),
                    bitmap = watermarked,
                    siteCode = currentSite
                )

                saveResult.onSuccess { uri ->
                    _lastCapturedUri.value = uri
                    _capturedPhotos.value = listOf(uri) + _capturedPhotos.value.filter { it != uri }
                    Log.d("TowerCamVM", "Photo saved locally: $uri")

                    // 4. Smart Firebase Firestore Integration:
                    // Check if siteCode has already been logged.
                    // Directly writes to Azizur Rahman's central Firestore database 'towergpslocation'!
                    launch(Dispatchers.IO) {
                        val firestoreResult = repository.logTowerLocationIfNew(
                            siteCode = currentSite,
                            latitude = currentGps.latitude,
                            longitude = currentGps.longitude,
                            timestampStr = captureTimestamp
                        )

                        withContext(Dispatchers.Main) {
                            _lastSubmittedSiteCode.value = repository.getLastSubmittedSiteCode()

                            firestoreResult.onSuccess { outcome ->
                                when (outcome) {
                                    is com.example.data.repository.LocationLogOutcome.CacheHit -> {
                                        showStatus("লোকাল ক্যাশে সংরক্ষিত ($currentSite) ✓")
                                    }
                                    is com.example.data.repository.LocationLogOutcome.AlreadyVerified -> {
                                        showStatus("সাইট $currentSite ভেরিফাইড (Yes) ✓")
                                    }
                                    is com.example.data.repository.LocationLogOutcome.NewSiteLogged -> {
                                        showStatus("সাইট $currentSite ডাটাবেজে যুক্ত হয়েছে (স্লট ১) ✓")
                                    }
                                    is com.example.data.repository.LocationLogOutcome.SlotUpdated -> {
                                        showStatus("সাইট $currentSite ডাটাবেজে আপডেট হয়েছে (স্লট ${outcome.slotNumber}) ✓")
                                    }
                                    is com.example.data.repository.LocationLogOutcome.SlotsFull -> {
                                        showStatus("সাইট $currentSite এর ৫টি স্লটই পূর্ণ রয়েছে ✓")
                                    }
                                }
                            }.onFailure { err ->
                                Log.w("TowerCamVM", "Firestore sync warning: ${err.message}")
                                showStatus("Saved to Gallery (Offline / Sync pending)")
                            }
                        }
                    }
                }.onFailure { err ->
                    Log.e("TowerCamVM", "Failed to save photo", err)
                    withContext(Dispatchers.Main) {
                        showStatus("Error saving photo: ${err.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                Log.e("TowerCamVM", "Capture processing error", e)
                withContext(Dispatchers.Main) {
                    showStatus("Capture failed: ${e.localizedMessage}")
                }
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun syncPendingLocations() {
        if (Firebase.auth.currentUser == null) return
        val currentSite = _siteCode.value
        val currentGps = gpsLocation.value
        val captureTimestamp = getFormattedTimestamp()

        viewModelScope.launch(Dispatchers.IO) {
            val firestoreResult = repository.logTowerLocationIfNew(
                siteCode = currentSite,
                latitude = currentGps.latitude,
                longitude = currentGps.longitude,
                timestampStr = captureTimestamp
            )
            withContext(Dispatchers.Main) {
                _lastSubmittedSiteCode.value = repository.getLastSubmittedSiteCode()
                firestoreResult.onSuccess { outcome ->
                    when (outcome) {
                        is com.example.data.repository.LocationLogOutcome.CacheHit -> {
                            showStatus("লোকাল ক্যাশে বিদ্যমান ($currentSite)")
                        }
                        is com.example.data.repository.LocationLogOutcome.AlreadyVerified -> {
                            showStatus("সাইট $currentSite ভেরিফাইড ✓")
                        }
                        is com.example.data.repository.LocationLogOutcome.NewSiteLogged -> {
                            showStatus("ক্লাউড সিঙ্ক: সাইট $currentSite যুক্ত হয়েছে (স্লট ১)")
                        }
                        is com.example.data.repository.LocationLogOutcome.SlotUpdated -> {
                            showStatus("ক্লাউড সিঙ্ক: সাইট $currentSite আপডেট হয়েছে (স্লট ${outcome.slotNumber})")
                        }
                        is com.example.data.repository.LocationLogOutcome.SlotsFull -> {
                            showStatus("ক্লাউড সিঙ্ক: সাইট $currentSite এর ৫টি স্লটই পূর্ণ")
                        }
                    }
                }
            }
        }
    }

    private fun getFormattedTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    companion object {
        private const val KEY_CURRENT_SITE_CODE = "key_current_site_code"
    }
}
