package com.rondasafe.app.domain.service

import android.content.Context
import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.core.constants.PatrolScanResult
import com.rondasafe.app.data.local.LocalVisitedCheckpointEntity
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.ScanDto
import com.rondasafe.app.data.sync.OfflineEventQueue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class QrScanService(
    context: Context,
    private val sessionStore: GuardSessionStore,
    private val eventQueue: OfflineEventQueue,
) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()

    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto {
        val guard = sessionStore.current ?: error("Porteiro não autenticado.")
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")

        val capturedAt = Instant.now().toString()
        val tokenHash = OfflineOperationalCache.tokenHash(qr)
        val match = OfflineOperationalCache.qrMatch(appContext, qr)
        val required = OfflineOperationalCache.requiredCheckpointIds(appContext, run.patrolTemplateId)
        val before = dao.localVisitCount(runId)

        val result: PatrolScanResult
        val checkpointId = match?.checkpointId
        val checkpointName = match?.checkpointName

        result = when {
            match == null -> PatrolScanResult.UNKNOWN_QR
            match.checkpointId !in required -> PatrolScanResult.NOT_IN_ROUND
            else -> {
                val inserted = dao.addLocalVisit(
                    LocalVisitedCheckpointEntity(
                        runClientEventId = runId,
                        checkpointId = match.checkpointId,
                        checkpointName = match.checkpointName,
                        scannedAtLocal = capturedAt,
                    )
                )
                if (inserted == -1L) PatrolScanResult.DUPLICATE else PatrolScanResult.ACCEPTED
            }
        }

        val visited = dao.localVisitCount(runId)
        if (visited != before) dao.updateLocalVisited(runId, visited)

        eventQueue.enqueue(
            type = OfflineEventType.QR_SCANNED,
            createdAtLocal = capturedAt,
            monotonicMs = monotonicMs,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("run_client_event_id", runId)
                put("token_hash", tokenHash)
                put("captured_at_local", capturedAt)
                put("captured_monotonic_ms", monotonicMs)
            }.toString(),
        )

        return ScanDto(
            scanResult = result.name,
            checkpointId = checkpointId,
            visitedPoints = visited,
            totalPoints = run.requiredPoints,
            checkpointName = checkpointName,
        )
    }
}
