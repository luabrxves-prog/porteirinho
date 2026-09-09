package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

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

    suspend fun condominium(): BuildingDto =
        listBuildings().firstOrNull { it.name.trim().equals("Condomínio Solar Carlos Gomes", ignoreCase = true) }
            ?: error("Condomínio Solar Carlos Gomes não encontrado entre os condomínios ativos.")

    suspend fun defaultBlocks(): List<BlockDto> {
        val building = condominium()
        var existing = listBlocks(building.id, includeArchived = true)

        suspend fun ensure(name: String) {
            val block = existing.firstOrNull { it.name.equals(name, true) }
            when {
                block == null -> createBlock(building.id, name)
                !block.active -> restore("blocks", block.id)
            }
        }

        ensure("Bloco A")
        ensure("Bloco B")
        existing = listBlocks(building.id)

        return existing
            .filter { it.name.equals("Bloco A", true) || it.name.equals("Bloco B", true) }
            .sortedBy { if (it.name.equals("Bloco A", true)) 1 else 2 }
    }

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
        val now = Instant.now().toString()
        client.from(table).update(
            ArchiveDto(active = false, archivedAt = now, archivedBy = adminId)
        ) { filter { eq("id", id) } }
    }

    suspend fun restore(table: String, id: String) {
        client.from(table).update(RestoreDto(active = true)) {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteArchived(entityType: String, id: String) {
        client.postgrest.rpc(
            function = "admin_delete_archived_entity",
            parameters = buildJsonObject {
                put("p_entity_type", entityType)
                put("p_id", id)
            },
        )
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

    suspend fun createQr(checkpointId: String): QrFunctionResponse = invokeQr("create", checkpointId)
    suspend fun replaceQr(checkpointId: String): QrFunctionResponse = invokeQr("replace", checkpointId)

    suspend fun listAlerts(includeResolved: Boolean = false): List<AlertDto> =
        client.from("alerts").select {
            if (!includeResolved) filter { exact("resolved_at", null) }
        }.decodeList<AlertDto>().sortedByDescending { it.createdAt }

    suspend fun markAlertRead(alertId: String) {
        client.from("alerts").update(AlertReadDto(readAt = Instant.now().toString())) {
            filter { eq("id", alertId) }
        }
    }

    suspend fun resolveAlert(alertId: String) {
        client.from("alerts").update(
            AlertResolveDto(resolvedAt = Instant.now().toString(), resolvedBy = currentAdminId())
        ) { filter { eq("id", alertId) } }
    }

    private suspend fun invokeQr(action: String, checkpointId: String): QrFunctionResponse {
        val response = client.functions.invoke(function = "admin-qr", body = QrFunctionRequest(action = action, checkpointId = checkpointId))
        return response.body<QrFunctionResponse>().also { it.error?.let(::error) }
    }
}
