package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class TowerLocationRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun logTowerLocationIfNew_unauthenticatedTechnician_logsToFirestoreDirectlyAndDeduplicates() = runBlocking {
        // Any technician on any device without signing in
        auth.signOut()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_prefs_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val repository = TowerLocationRepository(firestore, prefs)

        val uniqueSiteCode = "TWR_" + UUID.randomUUID().toString().take(6).uppercase()
        val lat = 23.8103
        val lon = 90.4125
        val timestamp = "2026-11-05 17:30:00"

        val firstResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logTowerLocationIfNew(uniqueSiteCode, lat, lon, timestamp)
        }
        assertTrue("Public technician logging should succeed without sign in", firstResult.isSuccess)
        assertTrue("First logging of new site should return true", firstResult.getOrThrow())

        // Database owner signs in and verifies document exists in central database
        signInTestUser(OWNER_EMAIL)
        val docResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getTowerLocation(uniqueSiteCode)
        }
        assertTrue(docResult.isSuccess)
        val tower = docResult.getOrThrow()
        assertNotNull(tower)
        assertEquals(uniqueSiteCode, tower?.site_code)

        // Second attempt with exact same site code -> should skip write and return false
        val secondResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logTowerLocationIfNew(uniqueSiteCode, lat, lon, timestamp)
        }
        assertTrue("Second call should succeed", secondResult.isSuccess)
        assertFalse("Second call with same site code should return false (deduplicated)", secondResult.getOrThrow())
    }

    @Test
    fun getTowerLocation_unauthenticatedUser_cannotReadData() = runBlocking {
        // Unauthenticated users can submit photos, but cannot read or scrape the database
        auth.signOut()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_prefs_unauth_read", Context.MODE_PRIVATE)
        val repository = TowerLocationRepository(firestore, prefs)

        val siteCode = "DHBDD01"
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val res = repository.getTowerLocation(siteCode)
                if (res.isFailure) throw res.exceptionOrNull()!!
            }
            fail("Expected PERMISSION_DENIED when unauthenticated user attempts to read database")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    private companion object {
        const val OWNER_EMAIL = "azizur_owner@towercam.local"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
