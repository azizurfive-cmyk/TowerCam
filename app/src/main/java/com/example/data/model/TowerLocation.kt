package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TowerLocation(
    @DocumentId val id: String = "",
    val site_code: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: String = "",
    val technician: String = "Field Technician",
    @ServerTimestamp val created_at: Timestamp? = null
)
