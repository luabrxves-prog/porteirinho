package com.rondasafe.app.data.sync

import android.content.Context
import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.PendingEventEntity
import com.rondasafe.app.data.remote.datasource.OfflineSyncRemoteDataSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OfflineSyncService(
    context: Context,
    private val remote: OfflineSyncRemoteDataSource,
    private val serializer: OfflineEventSerializer = OfflineEventSerializer(),
    private val retryPolicy: OfflineRetryPolicy = OfflineRetryPolicy(),
) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncPending(): OfflineSyncOutcome = syncMutex.withLock { syncLoop() }

    private suspend fun syncLoop(): OfflineSyncOutcome {
        dao.recoverLegacyCompatibilityFailures()
        pruneExpiredLocalData()
        if (!remote.isConfigured()) return OfflineSyncOutcome.SUCCESS

        while (true) {
            val pending = dao.pending()
            if (pending.isEmpty()) {
                pruneExpiredLocalData()
                return OfflineSyncOutcome.SUCCESS
            }

            var retryNeeded = false
            for (event in pending) {
                val request = runCatching { serializer.prepare(event) }.getOrElse {
                    dao.markPermanentFailure(
                        event.clientEventId,
                        "Payload local inválido: ${it.message ?: "erro de leitura"}",
                    )
                    continue
                }

                val response = try {
                    remote.send(request.functionName, request.body)
                } catch (error: Exception) {
                    dao.markFailed(event.clientEventId, error.message ?: "Falha de conexão.")
                    return OfflineSyncOutcome.RETRY
                }

                when (val decision = retryPolicy.decide(response, event.attempts)) {
                    OfflineSyncDecision.Synced -> acknowledge(event)
                    is OfflineSyncDecision.PermanentFailure -> dao.markPermanentFailure(event.clientEventId, decision.reason)
                    is OfflineSyncDecision.Retry -> {
                        dao.markFailed(event.clientEventId, decision.reason)
                        retryNeeded = true
                    }
                }
            }
            if (retryNeeded) return OfflineSyncOutcome.RETRY
        }
    }

    private suspend fun acknowledge(event: PendingEventEntity) {
        if (event.type == OfflineEventType.PATROL_FINISHED.name) {
            runClientEventId(event)?.let { dao.deleteLocalVisits(it) }
        }
        dao.markSynced(event.clientEventId)
    }

    private fun runClientEventId(event: PendingEventEntity): String? = runCatching {
        json.parseToJsonElement(event.payloadJson)
            .jsonObject["run_client_event_id"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    private suspend fun pruneExpiredLocalData() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS).toString()
        dao.deleteExpiredPermanentFailures(cutoff)
        dao.deleteOldInactiveRuns(cutoff)
        dao.deleteOldInactiveShifts(cutoff)
    }
}
