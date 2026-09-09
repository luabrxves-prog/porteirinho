package com.rondasafe.app

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflinePersistenceRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun event() = PendingEventEntity(UUID.randomUUID().toString(), "SHIFT_STARTED", "{}", "2026-09-09T12:00:00Z", null)
    @Test fun pendingOperationSurvivesDatabaseCloseAndReopen() = runBlocking {
        val name = "qa-${UUID.randomUUID()}.db"
        val original = event()
        val first = Room.databaseBuilder(context, OfflineDatabase::class.java, name).build()
        try { first.offlineDao().enqueue(original) } finally { first.close() }
        val second = Room.databaseBuilder(context, OfflineDatabase::class.java, name).build()
        try {
            assertEquals(original.clientEventId, second.offlineDao().pending().single().clientEventId)
            assertEquals("PENDING", second.offlineDao().eventState(original.clientEventId))
        } finally { second.close(); context.deleteDatabase(name) }
    }
    @Test fun unacknowledgedOperationIsNotSynced() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
        try {
            val event = event(); val dao = db.offlineDao()
            dao.enqueue(event)
            assertEquals(1, dao.pendingCount())
            dao.markSynced(event.clientEventId, "2026-09-09T12:00:01Z")
            assertEquals(0, dao.pendingCount())
            assertEquals("SYNCED", dao.eventState(event.clientEventId))
        } finally { db.close() }
    }
    @Test fun transactionFailureDoesNotLeaveHalfAnOperation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
        try {
            try { db.withTransaction { db.offlineDao().enqueue(event()); error("test rollback") } } catch (_: IllegalStateException) { }
            assertEquals(0, db.offlineDao().pendingCount())
        } finally { db.close() }
    }
    @Test fun repeatedQrCannotIncreaseVisitedCountTwice() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
        try {
            val visit = LocalVisitedCheckpointEntity("run", "point", "Ponto", "2026-09-09T12:00:00Z")
            db.offlineDao().addLocalVisit(visit)
            db.offlineDao().addLocalVisit(visit)
            assertEquals(1, db.offlineDao().localVisitCount("run"))
        } finally { db.close() }
    }
}
