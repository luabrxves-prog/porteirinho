package com.rondasafe.app.data.remote.datasource

import android.content.Context
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.AvailablePatrolsResponse
import com.rondasafe.app.data.model.DeviceProvisionRequest
import com.rondasafe.app.data.model.DeviceProvisionResponse
import com.rondasafe.app.data.model.GuardListResponse
import com.rondasafe.app.data.model.GuardLoginResponse
import com.rondasafe.app.data.model.PortariaCacheResponse
import com.rondasafe.app.data.model.PortariaRequest
import com.rondasafe.app.data.model.SimplePortariaResponse
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.domain.service.GuardSessionStore
import com.rondasafe.app.security.DeviceCredentialStore
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

class PortariaRemoteDataSource(
    context: Context,
    private val deviceCredentialStore: DeviceCredentialStore,
    private val guardSessionStore: GuardSessionStore,
) {
    private val appContext = context.applicationContext
    private val client get() = SupabaseProvider.client

    suspend fun provisionDevice(request: DeviceProvisionRequest): DeviceProvisionResponse {
        val response = client.functions.invoke(function = "admin-devices", body = request)
        val payload = response.body<DeviceProvisionResponse>()
        payload.error?.let(::error)
        val device = payload.device ?: error("Dispositivo não retornado.")
        val secret = payload.deviceSecret ?: error("Segredo do dispositivo não retornado.")
        deviceCredentialStore.set(DeviceCredentialStore.DeviceCredential(device.deviceId, secret))
        return payload
    }

    suspend fun syncOperationalCache(): PortariaCacheResponse {
        val device = requireDeviceCredential()
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
        OfflineOperationalCache.save(appContext, cache)
        return cache
    }

    suspend fun listGuards(): GuardListResponse =
        invoke(PortariaRequest(action = "list_guards"))

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse =
        invoke(
            PortariaRequest(action = "login_guard", guardId = guardId, pin = pin),
            includeGuardSession = false,
        )

    suspend fun changePin(newPin: String): SimplePortariaResponse =
        invoke(PortariaRequest(action = "change_pin", newPin = newPin))

    suspend fun availablePatrols(): AvailablePatrolsResponse =
        invoke(PortariaRequest(action = "available_patrols"))

    private fun requireDeviceCredential(): DeviceCredentialStore.DeviceCredential =
        deviceCredentialStore.current ?: error("Este aparelho ainda não foi configurado como portaria.")

    private suspend inline fun <reified T> invoke(
        request: PortariaRequest,
        includeGuardSession: Boolean = true,
    ): T {
        val device = requireDeviceCredential()
        val headers = mutableListOf(
            "x-device-id" to listOf(device.deviceId),
            "x-device-secret" to listOf(device.deviceSecret),
            HttpHeaders.ContentType to listOf("application/json"),
        )
        if (includeGuardSession) {
            guardSessionStore.current?.token?.let { headers += "x-guard-session" to listOf(it) }
        }
        return client.functions.invoke(
            function = "portaria-ops",
            body = request,
            headers = headersOf(*headers.toTypedArray()),
        ).body()
    }
}
