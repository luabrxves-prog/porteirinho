package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.GuardDto
import com.rondasafe.app.data.model.GuardFunctionRequest
import com.rondasafe.app.data.model.GuardFunctionResponse
import com.rondasafe.app.data.model.ArchiveDto
import com.rondasafe.app.data.model.RestoreDto
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import java.time.Instant

object GuardRepository {
    private val client get() = SupabaseProvider.client

    suspend fun list(includeArchived: Boolean = false): List<GuardDto> =
        client.from("guards").select {
            if (!includeArchived) filter { eq("active", true) }
        }.decodeList<GuardDto>().sortedBy { it.name.lowercase() }

    suspend fun create(name: String, photoUrl: String? = null): GuardFunctionResponse =
        invoke(GuardFunctionRequest(action = "create", name = name.trim(), photoUrl = photoUrl?.trim()))

    suspend fun resetPin(guardId: String): GuardFunctionResponse =
        invoke(GuardFunctionRequest(action = "reset_pin", guardId = guardId))

    suspend fun archive(guardId: String) {
        val adminId = client.auth.currentUserOrNull()?.id ?: error("Sessão administrativa não encontrada.")
        client.from("guards").update(
            ArchiveDto(archivedAt = Instant.now().toString(), archivedBy = adminId)
        ) { filter { eq("id", guardId) } }
    }

    suspend fun restore(guardId: String) {
        client.from("guards").update(RestoreDto()) { filter { eq("id", guardId) } }
    }

    private suspend fun invoke(request: GuardFunctionRequest): GuardFunctionResponse {
        val response = client.functions.invoke(function = "admin-guards", body = request)
        return response.body<GuardFunctionResponse>().also { it.error?.let(::error) }
    }
}
