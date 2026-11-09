package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.R
import com.example.data.local.CachedSite
import com.example.data.local.CachedSiteDao
import com.example.data.local.TowerCamDatabase
import com.example.data.model.TowerLocation
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

sealed class LocationLogOutcome {
    data class CacheHit(val siteCode: String) : LocationLogOutcome()
    data class AlreadyVerified(val siteCode: String) : LocationLogOutcome()
    data class SlotsFull(val siteCode: String) : LocationLogOutcome()
    data class NewSiteLogged(val siteCode: String, val slotNumber: Int = 1) : LocationLogOutcome()
    data class SlotUpdated(val siteCode: String, val slotNumber: Int) : LocationLogOutcome()
}

class TowerLocationRepository(
    private val db: FirebaseFirestore,
    private val cachedSiteDao: CachedSiteDao,
    private val sharedPrefs: SharedPreferences? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        TowerCamDatabase.getInstance(context).cachedSiteDao(),
        context.getSharedPreferences("tower_cam_prefs", Context.MODE_PRIVATE)
    )

    // Secondary constructor for testing or mock injection
    constructor(db: FirebaseFirestore, sharedPrefs: SharedPreferences?) : this(
        db,
        object : CachedSiteDao {
            private val cache = mutableMapOf<String, CachedSite>()
            override suspend fun getCachedSite(siteCode: String) = cache[siteCode]
            override suspend fun getCount() = cache.size
            override suspend fun insertOrUpdate(site: CachedSite) { cache[site.siteCode] = site }
            override suspend fun deleteOldest(count: Int) {
                val oldestKeys = cache.values.sortedBy { it.cachedAt }.take(count).map { it.siteCode }
                oldestKeys.forEach { cache.remove(it) }
            }
            override suspend fun insertWithFifoLimit(site: CachedSite, maxLimit: Int) {
                if (!cache.containsKey(site.siteCode) && cache.size >= maxLimit) {
                    deleteOldest((cache.size - maxLimit) + 1)
                }
                cache[site.siteCode] = site
            }
            override suspend fun getAllCachedSites() = cache.values.sortedByDescending { it.cachedAt }
            override suspend fun clearAll() = cache.clear()
        },
        sharedPrefs
    )

    private val auth = Firebase.auth
    private val collectionName = "towergpslocation"

    fun getAnonymousTechnicianId(): String {
        val existing = sharedPrefs?.getString(KEY_DEVICE_UUID, null)
        if (existing != null) return "Technician_$existing"
        val newId = java.util.UUID.randomUUID().toString().take(6).uppercase()
        sharedPrefs?.edit()?.putString(KEY_DEVICE_UUID, newId)?.apply()
        return "Technician_$newId"
    }

    fun getTechnicianIdentifier(): String {
        return auth.currentUser?.email
            ?: auth.currentUser?.displayName
            ?: getAnonymousTechnicianId()
    }

    fun getLastSubmittedSiteCode(): String? {
        return sharedPrefs?.getString(KEY_LAST_SUBMITTED_SITE_CODE, null)
    }

    fun setLastSubmittedSiteCode(code: String) {
        sharedPrefs?.edit()?.putString(KEY_LAST_SUBMITTED_SITE_CODE, code)?.apply()
    }

    /**
     * Smart Caching & Multi-Technician Slot Architecture (Version 1.1):
     *
     * 1. Smart Local Cache Check:
     *    Checks Room local database for site_code.
     *    - Cache Hit: skips online query completely (saving bandwidth and Firestore reads/writes).
     *    - Cache Miss: saves site_code, latitude, longitude with FIFO (max 1,000 limit).
     *
     * 2. Single Cloud Query on Cache Miss:
     *    - If site doesn't exist online -> creates new doc (verification = "no", slot 1).
     *    - If site exists and verification == "yes" -> no changes/edits made.
     *    - If site exists and verification == "no":
     *         Checks technician slots (1 to 5).
     *         If slots < 5, fills the next available slot (technician_N, latitude_N, longitude_N, timestamp_N).
     *         If all 5 slots are full, skips.
     */
    suspend fun logTowerLocationIfNew(
        siteCode: String,
        latitude: Double,
        longitude: Double,
        timestampStr: String,
        technicianName: String? = null
    ): Result<LocationLogOutcome> {
        val sanitizedCode = siteCode.trim().uppercase()
        if (sanitizedCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Site Code cannot be empty"))
        }

        // Step 1: Check Local Cache
        val localCachedSite = try {
            cachedSiteDao.getCachedSite(sanitizedCode)
        } catch (e: Exception) {
            Log.w("TowerCamRepo", "Local cache check warning: ${e.message}")
            null
        }

        if (localCachedSite != null) {
            Log.d("TowerCamRepo", "Cache Hit: Site '$sanitizedCode' already exists in local DB. Skipping cloud query.")
            setLastSubmittedSiteCode(sanitizedCode)
            return Result.success(LocationLogOutcome.CacheHit(sanitizedCode))
        }

        // Cache Miss:
        // Save to Local Database with FIFO circular policy (max 1,000 items)
        try {
            cachedSiteDao.insertWithFifoLimit(
                CachedSite(
                    siteCode = sanitizedCode,
                    latitude = latitude,
                    longitude = longitude,
                    cachedAt = System.currentTimeMillis()
                ),
                maxLimit = 1000
            )
            Log.d("TowerCamRepo", "Cache Miss: Saved '$sanitizedCode' to local Room cache (FIFO limit 1,000)")
        } catch (e: Exception) {
            Log.e("TowerCamRepo", "Failed to insert into local cache", e)
        }

        // Step 2: Query Online Firestore (Single Document Query)
        return try {
            val docRef = db.collection(collectionName).document(sanitizedCode)
            val snapshot = docRef.get().await()
            val technician = technicianName ?: getTechnicianIdentifier()

            if (!snapshot.exists()) {
                // Online document does not exist yet -> Create new site with verification = "no" and slot 1
                val newDocPayload = mapOf(
                    "site_code" to sanitizedCode,
                    "verification" to "no",
                    "created_at" to com.google.firebase.Timestamp.now(),
                    "technician_1" to technician,
                    "latitude_1" to latitude,
                    "longitude_1" to longitude,
                    "timestamp_1" to timestampStr
                )
                docRef.set(newDocPayload).await()
                setLastSubmittedSiteCode(sanitizedCode)
                Log.d("TowerCamRepo", "Created new site '$sanitizedCode' with Slot 1 (verification=no)")
                Result.success(LocationLogOutcome.NewSiteLogged(sanitizedCode, slotNumber = 1))
            } else {
                // Online document exists! Check verification:
                val verification = snapshot.getString("verification") ?: "no"
                if (verification.equals("yes", ignoreCase = true)) {
                    Log.d("TowerCamRepo", "Site '$sanitizedCode' is already verified (yes). No edits or updates needed.")
                    setLastSubmittedSiteCode(sanitizedCode)
                    Result.success(LocationLogOutcome.AlreadyVerified(sanitizedCode))
                } else {
                    // verification is "no". Check which slot (1 through 5) is available.
                    val targetSlot = determineAvailableSlot(snapshot)
                    if (targetSlot == 0) {
                        Log.d("TowerCamRepo", "Site '$sanitizedCode' has all 5 technician slots filled. No additional slot update.")
                        setLastSubmittedSiteCode(sanitizedCode)
                        Result.success(LocationLogOutcome.SlotsFull(sanitizedCode))
                    } else {
                        val slotUpdate = mapOf(
                            "technician_$targetSlot" to technician,
                            "latitude_$targetSlot" to latitude,
                            "longitude_$targetSlot" to longitude,
                            "timestamp_$targetSlot" to timestampStr
                        )
                        docRef.update(slotUpdate).await()
                        setLastSubmittedSiteCode(sanitizedCode)
                        Log.d("TowerCamRepo", "Updated Site '$sanitizedCode' in Slot $targetSlot")
                        Result.success(LocationLogOutcome.SlotUpdated(sanitizedCode, targetSlot))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TowerCamRepo", "Failed to log/sync tower location with Firestore", e)
            Result.failure(e)
        }
    }

    private fun determineAvailableSlot(snapshot: DocumentSnapshot): Int {
        // Slot 1 is considered filled if latitude_1 or legacy latitude exists
        val slot1Filled = snapshot.getDouble("latitude_1") != null || snapshot.getDouble("latitude") != null
        if (!slot1Filled) return 1

        val slot2Filled = snapshot.getDouble("latitude_2") != null
        if (!slot2Filled) return 2

        val slot3Filled = snapshot.getDouble("latitude_3") != null
        if (!slot3Filled) return 3

        val slot4Filled = snapshot.getDouble("latitude_4") != null
        if (!slot4Filled) return 4

        val slot5Filled = snapshot.getDouble("latitude_5") != null
        if (!slot5Filled) return 5

        return 0 // All 5 slots are already filled
    }

    /**
     * Query tower locations logged in Firestore.
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

    suspend fun clearLocalCache() {
        cachedSiteDao.clearAll()
    }

    suspend fun getLocalCacheCount(): Int {
        return cachedSiteDao.getCount()
    }

    companion object {
        private const val KEY_LAST_SUBMITTED_SITE_CODE = "last_submitted_site_code"
        private const val KEY_DEVICE_UUID = "device_uuid"
    }
}
