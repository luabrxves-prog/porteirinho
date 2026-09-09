package com.rondasafe.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun definitiveSuccessRequiresServerConfirmation() {
        assertFalse(SyncPolicy.canShowDefinitiveSuccess(false))
        assertTrue(SyncPolicy.canShowDefinitiveSuccess(true))
    }

    @Test
    fun versionMismatchIsConflict() {
        assertFalse(SyncPolicy.hasVersionConflict(expectedVersion = 4, serverVersion = 4))
        assertTrue(SyncPolicy.hasVersionConflict(expectedVersion = 4, serverVersion = 5))
    }

    @Test
    fun newerRemoteVersionWins() {
        assertTrue(SyncPolicy.shouldApplyRemote(localVersion = 2, remoteVersion = 3))
        assertFalse(SyncPolicy.shouldApplyRemote(localVersion = 3, remoteVersion = 2))
    }

    @Test
    fun sameVersionUsesLastWriteWinsTimestamp() {
        assertTrue(
            SyncPolicy.shouldApplyRemote(
                localVersion = 7,
                remoteVersion = 7,
                localUpdatedAt = "2026-09-09T10:00:00Z",
                remoteUpdatedAt = "2026-09-09T10:00:01Z",
            )
        )
        assertFalse(
            SyncPolicy.shouldApplyRemote(
                localVersion = 7,
                remoteVersion = 7,
                localUpdatedAt = "2026-09-09T10:00:02Z",
                remoteUpdatedAt = "2026-09-09T10:00:01Z",
            )
        )
    }
}
