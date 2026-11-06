package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache entity holding only 3 core attributes:
 * 1. Site Code (Primary Key)
 * 2. Latitude
 * 3. Longitude
 * Along with cached_at timestamp to implement the FIFO 1,000 items auto-delete policy.
 */
@Entity(tableName = "cached_sites")
data class CachedSite(
    @PrimaryKey
    @ColumnInfo(name = "site_code")
    val siteCode: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)
