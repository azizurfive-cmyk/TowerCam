package com.example

import com.example.data.local.CachedSite
import com.example.data.model.TowerLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun siteCode_sanitization_isConsistent() {
        val raw = "  dhbdd01  "
        val sanitized = raw.trim().uppercase()
        assertEquals("DHBDD01", sanitized)
    }

    @Test
    fun towerLocation_slotDetection_assignsCorrectNextSlot() {
        // Empty document -> Slot 1
        val emptyDoc = TowerLocation(site_code = "SITE01")
        assertEquals(1, emptyDoc.getNextAvailableSlotIndex())
        assertFalse(emptyDoc.isVerified())

        // 1 slot filled -> Slot 2
        val doc1 = TowerLocation(
            site_code = "SITE01",
            latitude_1 = 23.81,
            longitude_1 = 90.41,
            technician_1 = "Tech1"
        )
        assertEquals(2, doc1.getNextAvailableSlotIndex())

        // 3 slots filled -> Slot 4
        val doc3 = doc1.copy(
            latitude_2 = 23.82,
            longitude_2 = 90.42,
            technician_2 = "Tech2",
            latitude_3 = 23.83,
            longitude_3 = 90.43,
            technician_3 = "Tech3"
        )
        assertEquals(4, doc3.getNextAvailableSlotIndex())

        // All 5 slots filled -> 0 (Slots Full)
        val doc5 = doc3.copy(
            latitude_4 = 23.84,
            longitude_4 = 90.44,
            technician_4 = "Tech4",
            latitude_5 = 23.85,
            longitude_5 = 90.45,
            technician_5 = "Tech5"
        )
        assertEquals(0, doc5.getNextAvailableSlotIndex())
    }

    @Test
    fun towerLocation_verificationStatus_handledCorrectly() {
        val unverified = TowerLocation(site_code = "TWR01", verification = "no")
        assertFalse(unverified.isVerified())

        val verified = TowerLocation(site_code = "TWR01", verification = "yes")
        assertTrue(verified.isVerified())
    }

    @Test
    fun fifoCache_evictsOldest_whenLimitReached() = runBlocking {
        val memoryCache = mutableMapOf<String, CachedSite>()
        val limit = 5

        fun insertWithFifo(site: CachedSite) {
            if (!memoryCache.containsKey(site.siteCode) && memoryCache.size >= limit) {
                val oldest = memoryCache.values.minByOrNull { it.cachedAt }
                if (oldest != null) {
                    memoryCache.remove(oldest.siteCode)
                }
            }
            memoryCache[site.siteCode] = site
        }

        // Insert 5 items
        for (i in 1..5) {
            insertWithFifo(CachedSite("SITE_$i", 23.0 + i, 90.0 + i, cachedAt = i.toLong()))
        }
        assertEquals(5, memoryCache.size)
        assertNotNull(memoryCache["SITE_1"])

        // Insert 6th item with newer timestamp
        insertWithFifo(CachedSite("SITE_6", 29.0, 96.0, cachedAt = 6L))
        assertEquals(5, memoryCache.size)
        // Oldest item (SITE_1 with timestamp 1) must be evicted
        assertNull(memoryCache["SITE_1"])
        assertNotNull(memoryCache["SITE_6"])
        assertNotNull(memoryCache["SITE_2"])
    }
}
