package com.example.version

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class VersionState {
    object Initial : VersionState()
    object Checking : VersionState()
    data class Allowed(val version: String, val nextCheckTime: Long = 0L) : VersionState()
    data class Blocked(val version: String, val updateLink: String) : VersionState()
    data class Error(val message: String) : VersionState()
}

class VersionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("tower_cam_version_prefs", Context.MODE_PRIVATE)
    val currentVersion: String = "1.4"

    private val _versionState = MutableStateFlow<VersionState>(getCachedVersionState())
    val versionState: StateFlow<VersionState> = _versionState.asStateFlow()

    private fun getCachedVersionState(): VersionState {
        val isBlocked = prefs.getBoolean(KEY_IS_BLOCKED, false)
        val updateLink = prefs.getString(KEY_UPDATE_LINK, DEFAULT_TELEGRAM_LINK) ?: DEFAULT_TELEGRAM_LINK
        val nextCheckTime = prefs.getLong(KEY_NEXT_CHECK_TIME, 0L)

        return if (isBlocked) {
            VersionState.Blocked(currentVersion, updateLink)
        } else {
            VersionState.Allowed(currentVersion, nextCheckTime)
        }
    }

    /**
     * Checks Firestore for version authorization using a Random Interval strategy.
     * Prevents excessive Firestore reads by caching the authorization state locally
     * and only querying once every 15-30 days per device at random times.
     */
    suspend fun checkVersion(forceCheck: Boolean = false): VersionState = withContext(Dispatchers.IO) {
        val nextCheckTime = prefs.getLong(KEY_NEXT_CHECK_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        // If not forced and interval has not elapsed, use cached state with ZERO Firestore reads!
        if (!forceCheck && nextCheckTime > 0L && currentTime < nextCheckTime) {
            val cached = getCachedVersionState()
            _versionState.value = cached
            Log.d("VersionManager", "Version check skipped: Using cached status. Next check in ${((nextCheckTime - currentTime) / (24 * 3600 * 1000L))} days.")
            return@withContext cached
        }

        _versionState.value = VersionState.Checking

        try {
            val db = FirebaseFirestore.getInstance(
                context.getString(R.string.firestore_database_id)
            )

            // Collection: 'setting option', Document: 'update version'
            val docSnapshot = db.collection("setting option").document("update version").get().await()

            if (!docSnapshot.exists()) {
                Log.w("VersionManager", "Update version document does not exist in Firestore. Defaulting to allowed.")
                val allowedState = VersionState.Allowed(currentVersion)
                _versionState.value = allowedState
                return@withContext allowedState
            }

            // Check field for current version (e.g. '1.2' or '1_2')
            val status = docSnapshot.getString(currentVersion)
                ?: docSnapshot.getString(currentVersion.replace(".", "_"))
                ?: "Yes"

            val updateLink = docSnapshot.getString("New_app_link")
                ?: docSnapshot.getString("new_app_link")
                ?: DEFAULT_TELEGRAM_LINK

            val isBlocked = status.trim().equals("No", ignoreCase = true)

            // Random interval between 15 and 30 days to distribute Firestore reads across devices
            val randomDays = (15..30).random()
            val newNextCheckTime = currentTime + (randomDays * 24L * 3600L * 1000L)

            prefs.edit()
                .putBoolean(KEY_IS_BLOCKED, isBlocked)
                .putString(KEY_UPDATE_LINK, updateLink)
                .putLong(KEY_NEXT_CHECK_TIME, newNextCheckTime)
                .putLong(KEY_LAST_CHECK_TIME, currentTime)
                .apply()

            val resultState = if (isBlocked) {
                Log.w("VersionManager", "Version $currentVersion is marked as BLOCKED in Firestore (Status: $status)")
                VersionState.Blocked(currentVersion, updateLink)
            } else {
                Log.d("VersionManager", "Version $currentVersion is authorized in Firestore (Status: $status). Next check in $randomDays days.")
                // If a Telegram technician is logged in, mark their app activity in the users collection
                recordTelegramUserMonthlyActivity(db)
                VersionState.Allowed(currentVersion, newNextCheckTime)
            }

            _versionState.value = resultState
            resultState
        } catch (e: Exception) {
            Log.e("VersionManager", "Error checking version in Firestore: ${e.message}", e)
            // In case of network error, do not block the user; fallback to cached state
            val fallback = getCachedVersionState()
            _versionState.value = fallback
            fallback
        }
    }

    private fun recordTelegramUserMonthlyActivity(db: FirebaseFirestore) {
        try {
            val telegramPrefs = context.getSharedPreferences("tower_cam_telegram_auth", Context.MODE_PRIVATE)
            val telegramUserId = telegramPrefs.getLong("telegram_user_id", 0L)
            if (telegramUserId > 0L) {
                val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())

                db.collection("users")
                    .document(telegramUserId.toString())
                    .set(
                        mapOf("last_software_update" to nowIso),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                Log.d("VersionManager", "Recorded last_software_update for Telegram user $telegramUserId: $nowIso")
            }
        } catch (e: Exception) {
            Log.w("VersionManager", "Could not record user monthly activity: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_TELEGRAM_LINK = "https://t.me/towercamera"
        private const val KEY_IS_BLOCKED = "key_is_blocked"
        private const val KEY_UPDATE_LINK = "key_update_link"
        private const val KEY_NEXT_CHECK_TIME = "key_next_check_time"
        private const val KEY_LAST_CHECK_TIME = "key_last_check_time"
    }
}
