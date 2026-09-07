package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.data.local.LocalPatrolRunEntity
import com.rondasafe.app.data.local.LocalShiftEntity
import com.rondasafe.app.data.local.LocalVisitedCheckpointEntity
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.security.OfflineCredentialVault
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.util.UUID

object PortariaRepository {
    private val client get() = SupabaseProvider.client

    private const val DEVICE_ID_KEY = "portaria_device_id"
    private const val DEVICE_SECRET_KEY = "portaria_device_secret"

    data class DeviceCredential(val deviceId: String, val deviceSecret: String)
    data class GuardSession(
        val guardId: String,
        val guardName: String,
        val token: String? = null,
        val offline: Boolean = false,
    )

    private var appContext: Context? = null

    var deviceCredential: DeviceCredential? = null
        private set
    var guardSession: GuardSession? = null
        private set

    fun restoreDeviceCredential(context: Context) {
        appContext = context.applicationContext
        val id = OfflineCredentialVault.get(context, DEVICE_ID_KEY)
        val secret = OfflineCredentialVault.get(context, DEVICE_SECRET_KEY)
        deviceCredential = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) DeviceCredential(id, secret) else null
    }

    fun persistDeviceCredential(context: Context) {
        appContext = context.applicationContext
        val credential = deviceCredential ?: return
        OfflineCredentialVault.put(context, DEVICE_ID_KEY, credential.deviceId)
        OfflineCredentialVault.put(context, DEVICE_SECRET_KEY, credential.deviceSecret)
    }

    fun clearDeviceCredential(context: Context) {
        deviceCredential = null
        OfflineOperationalCache.clear(context)
        OfflineCredentialVault.remove(context, DEVICE_ID_KEY)
        OfflineCredentialVault.remove(context, DEVICE_SECRET_KEY)
    }

    fun clearGuardSession() {
        guardSession = null
    }

    suspend fun provisionDevice(request: DeviceProvisionRequest): DeviceProvisionResponse {
        val response = client.functions.invoke(function = "admin-devices", body = request)
        val payload = response.body<DeviceProvisionResponse>()
        payload.error?.let(::error)
        val device = payload.device ?: error("Dispositivo não retornado.")
        val secret = payload.deviceSecret ?: error("Segredo do dispositivo não retornado.")
        deviceCredential = DeviceCredential(device.deviceId, secret)
        return payload
    }

    suspend fun syncOperationalCache(): PortariaCacheResponse {
        val context = requireContext()
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val response = client.functions.invoke(
            function = "portaria-cache",
            body = mapOf("action" to "sync"),
            headers = headersOf(
                "x-device-id" to listOf(device.deviceId),
                "x-device-secret" to listOf(device.deviceSecret),
                HttpHeaders.ContentType to listOf("application/json"),
            ),
        )
        val cache = response.body<PortariaCacheResponse>()
        OfflineOperationalCache.save(context, cache)
        return cache
    }

    suspend fun listGuards(): List<PortariaGuardDto> {
        val context = appContext
        return try {
            val online = invoke<GuardListResponse>(PortariaRequest(action = "list_guards")).guards
            runCatching { syncOperationalCache() }
            online
        } catch (error: IOException) {
            if (context != null) OfflineOperationalCache.guards(context).takeIf { it.isNotEmpty() } ?: throw error
            else throw error
        }
    }

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse {
        val context = appContext
        return try {
            val payload = invoke<GuardLoginResponse>(
                PortariaRequest(action = "login_guard", guardId = guardId, pin = pin),
                includeGuardSession = false,
            )
            val guard = payload.guard ?: error("Porteiro não retornado.")
            val token = payload.guardSession ?: error("Sessão do porteiro não retornada.")
            guardSession = GuardSession(guard.id, guard.name, token = token, offline = false)
            context?.let { OfflineOperationalCache.clearPinLockout(it, guard.id) }
            runCatching { syncOperationalCache() }
            payload
        } catch (onlineError: IOException) {
            val verification = context?.let { OfflineOperationalCache.verifyPin(it, guardId, pin) }
                ?: throw onlineError
            when (verification) {
                is OfflineOperationalCache.PinVerification.Success -> {
                    val cached = verification.guard
                    guardSession = GuardSession(cached.id, cached.name, token = null, offline = true)
                    GuardLoginResponse(
                        guard = GuardLoginDto(cached.id, cached.name, cached.pinState),
                        mustChangePin = false,
                        guardSession = "offline",
                    )
                }
                is OfflineOperationalCache.PinVerification.Invalid ->
                    error("PIN inválido. Restam ${verification.remainingAttempts} tentativa(s) offline.")
                is OfflineOperationalCache.PinVerification.Locked ->
                    error("PIN temporariamente bloqueado neste aparelho por 15 minutos.")
                OfflineOperationalCache.PinVerification.RequiresConnection ->
                    error("O primeiro acesso e a troca do PIN temporário precisam de conexão.")
                OfflineOperationalCache.PinVerification.GuardUnavailable -> throw onlineError
            }
        }
    }

    suspend fun changePin(newPin: String) {
        invoke<SimplePortariaResponse>(PortariaRequest(action = "change_pin", newPin = newPin))
        runCatching { syncOperationalCache() }
    }

    suspend fun startShift(): ShiftDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        dao.activeLocalShift()?.let { error("Já existe um turno ativo neste aparelho.") }

        val eventId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        dao.saveLocalShift(LocalShiftEntity(eventId, guard.guardId, guard.guardName, startedAt, true))
        dao.enqueue(
            PendingEventEntity(
                clientEventId = eventId,
                type = "SHIFT_STARTED",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("started_at_local", startedAt)
                }.toString(),
                createdAtLocal = startedAt,
                monotonicMs = null,
            )
        )
        OfflineSyncWorker.schedule(context)
        return ShiftDto(shiftId = eventId, startedAtServer = startedAt)
    }

    suspend fun availablePatrols(): List<AvailablePatrolDto> {
        val context = appContext
        val guard = guardSession
        if (context != null && guard != null && OfflineOperationalCache.hasCache(context)) {
            return OfflineOperationalCache.availablePatrols(context, guard.guardId)
        }
        return invoke<AvailablePatrolsResponse>(PortariaRequest(action = "available_patrols")).patrols
    }

    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) error("Turno inválido para este porteiro.")
        dao.activeLocalRun()?.let { error("Já existe uma ronda em andamento.") }

        val eventId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        val run = LocalPatrolRunEntity(
            runClientEventId = eventId,
            shiftClientEventId = shiftId,
            guardId = guard.guardId,
            patrolTemplateId = patrol.patrolTemplateId,
            scheduleWindowId = patrol.scheduleWindowId,
            patrolName = patrol.patrolName,
            scheduledFor = patrol.scheduledFor,
            isLate = patrol.isLate,
            requiredPoints = patrol.requiredPoints,
            visitedPoints = 0,
            startedAtLocal = startedAt,
            active = true,
        )
        dao.saveLocalRun(run)
        dao.enqueue(
            PendingEventEntity(
                clientEventId = eventId,
                type = "PATROL_STARTED",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("shift_client_event_id", shiftId)
                    put("patrol_template_id", patrol.patrolTemplateId)
                    put("schedule_window_id", patrol.scheduleWindowId)
                    put("scheduled_for", patrol.scheduledFor)
                    put("started_at_local", startedAt)
                    put("is_late", patrol.isLate)
                }.toString(),
                createdAtLocal = startedAt,
                monotonicMs = null,
            )
        )
        OfflineSyncWorker.schedule(context)
        return PatrolRunDto(runId = eventId, requiredPoints = patrol.requiredPoints, startedAtServer = startedAt)
    }

    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")

        val capturedAt = Instant.now().toString()
        val tokenHash = OfflineOperationalCache.tokenHash(qr)
        val match = OfflineOperationalCache.qrMatch(context, qr)
        val required = OfflineOperationalCache.requiredCheckpointIds(context, run.patrolTemplateId)
        val before = dao.localVisitCount(runId)

        val result: String
        var checkpointId: String? = match?.checkpointId
        var checkpointName: String? = match?.checkpointName

        if (match == null) {
            result = "UNKNOWN_QR"
        } else if (match.checkpointId !in required) {
            result = "NOT_IN_ROUND"
        } else {
            val inserted = dao.addLocalVisit(
                LocalVisitedCheckpointEntity(runId, match.checkpointId, match.checkpointName, capturedAt)
            )
            result = if (inserted == -1L) "DUPLICATE" else "ACCEPTED"
        }

        val visited = dao.localVisitCount(runId)
        if (visited != before) dao.updateLocalVisited(runId, visited)

        val scanEventId = UUID.randomUUID().toString()
        dao.enqueue(
            PendingEventEntity(
                clientEventId = scanEventId,
                type = "QR_SCANNED",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("run_client_event_id", runId)
                    put("token_hash", tokenHash)
                    put("captured_at_local", capturedAt)
                    put("captured_monotonic_ms", monotonicMs)
                }.toString(),
                createdAtLocal = capturedAt,
                monotonicMs = monotonicMs,
            )
        )
        OfflineSyncWorker.schedule(context)

        return ScanDto(
            scanResult = result,
            checkpointId = checkpointId,
            visitedPoints = visited,
            totalPoints = run.requiredPoints,
            checkpointName = checkpointName,
        )
    }

    suspend fun finishPatrol(runId: String): FinishPatrolDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")

        val finishedAt = Instant.now().toString()
        val visitedIds = dao.localVisitedCheckpointIds(runId).toSet()
        val requiredIds = OfflineOperationalCache.requiredCheckpointIds(context, run.patrolTemplateId)
        val missing = (requiredIds - visitedIds).toList()
        val visited = visitedIds.count { it in requiredIds }
        val total = requiredIds.size
        val status = if (total > 0 && missing.isEmpty()) "COMPLETED" else "INCOMPLETE"

        val finishEventId = UUID.randomUUID().toString()
        dao.enqueue(
            PendingEventEntity(
                clientEventId = finishEventId,
                type = "PATROL_FINISHED",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("run_client_event_id", runId)
                    put("finished_at_local", finishedAt)
                }.toString(),
                createdAtLocal = finishedAt,
                monotonicMs = null,
            )
        )
        dao.finishLocalRun(runId)
        dao.finishLocalShift(run.shiftClientEventId)
        guardSession = null
        OfflineSyncWorker.schedule(context)

        return FinishPatrolDto(
            status = status,
            visitedPoints = visited,
            totalPoints = total,
            missingCheckpointIds = missing,
        )
    }

    suspend fun endShift(shiftId: String) {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        dao.activeLocalRun()?.let { error("Finalize a ronda em andamento antes de encerrar o turno.") }
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) error("Turno inválido.")

        val endedAt = Instant.now().toString()
        val eventId = UUID.randomUUID().toString()
        dao.enqueue(
            PendingEventEntity(
                clientEventId = eventId,
                type = "SHIFT_ENDED",
                payloadJson = buildJsonObject {
                    put("guard_id", guard.guardId)
                    put("shift_client_event_id", shiftId)
                    put("ended_at_local", endedAt)
                }.toString(),
                createdAtLocal = endedAt,
                monotonicMs = null,
            )
        )
        dao.finishLocalShift(shiftId)
        guardSession = null
        OfflineSyncWorker.schedule(context)
    }

    suspend fun pendingOfflineEvents(): Int =
        appContext?.let { OfflineDatabase.get(it).offlineDao().pendingCount() } ?: 0

    fun scheduleOfflineSync() {
        appContext?.let(OfflineSyncWorker::schedule)
    }

    fun isOfflineSession(): Boolean = guardSession?.offline == true

    private fun requireContext(): Context = appContext ?: error("Contexto do aplicativo indisponível.")

    private suspend inline fun <reified T> invoke(request: PortariaRequest, includeGuardSession: Boolean = true): T {
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val headers = mutableListOf(
            "x-device-id" to listOf(device.deviceId),
            "x-device-secret" to listOf(device.deviceSecret),
            HttpHeaders.ContentType to listOf("application/json"),
        )
        if (includeGuardSession) guardSession?.token?.let { headers += "x-guard-session" to listOf(it) }
        val response = client.functions.invoke(
            function = "portaria-ops",
            body = request,
            headers = headersOf(*headers.toTypedArray()),
        )
        return response.body()
    }
}
