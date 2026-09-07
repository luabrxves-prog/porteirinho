package com.rondasafe.app.data.local

import android.content.Context
import androidx.work.BackoffPolicy
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

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

        PortariaRepository.restoreDeviceCredential(applicationContext)
        val device = PortariaRepository.deviceCredential
            // Sem credencial não adianta manter o WorkManager em backoff infinito.
            // Quando o aparelho for configurado novamente, o provisionamento agenda
            // uma nova sincronização imediatamente.
            ?: return Result.success()

        val json = Json { ignoreUnknownKeys = true }

        while (true) {
            val pending = dao.pending()
            if (pending.isEmpty()) return Result.success()

            var retryNeeded = false

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

                val functionName: String
                val body = if (event.type == "GUARD_OCCURRENCE") {
                    functionName = "guard-occurrence"
                    buildJsonObject {
                        put("client_event_id", event.clientEventId)
                        put("guard_id", payload["guard_id"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("run_client_event_id", payload["run_client_event_id"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("description", payload["description"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("captured_at_local", payload["captured_at_local"]?.jsonPrimitive?.contentOrNull ?: event.createdAtLocal)
                    }
                } else {
                    functionName = "offline-ingest"
                    buildJsonObject {
                        put("client_event_id", event.clientEventId)
                        put("type", event.type)
                        put("created_at_local", event.createdAtLocal)
                        event.monotonicMs?.let { put("monotonic_ms", it) }
                        put("payload", payload)
                    }
                }

                val response = runCatching {
                    SupabaseProvider.client.functions.invoke(
                        function = functionName,
                        body = body,
                        headers = headersOf(
                            "x-device-id" to listOf(device.deviceId),
                            "x-device-secret" to listOf(device.deviceSecret),
                            HttpHeaders.ContentType to listOf("application/json"),
                        ),
                    ).body<OfflineIngestResponse>()
                }.getOrElse { error ->
                    val message = error.message ?: "Falha de conexão."
                    dao.markFailed(event.clientEventId, message)

                    // Se a chamada nem chegou a produzir uma resposta estruturada,
                    // o mais seguro é aguardar a rede/backoff antes de continuar.
                    return Result.retry()
                }

                if (response.ack) {
                    dao.markSynced(event.clientEventId)
                    continue
                }

                val errorCode = response.error.orEmpty()
                if (response.retryable == false) {
                    dao.markPermanentFailure(
                        event.clientEventId,
                        errorCode.ifBlank { "O servidor rejeitou definitivamente este evento." },
                    )
                    continue
                }

                val parentNotSynced = errorCode == "PARENT_SHIFT_NOT_SYNCED" ||
                    errorCode == "PARENT_RUN_NOT_SYNCED"

                if (parentNotSynced && event.attempts >= MAX_PARENT_RETRIES - 1) {
                    dao.markPermanentFailure(
                        event.clientEventId,
                        "Registro antigo incompatível com a configuração atual do aparelho ($errorCode). " +
                            "Ele foi isolado para não bloquear os demais registros.",
                    )
                    continue
                }

                dao.markFailed(
                    event.clientEventId,
                    errorCode.ifBlank { "Falha temporária de sincronização." },
                )

                // Não interromper o lote por um único evento de dependência. Isso
                // permite que registros independentes e mais novos sincronizem.
                retryNeeded = true
            }

            if (retryNeeded) return Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "rondasafe-offline-sync"
        private const val MAX_PARENT_RETRIES = 3

        fun schedule(context: Context, force: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
