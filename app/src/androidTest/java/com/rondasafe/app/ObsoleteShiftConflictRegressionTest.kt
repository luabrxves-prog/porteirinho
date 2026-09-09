package com.rondasafe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObsoleteShiftConflictRegressionTest {
    private lateinit var db: OfflineDatabase
    private val oldTime = "2026-09-09T16:32:00Z"
    private val newTime = "2026-09-09T16:39:00Z"
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OfflineDatabase::class.java).build()
    }
    @After fun close() { db.close() }
    private suspend fun seed(newGuard: String = "guard", oldState: String = "FAILED_PERMANENT", reason: String = "GUARD_ALREADY_HAS_ACTIVE_SHIFT") {
        val dao = db.offlineDao()
        dao.saveLocalShift(LocalShiftEntity("old", "guard", "Porteiro", oldTime, false, syncState = oldState))
        dao.enqueue(PendingEventEntity("old", "SHIFT_STARTED", "{\"guard_id\":\"guard\"}", oldTime, null, lastError = reason, state = oldState))
        dao.saveLocalShift(LocalShiftEntity("accepted", newGuard, "Porteiro", newTime, true, "server-shift", "SYNCED"))
        dao.enqueue(PendingEventEntity("accepted", "SHIFT_STARTED", "{}", newTime, null, state = "SYNCED", syncedAt = newTime))
    }
    @Test fun successfulLaterShiftRetiresOnlyTheEmptyConflictWithoutFakingAck() = runBlocking {
        seed()
        var sends = 0
        assertTrue(OutboxProcessor(db) { sends++; SyncAck() }.drain())
        val dao = db.offlineDao()
        assertEquals(0, sends)
        assertEquals("SUPERSEDED", dao.eventState("old"))
        assertEquals("GUARD_ALREADY_HAS_ACTIVE_SHIFT", dao.event("old")?.lastError)
        assertNull(dao.event("old")?.syncedAt)
        assertTrue(dao.event("old")!!.receiptJson!!.contains("\"ack\":false"))
        assertEquals("accepted", dao.activeLocalShift()?.shiftClientEventId)
        assertEquals(1, dao.supersededConflicts().size)
        assertTrue(dao.unsettled().isEmpty())
    }
    @Test fun anotherGuardsSuccessDoesNotHideTheConflict() = runBlocking {
        seed(newGuard="someone-else"); ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("old"))
    }
    @Test fun pendingStartIsNeverRetired() = runBlocking {
        seed(oldState="PENDING"); ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("PENDING", db.offlineDao().eventState("old"))
    }
    @Test fun credentialRejectionIsNeverRetired() = runBlocking {
        seed(reason="INVALID_DEVICE_SECRET"); ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("old"))
    }
    @Test fun localRoundPreventsRetiringItsParent() = runBlocking {
        seed()
        db.offlineDao().saveLocalRun(LocalPatrolRunEntity("run","old","guard","template","window","Ronda",oldTime,false,1,0,oldTime,false))
        ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("old"))
    }
    @Test fun queuedDependentIsPreserved() = runBlocking {
        seed()
        db.offlineDao().enqueue(PendingEventEntity("end","SHIFT_ENDED","{\"shift_client_event_id\":\"old\"}",oldTime,null))
        ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("old"))
        assertEquals("PENDING", db.offlineDao().eventState("end"))
    }
    @Test fun noServerConfirmationMeansNoRetirement() = runBlocking {
        seed()
        db.offlineDao().saveLocalShift(LocalShiftEntity("accepted","guard","Porteiro",newTime,true))
        ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("old"))
    }
    @Test fun readOrPhotoEvidenceRemainsFailedAfterOldStartIsResolved() = runBlocking {
        seed()
        db.offlineDao().enqueue(PendingEventEntity("scan","QR_SCANNED","{\"run_client_event_id\":\"run\"}",newTime,null,lastError="INVALID_TOKEN_HASH",state="FAILED_PERMANENT"))
        ObsoleteShiftConflictReconciler.reconcile(db)
        assertEquals("SUPERSEDED", db.offlineDao().eventState("old"))
        assertEquals("FAILED_PERMANENT", db.offlineDao().eventState("scan"))
    }
}
