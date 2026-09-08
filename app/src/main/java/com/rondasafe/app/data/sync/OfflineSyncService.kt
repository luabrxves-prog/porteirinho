package com.rondasafe.app.data.sync

import android.content.Context
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.remote.datasource.OfflineSyncRemoteDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineSyncService(
    context: Context,
    private val remote: OfflineSyncRemoteDataSource,
    private val serializer: OfflineEventSerializer = OfflineEventSerializer(),
    private val retryPolicy: OfflineRetryPolicy = OfflineRetryPolicy(),
) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()
    private val syncMutex = Mutex()

    suspend fun syncPending(): OfflineSyncOutcome = syncMutex.withLock {
        syncLoop()
    }

    private suspend fun syncLoop(): OfflineSyncOutcome {
        dao.recoverLegacyCompatibilityFailures()
        if (!remote.isConfigured()) return OfflineSyncOutcome.SUCCESS

        while (true) {
            val pending = dao.pending()
            if (pending.isEmpty()) return OfflineSyncOutcome.SUCCESS

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
                    OfflineSyncDecision.Synced -> dao.markSynced(event.clientEventId)
                    is OfflineSyncDecision.PermanentFailure -> {
                        dao.markPermanentFailure(event.clientEventId, decision.reason)
                    }
                    is OfflineSyncDecision.Retry -> {
                        dao.markFailed(event.clientEventId, decision.reason)
                        retryNeeded = true
                    }
                }
            }

            if (retryNeeded) return OfflineSyncOutcome.RETRY
        }
    }
}
