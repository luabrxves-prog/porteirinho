package com.rondasafe.app.data.repository

import android.content.Context
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
    data class GuardSession(val guardId: String, val guardName: String, val token: String)

    var deviceCredential: DeviceCredential? = null
        private set
    var guardSession: GuardSession? = null
        private set

    fun restoreDeviceCredential(context: Context) {
        val id = OfflineCredentialVault.get(context, DEVICE_ID_KEY)
        val secret = OfflineCredentialVault.get(context, DEVICE_SECRET_KEY)
        deviceCredential = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) {
            DeviceCredential(id, secret)
        } else null
    }

    fun persistDeviceCredential(context: Context) {
        val credential = deviceCredential ?: return
        OfflineCredentialVault.put(context, DEVICE_ID_KEY, credential.deviceId)
        OfflineCredentialVault.put(context, DEVICE_SECRET_KEY, credential.deviceSecret)
    }

    fun clearDeviceCredential(context: Context) {
        deviceCredential = null
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

    suspend fun listGuards(): List<PortariaGuardDto> =
        invoke<GuardListResponse>(PortariaRequest(action = "list_guards")).guards

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse {
        val payload = invoke<GuardLoginResponse>(PortariaRequest(action = "login_guard", guardId = guardId, pin = pin), includeGuardSession = false)
        val guard = payload.guard ?: error("Porteiro não retornado.")
        val token = payload.guardSession ?: error("Sessão do porteiro não retornada.")
        guardSession = GuardSession(guard.id, guard.name, token)
        return payload
    }

    suspend fun changePin(newPin: String) {
        invoke<SimplePortariaResponse>(PortariaRequest(action = "change_pin", newPin = newPin))
    }

    suspend fun startShift(): ShiftDto =
        invoke<ShiftResponse>(
            PortariaRequest(
                action = "start_shift",
                startedAtLocal = Instant.now().toString(),
                clientEventId = UUID.randomUUID().toString(),
            )
        ).shift ?: error("Turno não retornado.")

    suspend fun availablePatrols(): List<AvailablePatrolDto> =
        invoke<AvailablePatrolsResponse>(PortariaRequest(action = "available_patrols")).patrols

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

    private suspend inline fun <reified T> invoke(request: PortariaRequest, includeGuardSession: Boolean = true): T {
        val device = deviceCredential ?: error("Este aparelho ainda não foi configurado como portaria.")
        val headers = mutableListOf(
            "x-device-id" to listOf(device.deviceId),
            "x-device-secret" to listOf(device.deviceSecret),
            HttpHeaders.ContentType to listOf("application/json"),
        )
        if (includeGuardSession) {
            guardSession?.let { headers += "x-guard-session" to listOf(it.token) }
        }
        val response = client.functions.invoke(
            function = "portaria-ops",
            body = request,
            headers = headersOf(*headers.toTypedArray()),
        )
        return response.body()
    }
}
