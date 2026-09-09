package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import com.rondasafe.app.data.sync.SyncLogger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

object OccurrenceRepository {
    suspend fun report(context: Context, runClientEventId: String, description: String): Boolean {
        val text = description.trim()
        require(text.length in 3..1000) { "Descreva a ocorrência com pelo menos 3 caracteres." }

        val guard = PortariaRepository.guardSession ?: error("Porteiro não autenticado.")
        val appContext = context.applicationContext
        val dao = OfflineDatabase.get(appContext).offlineDao()
        val run = dao.localRun(runClientEventId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("A ocorrência só pode ser registrada durante uma ronda ativa.")

        val eventId = UUID.randomUUID().toString()
        val capturedAt = Instant.now().toString()
        dao.enqueue(
            PendingEventEntity(
                clientEventId = eventId,
                type = "GUARD_OCCURRENCE",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("run_client_event_id", runClientEventId)
                    put("description", text)
                    put("captured_at_local", capturedAt)
                }.toString(),
                createdAtLocal = capturedAt,
                monotonicMs = null,
            ),
        )
        SyncLogger.event("LOCAL_SAVE", "GUARD_OCCURRENCE id=${eventId.take(8)}")

        if (!guard.offline) {
            runCatching { OfflineSyncWorker.syncPending(appContext) }
                .onFailure { SyncLogger.error("SYNC_ERROR", it) }
            if (dao.eventState(eventId) == PendingEventEntity.STATE_SYNCED) {
                SyncLogger.event("REMOTE_SAVE", "GUARD_OCCURRENCE id=${eventId.take(8)}")
                return true
            }
        }

        OfflineSyncWorker.schedule(appContext)
        SyncLogger.event("SYNC_PENDING", "GUARD_OCCURRENCE id=${eventId.take(8)}")
        return false
    }
}
