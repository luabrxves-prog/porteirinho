package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

object OccurrenceRepository {
    suspend fun report(context: Context, runClientEventId: String, description: String) {
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
        OfflineSyncWorker.schedule(appContext)
    }
}
