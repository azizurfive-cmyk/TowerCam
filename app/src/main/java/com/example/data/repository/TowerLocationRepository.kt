package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.R
import com.example.data.model.TowerLocation
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TowerLocationRepository(
    private val db: FirebaseFirestore,
    private val sharedPrefs: SharedPreferences? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        context.getSharedPreferences("tower_cam_prefs", Context.MODE_PRIVATE)
    )

    private val auth = Firebase.auth
    private val collectionName = "towergpslocation"

    private fun getTechnicianIdentifier(): String {
        return auth.currentUser?.email
            ?: auth.currentUser?.displayName
            ?: ("Technician_" + (sharedPrefs?.getString(KEY_DEVICE_UUID, null) ?: run {
                val newId = java.util.UUID.randomUUID().toString().take(6).uppercase()
                sharedPrefs?.edit()?.putString(KEY_DEVICE_UUID, newId)?.apply()
                newId
            }))
    }

    fun getLastSubmittedSiteCode(): String? {
        return sharedPrefs?.getString(KEY_LAST_SUBMITTED_SITE_CODE, null)
    }

    fun setLastSubmittedSiteCode(code: String) {
        sharedPrefs?.edit()?.putString(KEY_LAST_SUBMITTED_SITE_CODE, code)?.apply()
    }

    /**
     * Smart deduplication logic:
     * Only writes to central Firestore collection 'towergpslocation' when the Site Code
     * is new or has changed compared to lastSubmittedSiteCode.
     * Works for any user/technician on any device, storing all data in the project's central database.
     *
     * @return Result<Boolean> true if a new document was written to Firestore, false if skipped due to deduplication.
     */
    suspend fun logTowerLocationIfNew(
        siteCode: String,
        latitude: Double,
        longitude: Double,
        timestampStr: String
    ): Result<Boolean> {
        val sanitizedCode = siteCode.trim().uppercase()
        if (sanitizedCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Site Code cannot be empty"))
        }

        val lastCode = getLastSubmittedSiteCode()
        if (sanitizedCode.equals(lastCode, ignoreCase = true)) {
            Log.d("TowerCamRepo", "Skipping Firestore write: Site Code '$sanitizedCode' already logged.")
            return Result.success(false)
        }

        return try {
            val docRef = db.collection(collectionName).document(sanitizedCode)
            val technician = getTechnicianIdentifier()

            val payload = mapOf(
                "site_code" to sanitizedCode,
                "latitude" to latitude,
                "longitude" to longitude,
                "timestamp" to timestampStr,
                "technician" to technician,
                "created_at" to com.google.firebase.Timestamp.now()
            )

            docRef.set(payload).await()
            setLastSubmittedSiteCode(sanitizedCode)
            Log.d("TowerCamRepo", "Successfully logged new site '$sanitizedCode' directly to central Firestore collection '$collectionName'")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("TowerCamRepo", "Failed to log tower location to Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Query tower locations logged by the current authenticated technician.
     */
    fun observeTowerLocations(): Flow<List<TowerLocation>> {
        return db.collection(collectionName)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TowerLocation::class.java)
            }
    }

    suspend fun getTowerLocation(siteCode: String): Result<TowerLocation?> {
        val sanitizedCode = siteCode.trim().uppercase()
        return try {
            val doc = db.collection(collectionName).document(sanitizedCode).get().await()
            Result.success(doc.toObject(TowerLocation::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_LAST_SUBMITTED_SITE_CODE = "last_submitted_site_code"
        private const val KEY_DEVICE_UUID = "device_uuid"
    }
}
