package com.rondasafe.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.PendingEventEntity
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

object OccurrenceRepository {
    suspend fun report(context: Context, runClientEventId: String, description: String): Boolean = PortariaRepository.operationMutex.withLock {
        val text = description.trim()
        require(text.length in 3..1000) { "Descreva a ocorrência com 3 a 1000 caracteres." }
        val guard = PortariaRepository.guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(context.applicationContext); val dao = db.offlineDao()
        val run = dao.localRun(runClientEventId) ?: error("Ronda local não encontrada.")
        check(run.active && run.guardId == guard.guardId) { "A ocorrência só pode ser registrada durante uma ronda ativa." }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        db.withTransaction {
            dao.enqueue(PendingEventEntity(id, "GUARD_OCCURRENCE", buildJsonObject {
                put("guard_id", guard.guardId); put("run_client_event_id", runClientEventId)
                put("description", text); put("captured_at_local", now)
            }.toString(), now, null))
        }
        PortariaRepository.deliver(id)?.ack == true
    }
}
