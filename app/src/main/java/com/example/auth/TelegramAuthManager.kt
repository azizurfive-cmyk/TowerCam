package com.example.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class TelegramUser(
    val id: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val username: String? = null,
    val photoUrl: String? = null,
    val authDate: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() {
            val full = "$firstName $lastName".trim()
            return if (full.isNotBlank()) full else (username ?: "Telegram User")
        }
}

class TelegramAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tower_cam_telegram_auth", Context.MODE_PRIVATE)

    private val _telegramUser = MutableStateFlow<TelegramUser?>(loadSavedUser())
    val telegramUser: StateFlow<TelegramUser?> = _telegramUser.asStateFlow()

    private val _isWaitingApproval = MutableStateFlow(false)
    val isWaitingApproval: StateFlow<Boolean> = _isWaitingApproval.asStateFlow()

    private val _timeoutSecondsRemaining = MutableStateFlow(300)
    val timeoutSecondsRemaining: StateFlow<Int> = _timeoutSecondsRemaining.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    val formattedTimeRemaining: String
        get() {
            val totalSec = _timeoutSecondsRemaining.value
            val mins = totalSec / 60
            val secs = totalSec % 60
            return String.format("%02d:%02d", mins, secs)
        }

    fun cancelWaiting() {
        stopListening()
        _authStatusMessage.value = "লগইন অপেক্ষা বাতিল করা হয়েছে"
    }

    private var sessionListener: ListenerRegistration? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private fun loadSavedUser(): TelegramUser? {
        val id = prefs.getLong(KEY_ID, 0L)
        if (id == 0L) return null
        return TelegramUser(
            id = id,
            firstName = prefs.getString(KEY_FIRST_NAME, "") ?: "",
            lastName = prefs.getString(KEY_LAST_NAME, "") ?: "",
            username = prefs.getString(KEY_USERNAME, null),
            photoUrl = prefs.getString(KEY_PHOTO_URL, null),
            authDate = prefs.getLong(KEY_AUTH_DATE, System.currentTimeMillis())
        )
    }

    private fun saveUserLocally(user: TelegramUser) {
        prefs.edit()
            .putLong(KEY_ID, user.id)
            .putString(KEY_FIRST_NAME, user.firstName)
            .putString(KEY_LAST_NAME, user.lastName)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .putLong(KEY_AUTH_DATE, user.authDate)
            .apply()
        _telegramUser.value = user

        // Record initial app login timestamp in users collection
        try {
            val db = FirebaseFirestore.getInstance(
                context.getString(R.string.firestore_database_id)
            )
            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

            db.collection("users")
                .document(user.id.toString())
                .set(
                    mapOf("last_software_update" to nowIso),
                    com.google.firebase.firestore.SetOptions.merge()
                )
        } catch (e: Exception) {
            Log.w(TAG, "Could not update user last_software_update on login: ${e.message}")
        }
    }

    /**
     * Initiates Telegram Bot Deep-Link Login:
     * 1. Creates a unique session token
     * 2. Registers session in Firestore
     * 3. Launches Telegram bot: tg://resolve?domain=TowerCam_Login_bot&start=<token>
     * 4. Starts real-time Firestore listener with 5-minute timeout guard
     */
    fun startTelegramLogin(activityContext: Context) {
        stopListening()

        val sessionId = "auth_" + UUID.randomUUID().toString().replace("-", "").take(16)
        _isWaitingApproval.value = true
        _timeoutSecondsRemaining.value = 300

        val db = try {
            FirebaseFirestore.getInstance(
                context.getString(R.string.firestore_database_id)
            )
        } catch (e: Exception) {
            FirebaseFirestore.getInstance()
        }

        // Initialize session in Firestore
        val sessionData = hashMapOf(
            "session_id" to sessionId,
            "status" to "pending",
            "created_at" to FieldValue.serverTimestamp()
        )

        db.collection(COLLECTION_SESSIONS)
            .document(sessionId)
            .set(sessionData)
            .addOnSuccessListener {
                Log.d(TAG, "Created Telegram auth session: $sessionId")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to initialize session doc: ${e.message}")
            }

        // 5-Minute Timeout Guardian (Protects Firestore read quotas)
        timeoutJob = scope.launch {
            for (sec in 300 downTo 1) {
                _timeoutSecondsRemaining.value = sec
                delay(1000L)
            }
            Log.d(TAG, "Telegram auth session timed out after 5 minutes")
            stopListening()
        }

        // Real-time Firestore snapshot listener
        sessionListener = db.collection(COLLECTION_SESSIONS)
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Snapshot error on session $sessionId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    val telegramId = snapshot.getLong("telegram_id")
                        ?: snapshot.getLong("id")
                        ?: snapshot.getString("telegram_id")?.toLongOrNull()

                    if (status.equals("approved", ignoreCase = true) || (telegramId != null && telegramId > 0)) {
                        val firstName = snapshot.getString("first_name") ?: ""
                        val lastName = snapshot.getString("last_name") ?: ""
                        val username = snapshot.getString("username")
                        val photoUrl = snapshot.getString("photo_url")

                        val approvedUser = TelegramUser(
                            id = telegramId ?: 0L,
                            firstName = firstName,
                            lastName = lastName,
                            username = username,
                            photoUrl = photoUrl,
                            authDate = System.currentTimeMillis()
                        )

                        Log.d(TAG, "Telegram auth successful for user: ${approvedUser.displayName}")
                        saveUserLocally(approvedUser)
                        stopListening()
                    }
                }
            }

        // Open Telegram App via Deep Link (Falls back to browser if app not installed)
        val deepLinkUri = Uri.parse("tg://resolve?domain=$BOT_USERNAME&start=$sessionId")
        val webUri = Uri.parse("https://t.me/$BOT_USERNAME?start=$sessionId")

        try {
            val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Telegram app not found, opening web link: ${e.message}")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activityContext.startActivity(webIntent)
        }
    }

    /**
     * Handles deep link return from Telegram inline button:
     * towercam://auth?token=<token>&id=<id>&first_name=<name>&username=<user>
     */
    fun handleDeepLink(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "towercam" || data.host != "auth") return false

        Log.d(TAG, "Handling deep link intent: $data")
        val idParam = data.getQueryParameter("id")?.toLongOrNull()
            ?: data.getQueryParameter("telegram_id")?.toLongOrNull()
        val firstName = data.getQueryParameter("first_name") ?: ""
        val lastName = data.getQueryParameter("last_name") ?: ""
        val username = data.getQueryParameter("username")
        val photoUrl = data.getQueryParameter("photo_url")

        if (idParam != null && idParam > 0) {
            val user = TelegramUser(
                id = idParam,
                firstName = firstName,
                lastName = lastName,
                username = username,
                photoUrl = photoUrl
            )
            saveUserLocally(user)
            stopListening()
            return true
        }

        // If only token is returned, check session doc
        val token = data.getQueryParameter("token") ?: data.getQueryParameter("start")
        if (!token.isNullOrBlank()) {
            val db = try {
                FirebaseFirestore.getInstance(
                    context.getString(R.string.firestore_database_id)
                )
            } catch (e: Exception) {
                FirebaseFirestore.getInstance()
            }

            db.collection(COLLECTION_SESSIONS).document(token).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val telegramId = doc.getLong("telegram_id") ?: doc.getLong("id")
                        if (telegramId != null && telegramId > 0) {
                            val user = TelegramUser(
                                id = telegramId,
                                firstName = doc.getString("first_name") ?: "",
                                lastName = doc.getString("last_name") ?: "",
                                username = doc.getString("username"),
                                photoUrl = doc.getString("photo_url")
                            )
                            saveUserLocally(user)
                            stopListening()
                        }
                    }
                }
            return true
        }

        return false
    }

    fun stopListening() {
        sessionListener?.remove()
        sessionListener = null
        timeoutJob?.cancel()
        timeoutJob = null
        _isWaitingApproval.value = false
    }

    fun logout() {
        stopListening()
        prefs.edit().clear().apply()
        _telegramUser.value = null
    }

    companion object {
        const val BOT_USERNAME = "TowerCam_Login_bot"
        private const val COLLECTION_SESSIONS = "telegram_auth_sessions"
        private const val TAG = "TowerCamTelegramAuth"

        private const val KEY_ID = "telegram_user_id"
        private const val KEY_FIRST_NAME = "telegram_first_name"
        private const val KEY_LAST_NAME = "telegram_last_name"
        private const val KEY_USERNAME = "telegram_username"
        private const val KEY_PHOTO_URL = "telegram_photo_url"
        private const val KEY_AUTH_DATE = "telegram_auth_date"
    }
}
