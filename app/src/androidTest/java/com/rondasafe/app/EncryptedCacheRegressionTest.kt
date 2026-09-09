package com.rondasafe.app

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.model.*
import com.rondasafe.app.security.OfflineCredentialVault
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Disposable instrumented test installation only. No production data or credentials. */
@RunWith(AndroidJUnit4::class)
class EncryptedCacheRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = OfflineDatabase.get(context).offlineDao()
    private val now = "2026-09-09T12:00:00Z"
    private fun snapshot() = PortariaCacheResponse(now, CachedBuildingDto("test-building", "sensitive-cache-marker", "America/Sao_Paulo"))
    @After fun cleanup() { OfflineOperationalCache.clear(context) }

    @Test fun roomSnapshotIsEncryptedAndReadableAfterReload() = runBlocking {
        val snapshot = snapshot()
        OfflineOperationalCache.save(context, snapshot)
        val stored = dao.operationalCache("portaria")!!.payloadJson
        assertTrue(stored.startsWith("enc:v1:"))
        assertFalse(stored.contains("sensitive-cache-marker"))
        assertEquals(snapshot, OfflineOperationalCache.load(context))
    }
    @Test fun plaintextRoomSnapshotMigratesWithoutDeletingPendingEvents() = runBlocking {
        val snapshot = snapshot()
        val id = UUID.randomUUID().toString()
        dao.enqueue(PendingEventEntity(id, "SHIFT_STARTED", "{}", now, null))
        try {
            dao.saveOperationalCache(OperationalCacheEntity("portaria", Json.encodeToString(snapshot), now, now))
            assertEquals(snapshot, OfflineOperationalCache.load(context))
            assertTrue(dao.operationalCache("portaria")!!.payloadJson.startsWith("enc:v1:"))
            assertEquals("PENDING", dao.eventState(id))
        } finally { dao.markSynced(id, now) }
    }
    @Test fun encryptedPreferencesMigrateToEncryptedRoomWithoutChangingData() = runBlocking {
        val snapshot = snapshot()
        dao.deleteOperationalCache("portaria")
        OfflineCredentialVault.put(context, "portaria_operational_cache_v1", Json.encodeToString(snapshot))
        assertEquals(snapshot, OfflineOperationalCache.load(context))
        assertTrue(dao.operationalCache("portaria")!!.payloadJson.startsWith("enc:v1:"))
        assertNull(OfflineCredentialVault.get(context, "portaria_operational_cache_v1"))
    }
    @Test fun authenticatedEncryptionRejectsTamperingAndUsesFreshIv() {
        val first = OfflineCredentialVault.encrypt("sample")
        val second = OfflineCredentialVault.encrypt("sample")
        assertNotEquals(first, second)
        assertEquals("sample", OfflineCredentialVault.decrypt(first))
        val bytes = Base64.decode(first, Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { OfflineCredentialVault.decrypt(Base64.encodeToString(bytes, Base64.NO_WRAP)) }.isFailure)
    }
    @Test fun unreadableSnapshotDoesNotErasePendingQueueOrCiphertext() = runBlocking {
        val id = UUID.randomUUID().toString()
        dao.enqueue(PendingEventEntity(id, "GUARD_OCCURRENCE", "{}", now, null))
        try {
            dao.saveOperationalCache(OperationalCacheEntity("portaria", "enc:v1:invalid", now, now))
            assertNull(OfflineOperationalCache.load(context))
            assertEquals("enc:v1:invalid", dao.operationalCache("portaria")!!.payloadJson)
            assertEquals("PENDING", dao.eventState(id))
        } finally { dao.markSynced(id, now) }
    }
}
