package com.rondasafe.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.sync.OutboxProcessor
import com.rondasafe.app.data.sync.SyncAck
import com.rondasafe.app.data.sync.SyncLogger
import com.rondasafe.app.security.OfflineCredentialVault
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Operational writes have exactly one delivery path: Room outbox -> ordered processor. */
object PortariaRepository {
    private val client get() = SupabaseProvider.client
    private const val DEVICE_ID_KEY = "portaria_device_id"
    private const val DEVICE_SECRET_KEY = "portaria_device_secret"
    internal val operationMutex = Mutex()
    private val cacheMutex = Mutex()
    data class DeviceCredential(val deviceId: String, val deviceSecret: String)
    data class GuardSession(val guardId: String, val guardName: String, val token: String? = null, val offline: Boolean = false)
    private var appContext: Context? = null
    var deviceCredential: DeviceCredential? = null; private set
    var guardSession: GuardSession? = null; private set

    private fun safeCredentialRead(context: Context, key: String): String? =
        runCatching { OfflineCredentialVault.get(context, key) }.getOrNull()
    fun restoreDeviceCredential(context: Context) {
        appContext = context.applicationContext
        val id = safeCredentialRead(context, DEVICE_ID_KEY)
        val secret = safeCredentialRead(context, DEVICE_SECRET_KEY)
        deviceCredential = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) DeviceCredential(id, secret) else null
    }
    fun persistDeviceCredential(context: Context) {
        appContext = context.applicationContext
        val credential = deviceCredential ?: return
        OfflineCredentialVault.put(context, DEVICE_ID_KEY, credential.deviceId)
        OfflineCredentialVault.put(context, DEVICE_SECRET_KEY, credential.deviceSecret)
    }
    suspend fun clearDeviceCredential(context: Context) = withContext(Dispatchers.IO) {
        check(OfflineDatabase.get(context).offlineDao().unsettled().isEmpty()) { "Sincronize ou revise as pendências antes de desvincular o aparelho." }
        deviceCredential = null
        OfflineOperationalCache.clear(context)
        OfflineCredentialVault.remove(context, DEVICE_ID_KEY)
        OfflineCredentialVault.remove(context, DEVICE_SECRET_KEY)
    }
    fun clearGuardSession() { guardSession = null }
    suspend fun provisionDevice(request: DeviceProvisionRequest): DeviceProvisionResponse {
        val result = client.functions.invoke(function = "admin-devices", body = request).body<DeviceProvisionResponse>()
        result.error?.let(::error)
        val device = result.device ?: error("Dispositivo não retornado.")
        val secret = result.deviceSecret ?: error("Credencial do dispositivo não retornada.")
        deviceCredential = DeviceCredential(device.deviceId, secret)
        return result
    }
    suspend fun syncOperationalCache(): PortariaCacheResponse = cacheMutex.withLock {
        val context = requireContext()
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val cache = client.functions.invoke(
            function = "portaria-cache", body = mapOf("action" to "sync"),
            headers = headersOf("x-device-id" to listOf(device.deviceId), "x-device-secret" to listOf(device.deviceSecret), HttpHeaders.ContentType to listOf("application/json")),
        ).body<PortariaCacheResponse>()
        check(deviceCredential?.deviceId == device.deviceId) { "A configuração do aparelho foi alterada. Atualize novamente." }
        OfflineOperationalCache.save(context, cache)
        cache
    }
    private suspend fun refreshCacheBestEffort() {
        try { withTimeout(15_000) { syncOperationalCache() } }
        catch (e: TimeoutCancellationException) { SyncLogger.error("SYNC_ERROR", e) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { SyncLogger.error("SYNC_ERROR", e) }
    }
    suspend fun listGuards(): List<PortariaGuardDto> {
        return try {
            val response = invoke<GuardListResponse>(PortariaRequest(action = "list_guards"))
            response.error?.let(::error)
            refreshCacheBestEffort()
            response.guards
        } catch (e: IOException) {
            val context = requireContext()
            if (!OfflineOperationalCache.hasCache(context)) throw e
            OfflineOperationalCache.guards(context)
        }
    }
    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse {
        require(pin.matches(Regex("^[0-9]{6}$"))) { "O PIN deve conter 6 dígitos." }
        val context = requireContext()
        return try {
            val result = invoke<GuardLoginResponse>(PortariaRequest(action = "login_guard", guardId = guardId, pin = pin), false)
            result.error?.let(::error)
            val guard = result.guard ?: error("Porteiro não retornado.")
            val token = result.guardSession ?: error("Sessão não retornada.")
            guardSession = GuardSession(guard.id, guard.name, token, false)
            OfflineOperationalCache.clearPinLockout(context, guard.id)
            refreshCacheBestEffort()
            result
        } catch (e: IOException) {
            val cache = OfflineOperationalCache.load(context) ?: throw e
            val age = runCatching { Duration.between(Instant.parse(cache.generatedAt), Instant.now()) }.getOrNull()
            check(age != null && !age.isNegative && age <= Duration.ofHours(12)) { "Conecte o aparelho para atualizar o acesso dos porteiros." }
            when (val verified = withContext(Dispatchers.Default) { OfflineOperationalCache.verifyPin(context, guardId, pin) }) {
                is OfflineOperationalCache.PinVerification.Success -> {
                    val guard = verified.guard
                    guardSession = GuardSession(guard.id, guard.name, offline = true)
                    GuardLoginResponse(GuardLoginDto(guard.id, guard.name, guard.pinState), mustChangePin = false, guardSession = "offline")
                }
                is OfflineOperationalCache.PinVerification.Invalid -> error("PIN inválido. Restam ${verified.remainingAttempts} tentativa(s) offline.")
                is OfflineOperationalCache.PinVerification.Locked -> error("PIN temporariamente bloqueado neste aparelho por 15 minutos.")
                OfflineOperationalCache.PinVerification.RequiresConnection -> error("O primeiro acesso e a troca do PIN temporário precisam de conexão.")
                OfflineOperationalCache.PinVerification.GuardUnavailable -> throw e
            }
        }
    }
    suspend fun changePin(newPin: String) {
        require(newPin.matches(Regex("^[0-9]{6}$"))) { "O novo PIN deve conter exatamente 6 dígitos." }
        val result = invoke<SimplePortariaResponse>(PortariaRequest(action = "change_pin", newPin = newPin))
        check(result.ok) { result.error ?: "O servidor não confirmou a alteração do PIN." }
        try { withTimeout(15_000) { syncOperationalCache() } }
        catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { OfflineOperationalCache.clear(requireContext()) }
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            error("PIN salvo no servidor. Reconecte o aparelho para atualizar o acesso offline.")
        }
    }

    suspend fun startShift(): ShiftDto = operationMutex.withLock {
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(requireContext()); val dao = db.offlineDao()
        dao.activeLocalShift()?.let {
            check(it.guardId == guard.guardId) { "Há um turno de outro porteiro em aberto neste aparelho." }
            deliver(it.shiftClientEventId)
            return@withLock ShiftDto(it.shiftClientEventId, it.startedAtLocal, dao.localShift(it.shiftClientEventId)?.serverShiftId != null)
        }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        val event = event(id, "SHIFT_STARTED", now) { put("guard_id", guard.guardId); put("started_at_local", now) }
        db.withTransaction {
            dao.saveLocalShift(LocalShiftEntity(id, guard.guardId, guard.guardName, now, true))
            dao.enqueue(event)
        }
        val ack = deliver(id)
        if (ack?.shift != null) ack.shift.copy(shiftId = id, synced = true) else ShiftDto(id, now, synced = false)
    }
    suspend fun availablePatrols(): List<AvailablePatrolDto> {
        val context = requireContext(); val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        val cached = suspend { OfflineOperationalCache.availablePatrols(context, guard.guardId) }
        val base = if (!guard.offline) {
            try {
                val response = invoke<AvailablePatrolsResponse>(PortariaRequest(action = "available_patrols"))
                response.error?.let(::error)
                response.patrols
            } catch (e: IOException) { if (!OfflineOperationalCache.hasCache(context)) throw e; cached() }
        } else cached()
        val rows = base.toMutableList()
        // A process restart must not strand a locally open round outside its original time window.
        dao.activeLocalRun()?.takeIf { it.guardId == guard.guardId }?.let { active ->
            if (rows.none { it.scheduleWindowId == active.scheduleWindowId && it.scheduledFor == active.scheduledFor }) {
                rows += AvailablePatrolDto(active.patrolTemplateId, active.scheduleWindowId, active.patrolName, active.scheduledFor, active.scheduledFor, active.isLate, active.requiredPoints)
            }
        }
        return rows.map { patrol ->
            val local = dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor)
            when {
                local == null -> patrol
                local.active && local.guardId == guard.guardId -> patrol.copy(executionStatus = "RESUMABLE", executedByGuardName = guard.guardName)
                local.syncState == PendingEventEntity.STATE_FAILED_PERMANENT -> patrol
                local.finalStatus in setOf("COMPLETED", "INCOMPLETE") -> patrol.copy(executionStatus = local.finalStatus!!)
                !local.active && local.syncState != PendingEventEntity.STATE_SYNCED -> patrol.copy(executionStatus = "IN_PROGRESS")
                else -> patrol
            }
        }.distinctBy { "${it.scheduleWindowId}:${it.scheduledFor}" }
    }
    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto = operationMutex.withLock {
        val context = requireContext(); val guard = guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(context); val dao = db.offlineDao()
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        check(shift.shiftClientEventId == shiftId && shift.guardId == guard.guardId) { "Turno inválido." }
        dao.activeLocalRun()?.let {
            check(it.guardId == guard.guardId && it.scheduleWindowId == patrol.scheduleWindowId && it.scheduledFor == patrol.scheduledFor) { "Já existe uma ronda em andamento." }
            deliver(it.runClientEventId)
            return@withLock PatrolRunDto(it.runClientEventId, it.requiredPoints, it.startedAtLocal, it.serverRunId != null)
        }
        check(patrol.executionStatus == "AVAILABLE") { "Esta ronda já foi iniciada ou finalizada neste horário." }
        check(dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor) == null) { "Esta ronda já foi registrada neste aparelho." }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        val required = OfflineOperationalCache.requiredCheckpointIds(context, patrol.patrolTemplateId)
        check(required.isNotEmpty()) { "Atualize os pontos de ronda antes de iniciar." }
        val event = event(id, "PATROL_STARTED", now) {
            put("guard_id", guard.guardId); put("shift_client_event_id", shiftId)
            put("patrol_template_id", patrol.patrolTemplateId); put("schedule_window_id", patrol.scheduleWindowId)
            put("scheduled_for", patrol.scheduledFor); put("started_at_local", now); put("is_late", patrol.isLate)
        }
        db.withTransaction {
            dao.saveLocalRun(LocalPatrolRunEntity(id,shiftId,guard.guardId,patrol.patrolTemplateId,patrol.scheduleWindowId,patrol.patrolName,patrol.scheduledFor,patrol.isLate,required.size,0,now,true))
            dao.saveOperationalCache(OperationalCacheEntity("run_required:$id", JsonArray(required.map(::JsonPrimitive)).toString(), now, now))
            dao.enqueue(event)
        }
        val ack = deliver(id)
        ack?.run?.copy(runId = id, synced = true) ?: PatrolRunDto(id, required.size, now, synced = false)
    }
    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto = operationMutex.withLock {
        val context = requireContext(); val guard = guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(context); val dao = db.offlineDao()
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        check(run.active && run.guardId == guard.guardId) { "Ronda não está em andamento." }
        val match = OfflineOperationalCache.qrMatch(context, qr)
        val required = requiredIds(run)
        val now = Instant.now().toString(); val id = UUID.randomUUID().toString()
        var localResult = if (match == null) "UNKNOWN_QR" else if (match.checkpointId !in required) "NOT_IN_ROUND" else "ACCEPTED"
        db.withTransaction {
            if (match != null && localResult == "ACCEPTED") {
                val inserted = dao.addLocalVisit(LocalVisitedCheckpointEntity(runId, match.checkpointId, match.checkpointName, now))
                if (inserted == -1L) localResult = "DUPLICATE"
            }
            dao.enqueue(event(id, "QR_SCANNED", now, monotonicMs) {
                put("guard_id", guard.guardId); put("run_client_event_id", runId)
                put("token_hash", OfflineOperationalCache.tokenHash(qr)); put("captured_at_local", now)
                put("captured_monotonic_ms", monotonicMs)
                match?.let { put("checkpoint_id", it.checkpointId) }
            })
            dao.updateLocalVisited(runId, dao.localVisitCount(runId))
        }
        val ack = deliver(id)
        ack?.scan?.copy(synced = true) ?: ScanDto(localResult, match?.checkpointId, dao.localVisitCount(runId), run.requiredPoints, match?.checkpointName, synced = false)
    }
    suspend fun finishPatrol(runId: String): FinishPatrolDto = operationMutex.withLock {
        val context = requireContext(); val guard = guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(context); val dao = db.offlineDao()
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        check(run.active && run.guardId == guard.guardId) { "Ronda não está em andamento." }
        val now = Instant.now().toString(); val id = UUID.randomUUID().toString()
        val required = requiredIds(run); val visited = dao.localVisitedCheckpointIds(runId).toSet()
        val missing = (required - visited).toList()
        db.withTransaction {
            dao.enqueue(event(id, "PATROL_FINISHED", now) { put("guard_id", guard.guardId); put("run_client_event_id", runId); put("finished_at_local", now) })
            dao.finishLocalRun(runId)
            dao.finishLocalShift(run.shiftClientEventId)
        }
        val ack = deliver(id)
        guardSession = null
        ack?.result?.let { OutboxProcessor.json.decodeFromJsonElement<FinishPatrolDto>(it).copy(synced = true) }
            ?: FinishPatrolDto(if (required.isNotEmpty() && missing.isEmpty()) "COMPLETED" else "INCOMPLETE", visited.count { it in required }, required.size, missing, synced = false)
    }
    suspend fun endShift(shiftId: String) = operationMutex.withLock {
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val db = OfflineDatabase.get(requireContext()); val dao = db.offlineDao()
        check(dao.activeLocalRun() == null) { "Finalize a ronda em andamento antes de encerrar o turno." }
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        check(shift.shiftClientEventId == shiftId && shift.guardId == guard.guardId) { "Turno inválido." }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        db.withTransaction {
            dao.enqueue(event(id, "SHIFT_ENDED", now) { put("guard_id", guard.guardId); put("shift_client_event_id", shiftId); put("ended_at_local", now) })
            dao.finishLocalShift(shiftId)
        }
        deliver(id)
        guardSession = null
    }
    private suspend fun requiredIds(run: LocalPatrolRunEntity): Set<String> {
        val saved = OfflineDatabase.get(requireContext()).offlineDao().operationalCache("run_required:${run.runClientEventId}")
        return saved?.let { OutboxProcessor.json.parseToJsonElement(it.payloadJson).jsonArray.map { value -> value.jsonPrimitive.content }.toSet() }
            ?: OfflineOperationalCache.requiredCheckpointIds(requireContext(), run.patrolTemplateId)
    }
    private fun event(id: String, type: String, now: String, monotonicMs: Long? = null, fields: JsonObjectBuilder.() -> Unit): PendingEventEntity {
        val payload = buildJsonObject { fields(); put("captured_offline", !connected()) }
        return PendingEventEntity(id, type, payload.toString(), now, monotonicMs)
    }
    private fun connected(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    internal suspend fun deliver(eventId: String): SyncAck? {
        val context = requireContext(); val dao = OfflineDatabase.get(context).offlineDao()
        OfflineSyncWorker.schedule(context)
        if (connected()) {
            try { withTimeoutOrNull(7_000) { OfflineSyncWorker.syncPending(context) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { SyncLogger.error("SYNC_ERROR", e) }
        }
        val stored = dao.event(eventId) ?: error("Registro local não encontrado.")
        if (stored.state == PendingEventEntity.STATE_FAILED_PERMANENT) {
            if (stored.type == "PATROL_STARTED") dao.localRun(eventId)?.let { dao.saveLocalRun(it.copy(active = false, syncState = PendingEventEntity.STATE_FAILED_PERMANENT)) }
            if (stored.type == "SHIFT_STARTED") dao.localShift(eventId)?.let { dao.saveLocalShift(it.copy(active = false, syncState = PendingEventEntity.STATE_FAILED_PERMANENT)) }
            error("O servidor não confirmou o registro (${stored.lastError ?: "REJECTED"}). A pendência foi preservada para revisão.")
        }
        if (stored.state != PendingEventEntity.STATE_SYNCED) return null
        return stored.receiptJson?.let { OutboxProcessor.json.decodeFromString<SyncAck>(it) }
    }
    suspend fun pendingOfflineEvents(): Int = appContext?.let { OfflineDatabase.get(it).offlineDao().pendingCount() } ?: 0
    fun scheduleOfflineSync() { appContext?.let(OfflineSyncWorker::schedule) }
    fun isOfflineSession(): Boolean = guardSession?.offline == true
    private fun requireContext(): Context = appContext ?: error("Contexto do aplicativo indisponível.")
    private suspend inline fun <reified T> invoke(request: PortariaRequest, includeGuardSession: Boolean = true): T {
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val headers = mutableListOf("x-device-id" to listOf(device.deviceId), "x-device-secret" to listOf(device.deviceSecret), HttpHeaders.ContentType to listOf("application/json"))
        if (includeGuardSession) guardSession?.token?.let { headers += "x-guard-session" to listOf(it) }
        return client.functions.invoke(function = "portaria-ops", body = request, headers = headersOf(*headers.toTypedArray())).body<T>()
    }
}
