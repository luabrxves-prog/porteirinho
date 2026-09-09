package com.rondasafe.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rondasafe.app.data.repository.ArchivedDataSource
import com.rondasafe.app.data.repository.ArchivedRecord
import com.rondasafe.app.ui.admin.ArchivedScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Real Compose/Android execution, deterministic data source; not a live-backend E2E. */
@RunWith(AndroidJUnit4::class)
class ArchivedScreenRegressionTest {
    @get:Rule val ui = createComposeRule()
    private val row = ArchivedRecord("qa-1", "guard", "Porteiro de teste", "Porteiros", "Arquivado", true)
    private val changes = MutableStateFlow(0L)
    private class Source(initial: List<ArchivedRecord>) : ArchivedDataSource {
        var rows = initial
        var failure: Exception? = null
        var loadFailure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        val deletes = AtomicInteger()
        val restores = AtomicInteger()
        override suspend fun list(): List<ArchivedRecord> { loadFailure?.let { throw it }; return rows }
        override suspend fun delete(item: ArchivedRecord) {
            deletes.incrementAndGet(); gate?.await(); failure?.let { throw it }
            rows = rows.filterNot { it.key == item.key }
        }
        override suspend fun restore(item: ArchivedRecord) {
            restores.incrementAndGet(); failure?.let { throw it }
            rows = rows.filterNot { it.key == item.key }
        }
    }
    private fun show(source: Source) {
        ui.setContent { MaterialTheme { ArchivedScreen({}, source, changes) } }
        ui.waitForIdle()
    }
    private fun waitFor(tag: String) {
        ui.waitUntil(5_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun emptyListHasNoInfiniteLoading() {
        show(Source(emptyList()))
        ui.onNodeWithText("Nada arquivado").assertExists()
        ui.onNodeWithTag("archives_loading").assertDoesNotExist()
    }
    @Test fun readErrorIsVisibleWithoutClosingScreen() {
        show(Source(emptyList()).apply { loadFailure = IOException("network unavailable") })
        waitFor("archives_error")
        ui.onNodeWithTag("archives_loading").assertDoesNotExist()
    }
    @Test fun successfulDeleteRemovesOnlyAcknowledgedRecord() {
        val source = Source(listOf(row)); show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        ui.onNodeWithTag("confirm_delete").performClick()
        waitFor("archives_success")
        ui.onNodeWithTag("archive_qa-1").assertDoesNotExist()
        assertEquals(1, source.deletes.get())
    }
    @Test fun failedDeleteKeepsRecordAndDisplaysError() {
        val source = Source(listOf(row)).apply { failure = IOException("network unavailable") }; show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        ui.onNodeWithTag("confirm_delete").performClick()
        waitFor("archives_error")
        ui.onNodeWithTag("archive_qa-1").assertExists()
        ui.onNodeWithTag("archives_success").assertDoesNotExist()
    }
    @Test fun historyRejectionDoesNotCrashOrRemoveRecord() {
        val source = Source(listOf(row)).apply { failure = IllegalStateException("Este porteiro possui histórico") }; show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        ui.onNodeWithTag("confirm_delete").performClick()
        waitFor("archives_error")
        ui.onNodeWithTag("archive_qa-1").assertExists()
    }
    @Test fun cancelDoesNotDelete() {
        val source = Source(listOf(row)); show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        ui.onNodeWithTag("cancel_delete").performClick()
        assertEquals(0, source.deletes.get())
        ui.onNodeWithTag("archive_qa-1").assertExists()
    }
    @Test fun invalidationKeepsConfirmationOpen() {
        val source = Source(listOf(row)); show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        changes.value = 1L
        ui.waitForIdle()
        ui.onNodeWithTag("confirm_delete").assertExists()
        assertEquals(0, source.deletes.get())
    }
    @Test fun waitingForServerDisablesDoubleTapAndDoesNotShowSuccess() {
        val gate = CompletableDeferred<Unit>()
        val source = Source(listOf(row)).apply { this.gate = gate }; show(source)
        ui.onNodeWithTag("delete_qa-1").performClick()
        ui.onNodeWithTag("confirm_delete").performClick()
        ui.waitUntil(5_000) { source.deletes.get() == 1 }
        ui.onNodeWithTag("confirm_delete").assertIsNotEnabled()
        ui.onNodeWithTag("archives_success").assertDoesNotExist()
        changes.value = 2L
        gate.complete(Unit)
        waitFor("archives_success")
        assertEquals(1, source.deletes.get())
    }
    @Test fun restoringRemovesItemFromArchivedList() {
        val source = Source(listOf(row)); show(source)
        ui.onNodeWithTag("restore_qa-1").performClick()
        waitFor("archives_success")
        assertEquals(1, source.restores.get())
        ui.onNodeWithTag("archive_qa-1").assertDoesNotExist()
    }
    @Test fun fixedOrHistoricalItemCannotBeDeleted() {
        show(Source(listOf(row.copy(canDelete = false, blockedReason = "Histórico preservado"))))
        ui.onNodeWithTag("delete_qa-1").assertIsNotEnabled()
        ui.onNodeWithTag("restore_qa-1").assertIsEnabled()
    }
    @Test fun duplicateRowsDoNotProduceDuplicateLazyListKeys() {
        show(Source(listOf(row, row)))
        ui.onAllNodesWithTag("archive_qa-1").assertCountEquals(1)
    }
}
