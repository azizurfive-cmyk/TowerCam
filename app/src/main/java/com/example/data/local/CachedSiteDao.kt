package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedSiteDao {

    @Query("SELECT * FROM cached_sites WHERE site_code = :siteCode LIMIT 1")
    suspend fun getCachedSite(siteCode: String): CachedSite?

    @Query("SELECT COUNT(*) FROM cached_sites")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(site: CachedSite)

    @Query("DELETE FROM cached_sites WHERE site_code IN (SELECT site_code FROM cached_sites ORDER BY cached_at ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    /**
     * Enforces the FIFO policy (Max 1,000 items):
     * If the cache size has reached 1,000 and a new site is being added,
     * the oldest site is automatically deleted before inserting the new one.
     */
    @Transaction
    suspend fun insertWithFifoLimit(site: CachedSite, maxLimit: Int = 1000) {
        val existing = getCachedSite(site.siteCode)
        if (existing == null) {
            val count = getCount()
            if (count >= maxLimit) {
                val excess = (count - maxLimit) + 1
                deleteOldest(excess)
            }
        }
        insertOrUpdate(site)
    }

    @Query("SELECT * FROM cached_sites ORDER BY cached_at DESC")
    suspend fun getAllCachedSites(): List<CachedSite>

    @Query("SELECT * FROM cached_sites ORDER BY cached_at DESC LIMIT :limit")
    suspend fun getRecentSites(limit: Int = 5): List<CachedSite>

    @Query("SELECT * FROM cached_sites ORDER BY cached_at DESC LIMIT :limit")
    fun getRecentSitesFlow(limit: Int = 5): kotlinx.coroutines.flow.Flow<List<CachedSite>>

    @Query("DELETE FROM cached_sites")
    suspend fun clearAll()
}
