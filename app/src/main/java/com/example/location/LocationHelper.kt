package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class GpsLocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracyMeters: Float = 0.0f,
    val isLocked: Boolean = false,
    val provider: String = "GPS"
) {
    val formattedCoordinates: String
        get() {
            if (!isLocked && latitude == 0.0 && longitude == 0.0) {
                return "GPS ACQUIRING..."
            }
            val latDir = if (latitude >= 0) "N" else "S"
            val lonDir = if (longitude >= 0) "E" else "W"
            return String.format(
                Locale.US,
                "%.6f° %s, %.6f° %s",
                kotlin.math.abs(latitude),
                latDir,
                kotlin.math.abs(longitude),
                lonDir
            )
        }

    val formattedAccuracy: String
        get() = if (accuracyMeters > 0) String.format(Locale.US, "±%.1fm", accuracyMeters) else "--"

    val formattedAltitude: String
        get() = if (altitude != 0.0) String.format(Locale.US, "%.1fm MSL", altitude) else "--"
}

class LocationHelper(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationData = MutableStateFlow(GpsLocationData())
    val locationData: StateFlow<GpsLocationData> = _locationData.asStateFlow()

    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { updateLocation(it) }
        }
    }

    private fun updateLocation(location: Location) {
        _locationData.value = GpsLocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracyMeters = location.accuracy,
            isLocked = true,
            provider = location.provider ?: "GPS"
        )
        Log.d("TowerCamGPS", "GPS Updated: ${location.latitude}, ${location.longitude} (±${location.accuracy}m)")
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTracking) return
        try {
            // First fetch last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { updateLocation(it) }
            }

            // High accuracy active request
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isTracking = true
            Log.d("TowerCamGPS", "Location updates requested")
        } catch (e: SecurityException) {
            Log.e("TowerCamGPS", "Permission missing for location updates", e)
        } catch (e: Exception) {
            Log.e("TowerCamGPS", "Failed to start location updates", e)
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            isTracking = false
        } catch (e: Exception) {
            Log.e("TowerCamGPS", "Failed to stop location updates", e)
        }
    }
}
