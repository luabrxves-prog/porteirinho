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
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID

object GuardRepository {
    private const val PHOTO_BUCKET = "guard-photos"
    private val client get() = SupabaseProvider.client

    suspend fun list(includeArchived: Boolean = false): List<GuardDto> =
        client.from("guards").select {
            if (!includeArchived) filter { eq("active", true) }
        }.decodeList<GuardDto>().sortedBy { it.name.lowercase() }

    suspend fun create(name: String, photoUrl: String? = null): GuardFunctionResponse =
        invoke(GuardFunctionRequest(action = "create", name = name.trim(), photoUrl = photoUrl?.trim()))

    suspend fun createWithPhoto(
        name: String,
        photoBytes: ByteArray?,
        contentType: ContentType? = null,
        extension: String = "jpg",
    ): GuardFunctionResponse {
        val response = create(name)
        val guardId = response.guard?.guardId ?: error("Porteiro não retornado.")
        if (photoBytes != null && photoBytes.isNotEmpty()) {
            uploadPhoto(guardId, photoBytes, contentType ?: ContentType.Image.JPEG, extension)
        }
        return response
    }

    suspend fun uploadPhoto(
        guardId: String,
        bytes: ByteArray,
        contentType: ContentType,
        extension: String,
    ): String {
        require(bytes.isNotEmpty()) { "A foto está vazia." }
        require(bytes.size <= 5 * 1024 * 1024) { "A foto deve ter no máximo 5 MB." }
        val safeExtension = extension.lowercase().removePrefix(".").takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val path = "$guardId/${UUID.randomUUID()}.$safeExtension"
        val bucket = client.storage[PHOTO_BUCKET]
        bucket.upload(path, bytes) {
            upsert = false
            this.contentType = contentType
        }
        val url = bucket.publicUrl(path)
        client.from("guards").update({ set("photo_url", url) }) {
            filter { eq("id", guardId) }
        }
        return url
    }

    suspend fun resetPin(guardId: String): GuardFunctionResponse =
        invoke(GuardFunctionRequest(action = "reset_pin", guardId = guardId))

    suspend fun archive(guardId: String) {
        val adminId = client.auth.currentUserOrNull()?.id ?: error("Sessão administrativa não encontrada.")
        client.from("guards").update(
            ArchiveDto(active = false, archivedAt = Instant.now().toString(), archivedBy = adminId)
        ) { filter { eq("id", guardId) } }
    }

    suspend fun restore(guardId: String) {
        client.from("guards").update(RestoreDto(active = true)) { filter { eq("id", guardId) } }
    }

    private suspend fun invoke(request: GuardFunctionRequest): GuardFunctionResponse {
        val response = client.functions.invoke(function = "admin-guards", body = request)
        return response.body<GuardFunctionResponse>().also { it.error?.let(::error) }
    }
}
