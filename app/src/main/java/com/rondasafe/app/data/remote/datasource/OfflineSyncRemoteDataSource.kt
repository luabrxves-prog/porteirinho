package com.rondasafe.app.data.remote.datasource

import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.sync.OfflineIngestResponse
import com.rondasafe.app.security.DeviceCredentialStore
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class OfflineSyncRemoteDataSource(
    private val deviceCredentialStore: DeviceCredentialStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client get() = SupabaseProvider.client

    fun isConfigured(): Boolean = deviceCredentialStore.current != null

    suspend fun send(functionName: String, body: JsonObject): OfflineIngestResponse {
        val device = deviceCredentialStore.current
            ?: error("Este aparelho ainda não foi configurado como portaria.")

        return try {
            client.functions.invoke(
                function = functionName,
                body = body,
                headers = headersOf(
                    "x-device-id" to listOf(device.deviceId),
                    "x-device-secret" to listOf(device.deviceSecret),
                    HttpHeaders.ContentType to listOf("application/json"),
                ),
            ).body()
        } catch (error: RestException) {
            runCatching {
                json.decodeFromString<OfflineIngestResponse>(error.error)
            }.getOrElse {
                OfflineIngestResponse(
                    ack = false,
                    retryable = error.statusCode >= 500,
                    error = "HTTP ${error.statusCode}: ${error.error}",
                )
            }
        }
    }
}
