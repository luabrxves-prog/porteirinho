package com.rondasafe.app.data.sync

import android.content.Context
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.repository.PortariaRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class ClientSyncStateDto(
    val id: Int,
    val version: Long,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("changed_table") val changedTable: String? = null,
    @SerialName("changed_id") val changedId: String? = null,
    val operation: String = "UPDATE",
)

/**
 * Processo único de sincronização entre aparelhos.
 *
 * O servidor mantém uma linha de invalidação (`client_sync_state`). Cada alteração
 * compartilhada incrementa sua versão. Os clientes escutam apenas essa linha e,
 * ao receber uma nova versão, recarregam o snapshot operacional e notificam as
 * telas para buscar os dados oficiais. Isso evita listeners duplicados para todas
 * as tabelas e não expõe tabelas de credenciais pelo Realtime.
 */
object CentralSyncManager {
    private val client get() = SupabaseProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeJob: Job? = null
    private var pollJob: Job? = null
    private var started = false
    private var lastVersion = -1L

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext

        realtimeJob = scope.launch {
            val channel = client.channel("rondasafe-client-sync")
            try {
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "client_sync_state"
                }.collectLatest { change ->
                    val record = change.record
                    val version = record["version"]?.jsonPrimitive?.longOrNull ?: return@collectLatest
                    val operation = record["operation"]?.jsonPrimitive?.contentOrNull ?: "UPDATE"
                    val changedTable = record["changed_table"]?.jsonPrimitive?.contentOrNull
                    if (version <= lastVersion) return@collectLatest
                    lastVersion = version
                    when (operation) {
                        "INSERT" -> SyncLogger.event("REALTIME_INSERT", changedTable)
                        "DELETE" -> SyncLogger.event("REALTIME_DELETE", changedTable)
                        else -> SyncLogger.event("REALTIME_UPDATE", changedTable)
                    }
                    refreshFromServer(appContext, version)
                }
            } finally {
                runCatching { channel.unsubscribe() }
            }
        }

        scope.launch {
            // A coleta acima precisa estar registrada antes da assinatura.
            delay(50)
            runCatching {
                val channel = client.realtime.channels.firstOrNull { it.topic.contains("rondasafe-client-sync") }
                channel?.subscribe(blockUntilSubscribed = true)
            }.onFailure { SyncLogger.error("SYNC_ERROR", it) }
        }

        pollJob = scope.launch {
            while (true) {
                runCatching {
                    val state = client.from("client_sync_state")
                        .select()
                        .decodeSingle<ClientSyncStateDto>()
                    if (state.version > lastVersion) {
                        lastVersion = state.version
                        refreshFromServer(appContext, state.version)
                    }
                }.onFailure { SyncLogger.error("SYNC_ERROR", it) }
                delay(30_000)
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        val realtime = realtimeJob
        val polling = pollJob
        realtimeJob = null
        pollJob = null
        scope.launch {
            realtime?.cancelAndJoin()
            polling?.cancelAndJoin()
            runCatching { client.realtime.removeAllChannels() }
        }
    }

    suspend fun refreshNow(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val state = client.from("client_sync_state")
                .select()
                .decodeSingle<ClientSyncStateDto>()
            if (state.version > lastVersion) lastVersion = state.version
            refreshFromServer(appContext, state.version)
        }.onFailure { SyncLogger.error("SYNC_ERROR", it) }
    }

    private suspend fun refreshFromServer(context: Context, version: Long) {
        PortariaRepository.restoreDeviceCredential(context)
        if (PortariaRepository.deviceCredential != null) {
            runCatching { PortariaRepository.syncOperationalCache() }
                .onFailure { SyncLogger.error("SYNC_ERROR", it) }
        }
        SharedSyncBus.publish(version)
    }
}
