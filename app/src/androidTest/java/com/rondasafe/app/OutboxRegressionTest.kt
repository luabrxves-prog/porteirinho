package com.rondasafe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.sync.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

/** Actual Room/SQLite and production outbox processor; transport faults are injected. */
@RunWith(AndroidJUnit4::class)
class OutboxRegressionTest {
    private lateinit var db: OfflineDatabase
    private val dao get() = db.offlineDao()
    private val now = "2026-09-09T12:00:00Z"
    private val runId = "run"
    private val shiftId = "shift"
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OfflineDatabase::class.java).build() }
    @After fun close() { db.close() }
    private fun event(type: String, id: String = UUID.randomUUID().toString(), time: String = now) = PendingEventEntity(id, type, buildJsonObject {
        put("guard_id", "guard")
        if (type == "PATROL_STARTED" || type == "SHIFT_ENDED") put("shift_client_event_id", shiftId)
        if (type in setOf("QR_SCANNED", "GUARD_OCCURRENCE", "PATROL_FINISHED")) put("run_client_event_id", runId)
        if (type == "QR_SCANNED") put("checkpoint_id", "point")
    }.toString(), time, null)
    private suspend fun seed() {
        dao.saveLocalShift(LocalShiftEntity(shiftId, "guard", "Porteiro", now, true))
        dao.saveLocalRun(LocalPatrolRunEntity(runId, shiftId, "guard", "template", "window", "Ronda", now, false, 1, 0, now, true))
    }
    private fun receipt(e: PendingEventEntity): SyncAck = when (e.type) {
        "SHIFT_STARTED" -> SyncAck(ack = true, clientEventId = e.clientEventId, shiftId = "remote-shift")
        "PATROL_STARTED" -> SyncAck(ack = true, clientEventId = e.clientEventId, run = PatrolRunDto("remote-run", 1, now))
        "QR_SCANNED" -> SyncAck(ack = true, clientEventId = e.clientEventId, scan = ScanDto("ACCEPTED", "point", 1, 1, "Ponto"))
        "PATROL_FINISHED" -> SyncAck(ack = true, clientEventId = e.clientEventId, result = buildJsonObject {
            put("status", "COMPLETED"); put("visited_points", 1); put("total_points", 1)
        })
        else -> SyncAck(ack = true, clientEventId = e.clientEventId)
    }
    @Test fun acknowledgedUuidCannotBeResetByDuplicateEnqueue() = runBlocking {
        val event = event("GUARD_OCCURRENCE")
        dao.enqueue(event); dao.markSynced(event.clientEventId, now); dao.enqueue(event)
        assertEquals("SYNCED", dao.eventState(event.clientEventId))
        assertEquals(0, dao.pendingCount())
    }
    @Test fun queuePreservesCaptureOrderWhenWallClockMovesBackwards() = runBlocking {
        val first = event("GUARD_OCCURRENCE", time = "2026-09-09T12:00:00Z")
        val second = event("GUARD_OCCURRENCE", time = "2026-09-09T11:00:00Z")
        dao.enqueue(first); dao.enqueue(second)
        assertEquals(listOf(first.clientEventId, second.clientEventId), dao.pending().map { it.clientEventId })
    }
    @Test fun completeLifecycleUpdatesProjectionOnlyAfterReceipts() = runBlocking {
        seed()
        val events = listOf(event("SHIFT_STARTED", shiftId), event("PATROL_STARTED", runId), event("QR_SCANNED"), event("PATROL_FINISHED"))
        events.forEach { dao.enqueue(it) }
        val sent = mutableListOf<String>()
        assertTrue(OutboxProcessor(db) { e -> sent += e.type; receipt(e) }.drain())
        assertEquals(events.map { it.type }, sent)
        assertEquals("COMPLETED", dao.localRun(runId)?.finalStatus)
        assertFalse(dao.localShift(shiftId)!!.active)
        assertEquals("SYNCED", dao.localRun(runId)?.syncState)
    }
    @Test fun temporaryScanFailureCannotBeOvertakenByFinish() = runBlocking {
        seed()
        val scan = event("QR_SCANNED"); val finish = event("PATROL_FINISHED")
        dao.enqueue(scan); dao.enqueue(finish)
        val sent = mutableListOf<String>()
        assertFalse(OutboxProcessor(db) { e -> sent += e.type; throw IOException("injected") }.drain())
        assertEquals(listOf("QR_SCANNED"), sent)
        assertNull(dao.localRun(runId)?.finalStatus)
        assertEquals("PENDING", dao.eventState(finish.clientEventId))
        assertTrue(OutboxProcessor(db, ::receipt).drain())
        assertEquals("COMPLETED", dao.localRun(runId)?.finalStatus)
    }
    @Test fun permanentScanFailureKeepsFinalizationBlockedForReview() = runBlocking {
        seed()
        val scan = event("QR_SCANNED"); val finish = event("PATROL_FINISHED")
        dao.enqueue(scan); dao.enqueue(finish)
        val sent = mutableListOf<String>()
        assertFalse(OutboxProcessor(db) { e -> sent += e.type; SyncAck(error = "INVALID_SCAN_EVENT", retryable = false) }.drain())
        assertEquals(listOf("QR_SCANNED"), sent)
        assertEquals("FAILED_PERMANENT", dao.eventState(scan.clientEventId))
        assertEquals("PENDING", dao.eventState(finish.clientEventId))
        assertNull(dao.localRun(runId)?.finalStatus)
    }
    @Test fun mismatchedAcknowledgementCannotMarkDifferentEventSynced() = runBlocking {
        val event = event("GUARD_OCCURRENCE"); dao.enqueue(event)
        assertFalse(OutboxProcessor(db) { SyncAck(ack = true, clientEventId = "wrong-id") }.drain())
        assertEquals("PENDING", dao.eventState(event.clientEventId))
    }
    @Test fun finishAcknowledgementMustIncludeAuthoritativeCountsAndStatus() = runBlocking {
        seed(); val event = event("PATROL_FINISHED"); dao.enqueue(event)
        assertFalse(OutboxProcessor(db) { SyncAck(ack = true) }.drain())
        assertEquals("PENDING", dao.eventState(event.clientEventId))
        assertNull(dao.localRun(runId)?.finalStatus)
    }
    @Test fun lateStartReceiptDoesNotHidePendingFinalization() = runBlocking {
        seed(); dao.finishLocalRun(runId)
        dao.enqueue(event("PATROL_STARTED", runId))
        assertTrue(OutboxProcessor(db, ::receipt).drain())
        assertEquals("remote-run", dao.localRun(runId)?.serverRunId)
        assertEquals("PENDING", dao.localRun(runId)?.syncState)
        assertNull(dao.localRun(runId)?.finalStatus)
    }
    @Test fun rejectedOfflineQrRemovesProvisionalVisit() = runBlocking {
        seed(); dao.addLocalVisit(LocalVisitedCheckpointEntity(runId, "point", "Ponto", now))
        val event = event("QR_SCANNED"); dao.enqueue(event)
        assertTrue(OutboxProcessor(db) { e -> SyncAck(ack = true, clientEventId = e.clientEventId, scan = ScanDto("REVOKED_QR", "point", 0, 1)) }.drain())
        assertEquals(0, dao.localVisitCount(runId))
    }
    @Test fun laterRejectedQrDoesNotEraseEarlierConfirmedVisit() = runBlocking {
        seed(); dao.addLocalVisit(LocalVisitedCheckpointEntity(runId, "point", "Ponto", now, confirmed = true))
        dao.enqueue(event("QR_SCANNED"))
        assertTrue(OutboxProcessor(db) { e -> SyncAck(ack = true, clientEventId = e.clientEventId, scan = ScanDto("REVOKED_QR", "point", 1, 1)) }.drain())
        assertEquals(1, dao.localVisitCount(runId))
    }
    @Test fun twoConcurrentPumpsSendOnePersistentEventOnlyOnce() = runBlocking {
        val event = event("GUARD_OCCURRENCE"); dao.enqueue(event)
        var calls = 0
        val processor = OutboxProcessor(db) { e -> calls++; delay(100); receipt(e) }
        awaitAll(async { processor.drain() }, async { processor.drain() })
        assertEquals(1, calls)
        assertEquals("SYNCED", dao.eventState(event.clientEventId))
    }
    @Test fun cancelledNetworkCallKeepsOperationPending() = runBlocking {
        val event = event("GUARD_OCCURRENCE"); dao.enqueue(event)
        try { OutboxProcessor(db) { throw CancellationException("test cancellation") }.drain(); fail("Cancellation expected") }
        catch (_: CancellationException) { }
        assertEquals("PENDING", dao.eventState(event.clientEventId))
        assertEquals(0, dao.event(event.clientEventId)?.attempts)
    }
}
