package com.rondasafe.app.domain.service

import android.content.Context
import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.sync.OfflineEventQueue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class OccurrenceService(
    context: Context,
    private val sessionStore: GuardSessionStore,
    private val eventQueue: OfflineEventQueue,
) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()

    suspend fun report(runClientEventId: String, description: String) {
        val text = description.trim()
        require(text.length in 3..1000) { "Descreva a ocorrência com pelo menos 3 caracteres." }

        val guard = sessionStore.current ?: error("Porteiro não autenticado.")
        val run = dao.localRun(runClientEventId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) {
            error("A ocorrência só pode ser registrada durante uma ronda ativa.")
        }

        val capturedAt = Instant.now().toString()
        eventQueue.enqueue(
            type = OfflineEventType.GUARD_OCCURRENCE,
            createdAtLocal = capturedAt,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("run_client_event_id", runClientEventId)
                put("description", text)
                put("captured_at_local", capturedAt)
            }.toString(),
        )
    }
}
