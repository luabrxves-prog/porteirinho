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
import com.rondasafe.app.data.sync.SyncLogger
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

    private fun safeCredentialRead(context: Context, key: String): String? =
        runCatching { OfflineCredentialVault.get(context, key) }
            .getOrElse {
                runCatching { OfflineCredentialVault.remove(context, key) }
                null
            }

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
        SyncLogger.event("REMOTE_SAVE", "device_provisioned id=${device.deviceId.take(8)}")
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
        SyncLogger.event("SYNC_SUCCESS", "operational_cache generated=${cache.generatedAt.take(19)}")
        return cache
    }

    suspend fun listGuards(): List<PortariaGuardDto> {
        val context = appContext
        return try {
            val online = invoke<GuardListResponse>(PortariaRequest(action = "list_guards")).guards
            runCatching { syncOperationalCache() }
            online
        } catch (error: IOException) {
            SyncLogger.error("SYNC_ERROR", error)
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
        require(newPin.matches(Regex("^\\d{6}$"))) { "O novo PIN deve conter exatamente 6 dígitos." }
        val context = requireContext()
        val response = invoke<SimplePortariaResponse>(PortariaRequest(action = "change_pin", newPin = newPin))
        check(response.ok) { response.error ?: "O servidor não confirmou a alteração do PIN." }
        SyncLogger.event("REMOTE_SAVE", "guard_pin_changed")
        try {
            syncOperationalCache()
        } catch (error: Exception) {
            // Nunca permita autenticação offline usando um hash antigo depois que o
            // servidor já confirmou o PIN novo.
            OfflineOperationalCache.clear(context)
            SyncLogger.error("SYNC_ERROR", error)
            error("PIN salvo no servidor, mas o cache deste aparelho não foi atualizado. Conecte-se à internet e tente novamente.")
        }
    }

    suspend fun startShift(): ShiftDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        dao.activeLocalShift()?.let { error("Já existe um turno ativo neste aparelho.") }

        val eventId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        val payload = buildJsonObject {
            put("guard_id", guard.guardId)
            put("started_at_local", startedAt)
        }.toString()
        dao.saveLocalShift(LocalShiftEntity(eventId, guard.guardId, guard.guardName, startedAt, true))
        dao.enqueue(PendingEventEntity(eventId, "SHIFT_STARTED", payload, startedAt, null))
        SyncLogger.event("LOCAL_SAVE", "SHIFT_STARTED id=${eventId.take(8)}")

        if (!guard.offline) {
            try {
                val remote = invoke<ShiftResponse>(
                    PortariaRequest(
                        action = "start_shift",
                        startedAtLocal = startedAt,
                        clientEventId = eventId,
                    )
                ).shift ?: error("O servidor não confirmou o início do turno.")
                dao.markShiftSynced(eventId, remote.shiftId)
                dao.markSynced(eventId, Instant.now().toString())
                SyncLogger.event("REMOTE_SAVE", "SHIFT_STARTED id=${eventId.take(8)}")
                return ShiftDto(eventId, remote.startedAtServer, synced = true)
            } catch (error: IOException) {
                SyncLogger.error("SYNC_ERROR", error)
            } catch (error: Exception) {
                dao.finishLocalShift(eventId)
                dao.markPermanentFailure(eventId, error.message)
                throw error
            }
        }

        OfflineSyncWorker.schedule(context)
        SyncLogger.event("SYNC_PENDING", "SHIFT_STARTED id=${eventId.take(8)}")
        return ShiftDto(eventId, startedAt, synced = false)
    }

    suspend fun availablePatrols(): List<AvailablePatrolDto> {
        val context = appContext
        val guard = guardSession ?: error("Porteiro não autenticado.")
        if (context == null) return emptyList()
        val dao = OfflineDatabase.get(context).offlineDao()
        val localShift = dao.activeLocalShift()

        val base: List<AvailablePatrolDto> = if (!guard.offline && localShift?.serverShiftId != null) {
            try {
                invoke<AvailablePatrolsResponse>(PortariaRequest(action = "available_patrols")).patrols
            } catch (error: IOException) {
                if (!OfflineOperationalCache.hasCache(context)) throw error
                OfflineOperationalCache.availablePatrols(context, guard.guardId)
            }
        } else {
            if (!OfflineOperationalCache.hasCache(context)) emptyList()
            else OfflineOperationalCache.availablePatrols(context, guard.guardId)
        }

        return base.map { patrol ->
            val local = dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor)
            when {
                local == null -> patrol
                local.active -> patrol.copy(
                    executionStatus = "IN_PROGRESS",
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                local.syncState != PendingEventEntity.STATE_SYNCED -> patrol.copy(
                    executionStatus = "IN_PROGRESS",
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                local.finalStatus != null -> patrol.copy(
                    executionStatus = local.finalStatus,
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                local.requiredPoints > 0 && local.visitedPoints >= local.requiredPoints -> patrol.copy(
                    executionStatus = "COMPLETED",
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                else -> patrol.copy(
                    executionStatus = "INCOMPLETE",
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
            }
        }
    }

    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto {
        require(patrol.executionStatus == "AVAILABLE") { "Esta ronda já foi iniciada ou finalizada neste horário." }
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        var shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) error("Turno inválido para este porteiro.")
        dao.activeLocalRun()?.let { error("Já existe uma ronda em andamento.") }
        dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor)?.let {
            error("Esta ronda já foi iniciada ou finalizada neste horário.")
        }

        if (!guard.offline && shift.serverShiftId == null) {
            OfflineSyncWorker.syncPending(context)
            shift = dao.localShift(shiftId) ?: shift
        }

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
        val payload = buildJsonObject {
            put("guard_id", guard.guardId)
            put("shift_client_event_id", shiftId)
            put("patrol_template_id", patrol.patrolTemplateId)
            put("schedule_window_id", patrol.scheduleWindowId)
            put("scheduled_for", patrol.scheduledFor)
            put("started_at_local", startedAt)
            put("is_late", patrol.isLate)
        }.toString()
        dao.saveLocalRun(run)
        dao.enqueue(PendingEventEntity(eventId, "PATROL_STARTED", payload, startedAt, null))
        SyncLogger.event("LOCAL_SAVE", "PATROL_STARTED id=${eventId.take(8)}")

        val serverShiftId = shift.serverShiftId
        if (!guard.offline && serverShiftId != null) {
            try {
                val remote = invoke<PatrolRunResponse>(
                    PortariaRequest(
                        action = "start_patrol",
                        shiftId = serverShiftId,
                        patrolTemplateId = patrol.patrolTemplateId,
                        scheduleWindowId = patrol.scheduleWindowId,
                        scheduledFor = patrol.scheduledFor,
                        isLate = patrol.isLate,
                        startedAtLocal = startedAt,
                        clientEventId = eventId,
                    )
                ).run ?: error("O servidor não confirmou o início da ronda.")
                dao.markRunStartedSynced(eventId, remote.runId)
                dao.markSynced(eventId, Instant.now().toString())
                SyncLogger.event("REMOTE_SAVE", "PATROL_STARTED id=${eventId.take(8)}")
                return PatrolRunDto(eventId, remote.requiredPoints, remote.startedAtServer, synced = true)
            } catch (error: IOException) {
                SyncLogger.error("SYNC_ERROR", error)
            } catch (error: Exception) {
                dao.finishLocalRun(eventId)
                dao.markPermanentFailure(eventId, error.message)
                throw error
            }
        }

        OfflineSyncWorker.schedule(context)
        SyncLogger.event("SYNC_PENDING", "PATROL_STARTED id=${eventId.take(8)}")
        return PatrolRunDto(eventId, patrol.requiredPoints, startedAt, synced = false)
    }

    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        var run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")
        if (!guard.offline && run.serverRunId == null) {
            OfflineSyncWorker.syncPending(context)
            run = dao.localRun(runId) ?: run
        }

        val capturedAt = Instant.now().toString()
        val tokenHash = OfflineOperationalCache.tokenHash(qr)
        val scanEventId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("guard_id", guard.guardId)
            put("run_client_event_id", runId)
            put("token_hash", tokenHash)
            put("captured_at_local", capturedAt)
            put("captured_monotonic_ms", monotonicMs)
        }.toString()
        dao.enqueue(PendingEventEntity(scanEventId, "QR_SCANNED", payload, capturedAt, monotonicMs))
        SyncLogger.event("LOCAL_SAVE", "QR_SCANNED id=${scanEventId.take(8)}")

        val serverRunId = run.serverRunId
        if (!guard.offline && serverRunId != null) {
            try {
                val remote = invoke<ScanResponse>(
                    PortariaRequest(
                        action = "scan",
                        runId = serverRunId,
                        qr = qr,
                        capturedAtLocal = capturedAt,
                        capturedMonotonicMs = monotonicMs,
                        clientEventId = scanEventId,
                    )
                ).scan ?: error("O servidor não confirmou a leitura do QR Code.")

                if (remote.checkpointId != null && remote.scanResult in setOf("ACCEPTED", "DUPLICATE")) {
                    dao.addLocalVisit(
                        LocalVisitedCheckpointEntity(
                            runClientEventId = runId,
                            checkpointId = remote.checkpointId,
                            checkpointName = remote.checkpointName ?: "Ponto",
                            scannedAtLocal = capturedAt,
                        )
                    )
                }
                dao.updateLocalVisited(runId, remote.visitedPoints)
                dao.markSynced(scanEventId, Instant.now().toString())
                SyncLogger.event("REMOTE_SAVE", "QR_SCANNED result=${remote.scanResult}")
                return remote.copy(synced = true)
            } catch (error: IOException) {
                SyncLogger.error("SYNC_ERROR", error)
            } catch (error: Exception) {
                dao.markPermanentFailure(scanEventId, error.message)
                throw error
            }
        }

        val match = OfflineOperationalCache.qrMatch(context, qr)
        val required = OfflineOperationalCache.requiredCheckpointIds(context, run.patrolTemplateId)
        val before = dao.localVisitCount(runId)
        val localResult: String
        val checkpointId = match?.checkpointId
        val checkpointName = match?.checkpointName
        if (match == null) {
            localResult = "UNKNOWN_QR"
        } else if (match.checkpointId !in required) {
            localResult = "NOT_IN_ROUND"
        } else {
            val inserted = dao.addLocalVisit(
                LocalVisitedCheckpointEntity(runId, match.checkpointId, match.checkpointName, capturedAt)
            )
            localResult = if (inserted == -1L) "DUPLICATE" else "ACCEPTED"
        }
        val visited = dao.localVisitCount(runId)
        if (visited != before) dao.updateLocalVisited(runId, visited)
        OfflineSyncWorker.schedule(context)
        SyncLogger.event("SYNC_PENDING", "QR_SCANNED id=${scanEventId.take(8)}")
        return ScanDto(localResult, checkpointId, visited, run.requiredPoints, checkpointName, synced = false)
    }

    suspend fun finishPatrol(runId: String): FinishPatrolDto {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        var run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")
        if (!guard.offline && run.serverRunId == null) {
            OfflineSyncWorker.syncPending(context)
            run = dao.localRun(runId) ?: run
        }

        val finishedAt = Instant.now().toString()
        val visitedIds = dao.localVisitedCheckpointIds(runId).toSet()
        val requiredIds = OfflineOperationalCache.requiredCheckpointIds(context, run.patrolTemplateId)
        val missing = (requiredIds - visitedIds).toList()
        val visited = visitedIds.count { it in requiredIds }
        val total = requiredIds.size
        val localStatus = if (total > 0 && missing.isEmpty()) "COMPLETED" else "INCOMPLETE"
        val finishEventId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("guard_id", guard.guardId)
            put("run_client_event_id", runId)
            put("finished_at_local", finishedAt)
        }.toString()
        dao.enqueue(PendingEventEntity(finishEventId, "PATROL_FINISHED", payload, finishedAt, null))
        SyncLogger.event("LOCAL_SAVE", "PATROL_FINISHED id=${finishEventId.take(8)}")

        val serverRunId = run.serverRunId
        if (!guard.offline && serverRunId != null) {
            try {
                val remote = invoke<FinishPatrolResponse>(
                    PortariaRequest(
                        action = "finish_patrol",
                        runId = serverRunId,
                        finishedAtLocal = finishedAt,
                        clientEventId = finishEventId,
                    )
                ).result ?: error("O servidor não confirmou a finalização da ronda.")
                dao.markRunFinishedSynced(runId, remote.status)
                dao.finishLocalShift(run.shiftClientEventId)
                dao.markSynced(finishEventId, Instant.now().toString())
                guardSession = null
                SyncLogger.event("REMOTE_SAVE", "PATROL_FINISHED status=${remote.status}")
                return remote.copy(synced = true)
            } catch (error: IOException) {
                SyncLogger.error("SYNC_ERROR", error)
            } catch (error: Exception) {
                dao.markPermanentFailure(finishEventId, error.message)
                throw error
            }
        }

        // Offline: encerra apenas a execução local. A tela não pode chamar isso de
        // conclusão confirmada; o servidor continuará sendo a fonte oficial.
        dao.finishLocalRun(runId)
        dao.finishLocalShift(run.shiftClientEventId)
        guardSession = null
        OfflineSyncWorker.schedule(context)
        SyncLogger.event("SYNC_PENDING", "PATROL_FINISHED id=${finishEventId.take(8)}")
        return FinishPatrolDto(localStatus, visited, total, missing, synced = false)
    }

    suspend fun endShift(shiftId: String) {
        val context = requireContext()
        val guard = guardSession ?: error("Porteiro não autenticado.")
        val dao = OfflineDatabase.get(context).offlineDao()
        dao.activeLocalRun()?.let { error("Finalize a ronda em andamento antes de encerrar o turno.") }
        var shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) error("Turno inválido.")
        if (!guard.offline && shift.serverShiftId == null) {
            OfflineSyncWorker.syncPending(context)
            shift = dao.localShift(shiftId) ?: shift
        }

        val endedAt = Instant.now().toString()
        val eventId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("guard_id", guard.guardId)
            put("shift_client_event_id", shiftId)
            put("ended_at_local", endedAt)
        }.toString()
        dao.enqueue(PendingEventEntity(eventId, "SHIFT_ENDED", payload, endedAt, null))

        val serverShiftId = shift.serverShiftId
        if (!guard.offline && serverShiftId != null) {
            try {
                val response = invoke<SimplePortariaResponse>(
                    PortariaRequest(action = "end_shift", shiftId = serverShiftId, endedAtLocal = endedAt)
                )
                check(response.ok) { response.error ?: "O servidor não confirmou o encerramento do turno." }
                dao.finishLocalShift(shiftId)
                dao.markSynced(eventId, Instant.now().toString())
                guardSession = null
                SyncLogger.event("REMOTE_SAVE", "SHIFT_ENDED id=${eventId.take(8)}")
                return
            } catch (error: IOException) {
                SyncLogger.error("SYNC_ERROR", error)
            } catch (error: Exception) {
                dao.markPermanentFailure(eventId, error.message)
                throw error
            }
        }

        dao.finishLocalShift(shiftId)
        guardSession = null
        OfflineSyncWorker.schedule(context)
        SyncLogger.event("SYNC_PENDING", "SHIFT_ENDED id=${eventId.take(8)}")
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
