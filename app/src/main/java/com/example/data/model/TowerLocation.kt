package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TowerLocation(
    @DocumentId val id: String = "",
    val site_code: String = "",
    val verification: String = "no",
    @ServerTimestamp val created_at: Timestamp? = null,

    // Slot 1 (Main):
    val technician_1: String? = null,
    val latitude_1: Double? = null,
    val longitude_1: Double? = null,
    val timestamp_1: String? = null,

    // Slot 2 (Optional):
    val technician_2: String? = null,
    val latitude_2: Double? = null,
    val longitude_2: Double? = null,
    val timestamp_2: String? = null,

    // Slot 3 (Optional):
    val technician_3: String? = null,
    val latitude_3: Double? = null,
    val longitude_3: Double? = null,
    val timestamp_3: String? = null,

    // Slot 4 (Optional):
    val technician_4: String? = null,
    val latitude_4: Double? = null,
    val longitude_4: Double? = null,
    val timestamp_4: String? = null,

    // Slot 5 (Optional):
    val technician_5: String? = null,
    val latitude_5: Double? = null,
    val longitude_5: Double? = null,
    val timestamp_5: String? = null,

    // Legacy fields for backward compatibility
    val latitude: Double? = null,
    val longitude: Double? = null,
    val technician: String? = null,
    val timestamp: String? = null
) {
    /**
     * Determines the next available slot (1 through 5).
     * Returns 0 if all 5 slots are already filled.
     */
    fun getNextAvailableSlotIndex(): Int {
        if (latitude_1 == null && latitude == null) return 1
        if (latitude_2 == null) return 2
        if (latitude_3 == null) return 3
        if (latitude_4 == null) return 4
        if (latitude_5 == null) return 5
        return 0 // All 5 slots are occupied
    }

    /**
     * Checks if this site has been verified ("yes").
     */
    fun isVerified(): Boolean {
        return verification.equals("yes", ignoreCase = true)
    }
}
