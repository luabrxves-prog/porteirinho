package com.rondasafe.app.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.repository.PortariaRepository
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
private data class OfflineIngestResponse(
    val ack: Boolean = false,
    val retryable: Boolean? = null,
    val error: String? = null,
)

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = OfflineDatabase.get(applicationContext).offlineDao()
        val pending = dao.pending()
        if (pending.isEmpty()) return Result.success()

        PortariaRepository.restoreDeviceCredential(applicationContext)
        val device = PortariaRepository.deviceCredential ?: return Result.retry()
        val json = Json { ignoreUnknownKeys = true }

        for (event in pending) {
            val payload = runCatching {
                json.parseToJsonElement(event.payloadJson).jsonObject
            }.getOrElse {
                dao.markPermanentFailure(
                    event.clientEventId,
                    "Payload local inválido: ${it.message ?: "erro de leitura"}",
                )
                continue
            }

            val body = buildJsonObject {
                put("client_event_id", event.clientEventId)
                put("type", event.type)
                put("created_at_local", event.createdAtLocal)
                event.monotonicMs?.let { put("monotonic_ms", it) }
                put("payload", payload)
            }

            val response = runCatching {
                SupabaseProvider.client.functions.invoke(
                    function = "offline-ingest",
                    body = body,
                    headers = headersOf(
                        "x-device-id" to listOf(device.deviceId),
                        "x-device-secret" to listOf(device.deviceSecret),
                        HttpHeaders.ContentType to listOf("application/json"),
                    ),
                ).body<OfflineIngestResponse>()
            }.getOrElse {
                dao.markFailed(event.clientEventId, it.message ?: "Falha de conexão.")
                return Result.retry()
            }

            if (response.ack) {
                dao.markSynced(event.clientEventId)
                continue
            }

            if (response.retryable == false) {
                dao.markPermanentFailure(
                    event.clientEventId,
                    response.error ?: "O servidor rejeitou definitivamente este evento.",
                )
                continue
            }

            dao.markFailed(event.clientEventId, response.error ?: "Falha temporária de sincronização.")
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "rondasafe-offline-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
