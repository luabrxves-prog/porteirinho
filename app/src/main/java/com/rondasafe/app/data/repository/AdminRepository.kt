package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.currentUserOrNull
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import kotlinx.datetime.Clock

object AdminRepository {
    private val client get() = SupabaseProvider.client

    private fun currentAdminId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Sessão administrativa não encontrada.")

    suspend fun listBuildings(includeArchived: Boolean = false): List<BuildingDto> =
        client.from("buildings").select {
            if (!includeArchived) filter { eq("active", true) }
        }.decodeList()

    suspend fun listBlocks(buildingId: String, includeArchived: Boolean = false): List<BlockDto> =
        client.from("blocks").select {
            filter {
                eq("building_id", buildingId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList<BlockDto>().sortedBy { it.sortOrder }

    suspend fun listFloors(blockId: String, includeArchived: Boolean = false): List<FloorDto> =
        client.from("floors").select {
            filter {
                eq("block_id", blockId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList<FloorDto>().sortedBy { it.sortOrder }

    suspend fun listCheckpoints(floorId: String, includeArchived: Boolean = false): List<CheckpointDto> =
        client.from("checkpoints").select {
            filter {
                eq("floor_id", floorId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList<CheckpointDto>().sortedBy { it.sortOrder }

    suspend fun createBuilding(name: String): BuildingDto {
        val adminId = currentAdminId()
        return client.from("buildings")
            .insert(CreateBuildingDto(name = name.trim(), createdBy = adminId)) { select() }
            .decodeSingle()
    }

    suspend fun createBlock(buildingId: String, name: String): BlockDto {
        val adminId = currentAdminId()
        return client.from("blocks")
            .insert(CreateBlockDto(buildingId = buildingId, name = name.trim(), createdBy = adminId)) { select() }
            .decodeSingle()
    }

    suspend fun createFloor(blockId: String, name: String): FloorDto {
        val adminId = currentAdminId()
        return client.from("floors")
            .insert(CreateFloorDto(blockId = blockId, name = name.trim(), createdBy = adminId)) { select() }
            .decodeSingle()
    }

    suspend fun createCheckpoint(floorId: String, name: String, description: String?): CheckpointDto {
        val adminId = currentAdminId()
        return client.from("checkpoints")
            .insert(
                CreateCheckpointDto(
                    floorId = floorId,
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    createdBy = adminId,
                )
            ) { select() }
            .decodeSingle()
    }

    suspend fun archive(table: String, id: String) {
        val adminId = currentAdminId()
        val now = Clock.System.now().toString()
        client.from(table).update(
            ArchiveDto(archivedAt = now, archivedBy = adminId)
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun restore(table: String, id: String) {
        client.from(table).update(RestoreDto()) {
            filter { eq("id", id) }
        }
    }

    suspend fun getActiveQr(checkpointId: String): ActiveQrDto? {
        val response = client.functions.invoke(
            function = "admin-qr",
            body = QrFunctionRequest(action = "get_active", checkpointId = checkpointId),
        )
        val payload = response.body<QrFunctionResponse>()
        payload.error?.let { error(it) }
        return payload.qr
    }

    suspend fun createQr(checkpointId: String): QrFunctionResponse =
        invokeQr("create", checkpointId)

    suspend fun replaceQr(checkpointId: String): QrFunctionResponse =
        invokeQr("replace", checkpointId)

    private suspend fun invokeQr(action: String, checkpointId: String): QrFunctionResponse {
        val response = client.functions.invoke(
            function = "admin-qr",
            body = QrFunctionRequest(action = action, checkpointId = checkpointId),
        )
        return response.body<QrFunctionResponse>().also {
            it.error?.let(::error)
        }
    }
}
