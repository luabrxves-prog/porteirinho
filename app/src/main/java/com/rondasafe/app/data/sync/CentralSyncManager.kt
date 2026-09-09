package com.rondasafe.app.data.sync

import android.content.Context
import android.os.SystemClock
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.repository.PortariaRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class ClientSyncStateDto(val id: Int, val version: Long)

/** One owned subscription. A failed refresh never advances the acknowledged revision. */
object CentralSyncManager {
    enum class Connection { STOPPED, CONNECTING, LIVE, RETRYING }
    private val client get() = SupabaseProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val _connection = MutableStateFlow(Connection.STOPPED)
    val connection: StateFlow<Connection> = _connection.asStateFlow()
    private var lifecycleJob: Job? = null
    private var requested = false
    private var appliedVersion = -1L
    private var lastOperationalRefreshMs = 0L

    @Synchronized
    fun start(context: Context) {
        if (requested) return
        requested = true
        val previous = lifecycleJob
        val app = context.applicationContext
        lifecycleJob = scope.launch {
            // A previous foreground session must finish removing its own channel first.
            previous?.join()
            try {
                coroutineScope {
                    launch { realtimeLoop(app) }
                    launch {
                        refreshNow(app)
                        while (isActive) {
                            delay(30_000)
                            refresh(app, force = false)
                        }
                    }
                }
            } finally { _connection.value = Connection.STOPPED }
        }
    }

    @Synchronized
    fun stop() {
        requested = false
        lifecycleJob?.cancel()
    }

    private suspend fun realtimeLoop(context: Context) {
        while (currentCoroutineContext().isActive) {
            try {
                coroutineScope {
                    _connection.value = Connection.CONNECTING
                    val channel = client.channel("rondasafe-client-sync")
                    val notifications = Channel<Unit>(Channel.CONFLATED)
                    val collector = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                        table = "client_sync_state"
                    }.onEach { change ->
                        val operation = change.record["operation"]?.jsonPrimitive?.contentOrNull
                        SyncLogger.event(when (operation) {
                            "INSERT" -> "REALTIME_INSERT"
                            "DELETE" -> "REALTIME_DELETE"
                            else -> "REALTIME_UPDATE"
                        })
                        notifications.trySend(Unit)
                    }.launchIn(this)
                    val refresher = launch {
                        for (ignored in notifications) {
                            delay(300) // Collapse scan/audit bursts without cancelling an ongoing refresh.
                            refresh(context, force = false)
                        }
                    }
                    try {
                        withTimeout(20_000) { channel.subscribe(blockUntilSubscribed = true) }
                        _connection.value = Connection.LIVE
                        // Closes the initial snapshot/subscription race.
                        notifications.trySend(Unit)
                        awaitCancellation()
                    } finally {
                        collector.cancel()
                        refresher.cancel()
                        notifications.close()
                        withContext(NonCancellable) {
                            withTimeoutOrNull(5_000) { client.realtime.removeChannel(channel) }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _connection.value = Connection.RETRYING
                SyncLogger.error("SYNC_ERROR", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connection.value = Connection.RETRYING
                SyncLogger.error("SYNC_ERROR", e)
            }
            delay(5_000)
        }
    }

    suspend fun refreshNow(context: Context) = refresh(context.applicationContext, force = true)

    private suspend fun refresh(context: Context, force: Boolean) {
        try {
            refreshMutex.withLock {
                withTimeout(20_000) {
                    val state = client.from("client_sync_state").select().decodeSingle<ClientSyncStateDto>()
                    val changed = state.version != appliedVersion
                    val heartbeatDue = SystemClock.elapsedRealtime() - lastOperationalRefreshMs >= 5 * 60_000
                    if (!force && !changed && !heartbeatDue) return@withTimeout
                    PortariaRepository.restoreDeviceCredential(context)
                    if (PortariaRepository.deviceCredential != null) {
                        PortariaRepository.syncOperationalCache()
                        lastOperationalRefreshMs = SystemClock.elapsedRealtime()
                    }
                    appliedVersion = state.version
                    if (force || changed) SharedSyncBus.publish(state.version)
                }
            }
        } catch (e: TimeoutCancellationException) {
            SyncLogger.error("SYNC_ERROR", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncLogger.error("SYNC_ERROR", e)
        }
    }
}
