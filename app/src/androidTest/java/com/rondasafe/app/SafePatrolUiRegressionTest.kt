package com.rondasafe.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rondasafe.app.data.local.LocalPatrolRunEntity
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.PatrolOperations
import com.rondasafe.app.ui.portaria.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SafePatrolUiRegressionTest {
    @get:Rule val ui = createComposeRule()
    private val now = "2026-09-09T12:00:00Z"
    private val patrol = AvailablePatrolDto("template", "window", "Ronda Matutina", now, now, false, 1)
    private val run = PatrolRunDto("run", 1, now)
    private inner class Source : PatrolOperations {
        val local = MutableStateFlow<LocalPatrolRunEntity?>(null)
        var available = listOf(patrol)
        var startGate: CompletableDeferred<Unit>? = null
        var scanGate: CompletableDeferred<Unit>? = null
        var occurrenceFailure = false
        var acknowledged = false
        var startCalls = 0
        var scanCalls = 0
        var finishCalls = 0
        var occurrenceCalls = 0
        override suspend fun available() = available
        override suspend fun start(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto { startCalls++; startGate?.await(); return run }
        override suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto { scanCalls++; scanGate?.await(); return ScanDto("ACCEPTED", "point", 1, 1, "Ponto", acknowledged) }
        override suspend fun finish(runId: String): FinishPatrolDto { finishCalls++; return FinishPatrolDto("COMPLETED", 1, 1, synced = acknowledged) }
        override suspend fun report(runId: String, description: String): Boolean { occurrenceCalls++; if (occurrenceFailure) throw IOException("network"); return acknowledged }
        override suspend fun endShift(shiftId: String) { }
        override fun observeRun(runId: String) = local
    }
    private val camera: @Composable (Modifier, Boolean, (String) -> Unit) -> Unit = { modifier, enabled, callback ->
        Button(onClick = { callback("test-qr") }, enabled = enabled, modifier = modifier.testTag("test_camera")) { Text("Simular leitura") }
    }
    private fun waitTag(tag: String) { ui.waitUntil(5_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun alreadyStartedOnAnotherDeviceCannotStartAgain() {
        val source = Source().apply { available = listOf(patrol.copy(executionStatus = "IN_PROGRESS")) }
        ui.setContent { MaterialTheme { SafeAvailablePatrolsScreen(ShiftDto("shift", now), { _, _ -> }, {}, source, MutableStateFlow(0L)) } }
        waitTag("start_window")
        ui.onNodeWithTag("start_window").assertIsNotEnabled()
    }
    @Test fun ownInterruptedRunCanResume() {
        val source = Source().apply { available = listOf(patrol.copy(executionStatus = "RESUMABLE")) }
        ui.setContent { MaterialTheme { SafeAvailablePatrolsScreen(ShiftDto("shift", now), { _, _ -> }, {}, source, MutableStateFlow(0L)) } }
        waitTag("start_window")
        ui.onNodeWithText("Continuar ronda").assertExists()
        ui.onNodeWithTag("start_window").assertIsEnabled()
    }
    @Test fun incomingRefreshDoesNotCancelPendingStartOrAllowDoubleClick() {
        val gate = CompletableDeferred<Unit>()
        val source = Source().apply { startGate = gate }
        val changes = MutableStateFlow(0L)
        var callbacks = 0
        ui.setContent { MaterialTheme { SafeAvailablePatrolsScreen(ShiftDto("shift", now), { _, _ -> callbacks++ }, {}, source, changes) } }
        waitTag("start_window")
        ui.onNodeWithTag("start_window").performClick()
        ui.waitUntil(5_000) { source.startCalls == 1 }
        changes.value = 1L
        ui.onNodeWithTag("start_window").assertIsNotEnabled()
        assertEquals(0, callbacks)
        gate.complete(Unit)
        ui.waitUntil(5_000) { callbacks == 1 }
        assertEquals(1, source.startCalls)
    }
    @Test fun finishCannotOvertakeScanBeingProcessed() {
        val gate = CompletableDeferred<Unit>()
        val source = Source().apply { scanGate = gate }
        ui.setContent { MaterialTheme { SafePatrolScannerScreen(run, "Ronda", {}, source, camera) } }
        ui.onNodeWithTag("test_camera").performClick()
        ui.onNodeWithTag("finish_patrol").assertIsNotEnabled()
        gate.complete(Unit)
        waitTag("scan_message")
        ui.onNodeWithTag("finish_patrol").assertIsEnabled()
        assertEquals(0, source.finishCalls)
    }
    @Test fun provisionalQrIsNotCalledServerConfirmed() {
        val source = Source()
        ui.setContent { MaterialTheme { SafePatrolScannerScreen(run, "Ronda", {}, source, camera) } }
        ui.onNodeWithTag("test_camera").performClick()
        waitTag("scan_message")
        ui.onNodeWithText("Leitura salva neste aparelho. Aguardando sincronização.").assertExists()
        ui.onNodeWithText("Ponto confirmado pelo servidor.").assertDoesNotExist()
    }
    @Test fun occurrenceFailureKeepsDescriptionForRetry() {
        val source = Source().apply { occurrenceFailure = true }
        ui.setContent { MaterialTheme { SafePatrolScannerScreen(run, "Ronda", {}, source, camera) } }
        ui.onNodeWithTag("report_occurrence").performClick()
        ui.onNodeWithTag("occurrence_description").performTextInput("Porta aberta")
        ui.onNodeWithTag("save_occurrence").performClick()
        ui.waitUntil(5_000) { source.occurrenceCalls == 1 }
        ui.waitForIdle()
        ui.onNodeWithTag("occurrence_description").assertTextContains("Porta aberta")
        ui.onNodeWithText("Ocorrência confirmada pelo servidor.").assertDoesNotExist()
    }
    @Test fun emptyOccurrenceCannotBeSent() {
        ui.setContent { MaterialTheme { SafePatrolScannerScreen(run, "Ronda", {}, Source(), camera) } }
        ui.onNodeWithTag("report_occurrence").performClick()
        ui.onNodeWithTag("save_occurrence").assertIsNotEnabled()
    }
    @Test fun finalScreenTransitionsOnlyWhenBackendReceiptArrives() {
        val source = Source()
        ui.setContent { MaterialTheme { SafePatrolFinishedScreen(FinishPatrolDto("COMPLETED", 1, 1, synced = false), {}, "run", source) } }
        ui.onNodeWithText("Ronda salva no aparelho").assertExists()
        ui.runOnIdle {
            source.local.value = LocalPatrolRunEntity("run", "shift", "guard", "template", "window", "Ronda", now, false, 1, 1, now, false, "server-run", "COMPLETED", "SYNCED")
        }
        ui.waitForIdle()
        ui.onNodeWithText("Ronda concluída").assertExists()
        ui.onNodeWithText("Ronda salva no aparelho").assertDoesNotExist()
    }
    @Test fun rejectedFinishNeverShowsConcluded() {
        ui.setContent { MaterialTheme { SafePatrolFinishedScreen(FinishPatrolDto("SYNC_FAILED", 1, 1, synced = false), {}, source = Source()) } }
        ui.onNodeWithText("Finalização não confirmada").assertExists()
        ui.onNodeWithText("Ronda concluída").assertDoesNotExist()
    }
    @Test fun sameQrIsNotResubmittedOnEveryFrameAfterSuccessfulSync() {
        val source = Source().apply { acknowledged = true }
        ui.setContent { MaterialTheme { SafePatrolScannerScreen(run, "Ronda", {}, source, camera) } }
        ui.onNodeWithTag("test_camera").performClick()
        ui.waitUntil(5_000) { source.scanCalls == 1 }
        ui.waitForIdle()
        ui.onNodeWithTag("test_camera").performClick()
        ui.waitForIdle()
        assertEquals(1, source.scanCalls)
        ui.onNodeWithTag("camera_viewport").assertIsDisplayed()
    }

}
