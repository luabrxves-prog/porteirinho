package com.rondasafe.app.data.local

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.data.sync.OutboxProcessor
import com.rondasafe.app.data.sync.SyncAck
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class OfflineSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = syncPending(applicationContext)
    companion object {
        private const val UNIQUE_WORK = "rondasafe-offline-sync"
        private val repairMutex = Mutex()
        suspend fun syncPending(context: Context): Result {
            val app = context.applicationContext
            val db = OfflineDatabase.get(app)
            val dao = db.offlineDao()
            repairMutex.withLock {
                if (dao.operationalCache("queue_repair_071") == null) {
                    db.withTransaction {
                        dao.recoverLegacyCompatibilityFailures()
                        dao.saveOperationalCache(OperationalCacheEntity("queue_repair_071", "{}", Instant.now().toString(), Instant.now().toString()))
                    }
                }
            }
            dao.purgeSynced(Instant.now().minus(30, ChronoUnit.DAYS).toString())
            PortariaRepository.restoreDeviceCredential(app)
            val device = PortariaRepository.deviceCredential ?: return if (dao.pendingCount() > 0) Result.retry() else Result.success()
            val processor = OutboxProcessor(db) { event ->
                val payload = OutboxProcessor.json.parseToJsonElement(event.payloadJson).jsonObject
                val occurrence = event.type == "GUARD_OCCURRENCE"
                val body = if (occurrence) buildJsonObject {
                    put("client_event_id", event.clientEventId)
                    put("guard_id", payload["guard_id"] ?: JsonNull)
                    put("run_client_event_id", payload["run_client_event_id"] ?: JsonNull)
                    put("description", payload["description"] ?: JsonNull)
                    put("captured_at_local", payload["captured_at_local"] ?: JsonPrimitive(event.createdAtLocal))
                } else buildJsonObject {
                    put("client_event_id", event.clientEventId)
                    put("type", event.type)
                    put("created_at_local", event.createdAtLocal)
                    put("payload", payload)
                }
                try {
                    SupabaseProvider.client.functions.invoke(
                        function = if (occurrence) "guard-occurrence" else "offline-ingest-v2",
                        body = body,
                        headers = headersOf(
                            "x-device-id" to listOf(device.deviceId), "x-device-secret" to listOf(device.deviceSecret),
                            HttpHeaders.ContentType to listOf("application/json"),
                        ),
                    ).body<SyncAck>()
                } catch (e: RestException) {
                    runCatching { OutboxProcessor.json.decodeFromString<SyncAck>(e.error) }.getOrElse {
                        SyncAck(error = "HTTP_${e.statusCode}", retryable = e.statusCode >= 500 || e.statusCode in setOf(408, 429))
                    }
                }
            }
            return if (processor.drain()) Result.success() else Result.retry()
        }

        @Suppress("UNUSED_PARAMETER")
        fun schedule(context: Context, force: Boolean = false) {
            // Never cancel a request that may already have committed on the server.
            // Normal work also supports API 23 without an expedited foreground-service crash.
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
