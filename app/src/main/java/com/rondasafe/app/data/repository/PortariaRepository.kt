package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.security.OfflineCredentialVault
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
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
        deviceCredential = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) {
            DeviceCredential(id, secret)
        } else null
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
        val context = appContext ?: error("Contexto do aplicativo indisponível.")
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
        return runCatching {
            val online = invoke<GuardListResponse>(PortariaRequest(action = "list_guards")).guards
            runCatching { syncOperationalCache() }
            online
        }.getOrElse { error ->
            if (context != null) {
                OfflineOperationalCache.guards(context).takeIf { it.isNotEmpty() } ?: throw error
            } else throw error
        }
    }

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse {
        val context = appContext
        return runCatching {
            val payload = invoke<GuardLoginResponse>(
                PortariaRequest(action = "login_guard", guardId = guardId, pin = pin),
                includeGuardSession = false,
            )
            val guard = payload.guard ?: error("Porteiro não retornado.")
            val token = payload.guardSession ?: error("Sessão do porteiro não retornada.")
            guardSession = GuardSession(guard.id, guard.name, token = token, offline = false)
            runCatching { syncOperationalCache() }
            payload
        }.getOrElse { onlineError ->
            val cached = context?.let { OfflineOperationalCache.verifyPin(it, guardId, pin) }
                ?: throw onlineError
            if (cached.credential.mustChangePin) {
                error("O primeiro acesso e a troca do PIN temporário precisam de conexão.")
            }
            guardSession = GuardSession(cached.id, cached.name, token = null, offline = true)
            GuardLoginResponse(
                guard = GuardLoginDto(cached.id, cached.name, cached.pinState),
                mustChangePin = false,
                guardSession = "offline",
            )
        }
    }

    suspend fun changePin(newPin: String) {
        invoke<SimplePortariaResponse>(PortariaRequest(action = "change_pin", newPin = newPin))
        runCatching { syncOperationalCache() }
    }

    suspend fun startShift(): ShiftDto =
        invoke<ShiftResponse>(
            PortariaRequest(
                action = "start_shift",
                startedAtLocal = Instant.now().toString(),
                clientEventId = UUID.randomUUID().toString(),
            )
        ).shift ?: error("Turno não retornado.")

    suspend fun availablePatrols(): List<AvailablePatrolDto> {
        val context = appContext
        val guard = guardSession
        if (guard?.offline == true && context != null) {
            return OfflineOperationalCache.availablePatrols(context, guard.guardId)
        }
        return runCatching {
            invoke<AvailablePatrolsResponse>(PortariaRequest(action = "available_patrols")).patrols
        }.getOrElse { error ->
            if (context != null && guard != null) {
                OfflineOperationalCache.availablePatrols(context, guard.guardId).takeIf { it.isNotEmpty() }
                    ?: throw error
            } else throw error
        }
    }

    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto =
        invoke<PatrolRunResponse>(
            PortariaRequest(
                action = "start_patrol",
                shiftId = shiftId,
                patrolTemplateId = patrol.patrolTemplateId,
                scheduleWindowId = patrol.scheduleWindowId,
                scheduledFor = patrol.scheduledFor,
                isLate = patrol.isLate,
                startedAtLocal = Instant.now().toString(),
                clientEventId = UUID.randomUUID().toString(),
            )
        ).run ?: error("Ronda não retornada.")

    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto =
        invoke<ScanResponse>(
            PortariaRequest(
                action = "scan",
                runId = runId,
                qr = qr,
                capturedAtLocal = Instant.now().toString(),
                capturedMonotonicMs = monotonicMs,
                clientEventId = UUID.randomUUID().toString(),
            )
        ).scan ?: error("Leitura não retornada.")

    suspend fun finishPatrol(runId: String): FinishPatrolDto {
        val result = invoke<FinishPatrolResponse>(
            PortariaRequest(action = "finish_patrol", runId = runId, finishedAtLocal = Instant.now().toString())
        ).result ?: error("Resultado não retornado.")
        guardSession = null
        return result
    }

    suspend fun endShift(shiftId: String) {
        invoke<SimplePortariaResponse>(PortariaRequest(action = "end_shift", shiftId = shiftId, endedAtLocal = Instant.now().toString()))
        guardSession = null
    }

    fun scheduleOfflineSync() {
        appContext?.let(OfflineSyncWorker::schedule)
    }

    fun isOfflineSession(): Boolean = guardSession?.offline == true

    private suspend inline fun <reified T> invoke(request: PortariaRequest, includeGuardSession: Boolean = true): T {
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val headers = mutableListOf(
            "x-device-id" to listOf(device.deviceId),
            "x-device-secret" to listOf(device.deviceSecret),
            HttpHeaders.ContentType to listOf("application/json"),
        )
        if (includeGuardSession) {
            guardSession?.token?.let { headers += "x-guard-session" to listOf(it) }
        }
        val response = client.functions.invoke(
            function = "portaria-ops",
            body = request,
            headers = headersOf(*headers.toTypedArray()),
        )
        return response.body()
    }
}
