package com.rondasafe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.sync.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException

/** Independent real Room stores, injected server responses. Does not mutate production. */
@RunWith(AndroidJUnit4::class)
class MultiClientSyncRegressionTest {
    private lateinit var a: OfflineDatabase
    private lateinit var b: OfflineDatabase
    private val now = "2026-09-09T12:00:00Z"
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        a = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
        b = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
    }
    @After fun close() { a.close(); b.close() }
    private fun event(id: String, type: String, parent: String? = null) = PendingEventEntity(
        id, type, buildJsonObject {
            put("guard_id", "guard")
            if (type == "PATROL_STARTED") put("shift_client_event_id", parent ?: "shift")
            if (type == "QR_SCANNED" || type == "PATROL_FINISHED") put("run_client_event_id", parent ?: "run")
        }.toString(), now, null,
    )
    private suspend fun seed(db: OfflineDatabase, shiftId: String = "shift", runId: String = "run") {
        val dao = db.offlineDao()
        dao.saveLocalShift(LocalShiftEntity(shiftId, "guard", "Porteiro", now, true))
        dao.saveLocalRun(LocalPatrolRunEntity(runId, shiftId, "guard", "template", "window", "Ronda", now, false, 1, 0, now, true))
    }
    @Test fun rejectedBackgroundShiftClosesProvisionalProjectionButKeepsEvidence() = runBlocking {
        seed(a)
        val dao = a.offlineDao()
        dao.enqueue(event("shift", "SHIFT_STARTED"))
        dao.enqueue(event("run", "PATROL_STARTED"))
        dao.enqueue(event("scan", "QR_SCANNED"))
        val sent = mutableListOf<String>()
        assertFalse(OutboxProcessor(a) { e ->
            sent += e.type
            SyncAck(error = "GUARD_ALREADY_HAS_ACTIVE_SHIFT", retryable = false)
        }.drain())
        assertEquals(listOf("SHIFT_STARTED"), sent)
        assertNull(dao.activeLocalShift())
        assertNull(dao.activeLocalRun())
        assertEquals("FAILED_PERMANENT", dao.eventState("shift"))
        assertEquals("PENDING", dao.eventState("scan"))
        assertEquals(3, dao.unsettled().size)
    }
    @Test fun rejectedBackgroundPatrolCannotRemainResumable() = runBlocking {
        seed(a)
        val dao = a.offlineDao()
        dao.enqueue(event("run", "PATROL_STARTED"))
        assertTrue(OutboxProcessor(a) { SyncAck(error = "PATROL_OCCURRENCE_ALREADY_EXECUTED", retryable = false) }.drain())
        assertNull(dao.activeLocalRun())
        assertNotNull(dao.activeLocalShift())
        assertEquals("FAILED_PERMANENT", dao.localRun("run")?.syncState)
        assertEquals("PATROL_OCCURRENCE_ALREADY_EXECUTED", dao.event("run")?.lastError)
    }
    @Test fun malformedBackgroundStartCannotLeavePhantomShift() = runBlocking {
        seed(a)
        val original = event("shift", "SHIFT_STARTED").copy(payloadJson = "not-json")
        a.offlineDao().enqueue(original)
        var sent = false
        assertTrue(OutboxProcessor(a) { sent = true; SyncAck(ack = true) }.drain())
        assertFalse(sent)
        assertNull(a.offlineDao().activeLocalShift())
        assertEquals("INVALID_LOCAL_PAYLOAD", a.offlineDao().event("shift")?.lastError)
    }
    @Test fun twoIndependentClientsReconcileOneAcceptedAndOneRejectedShift() = runBlocking {
        seed(a, "shift-a", "run-a"); seed(b, "shift-b", "run-b")
        a.offlineDao().enqueue(event("shift-a", "SHIFT_STARTED"))
        b.offlineDao().enqueue(event("shift-b", "SHIFT_STARTED"))
        var serverOwner: String? = null
        var serverInserts = 0
        val transport: suspend (PendingEventEntity) -> SyncAck = { e ->
            if (serverOwner == null || serverOwner == e.clientEventId) {
                if (serverOwner == null) serverInserts++
                serverOwner = e.clientEventId
                SyncAck(ack = true, clientEventId = e.clientEventId, shiftId = "server-shift")
            } else SyncAck(error = "GUARD_ALREADY_HAS_ACTIVE_SHIFT", retryable = false)
        }
        awaitAll(async { OutboxProcessor(a, transport).drain() }, async { OutboxProcessor(b, transport).drain() })
        assertEquals(1, serverInserts)
        val projections = listOf(a.offlineDao().localShift("shift-a")!!, b.offlineDao().localShift("shift-b")!!)
        assertEquals(1, projections.count { it.active && it.syncState == "SYNCED" })
        assertEquals(1, projections.count { !it.active && it.syncState == "FAILED_PERMANENT" })
    }
    @Test fun lostAcknowledgementRetriesSameUuidWithoutSecondServerInsert() = runBlocking {
        seed(a)
        val dao = a.offlineDao(); dao.enqueue(event("shift", "SHIFT_STARTED"))
        val committed = mutableSetOf<String>()
        var first = true
        val processor = OutboxProcessor(a) { e ->
            committed += e.clientEventId
            if (first) { first = false; throw IOException("injected lost response after commit") }
            SyncAck(ack = true, clientEventId = e.clientEventId, shiftId = "server-shift")
        }
        assertFalse(processor.drain())
        assertEquals("PENDING", dao.eventState("shift"))
        assertTrue(processor.drain())
        assertEquals(1, committed.size)
        assertEquals("SYNCED", dao.eventState("shift"))
        assertNotNull(dao.event("shift")?.receiptJson)
    }
    @Test fun oneDisconnectedClientDoesNotConsumeOtherClientsPendingEvent() = runBlocking {
        a.offlineDao().enqueue(event("a-occurrence", "GUARD_OCCURRENCE"))
        b.offlineDao().enqueue(event("b-occurrence", "GUARD_OCCURRENCE"))
        assertFalse(OutboxProcessor(a) { throw IOException("offline") }.drain())
        assertTrue(OutboxProcessor(b) { e -> SyncAck(ack = true, clientEventId = e.clientEventId) }.drain())
        assertEquals("PENDING", a.offlineDao().eventState("a-occurrence"))
        assertEquals("SYNCED", b.offlineDao().eventState("b-occurrence"))
        assertNull(a.offlineDao().event("b-occurrence"))
        assertNull(b.offlineDao().event("a-occurrence"))
    }
}
