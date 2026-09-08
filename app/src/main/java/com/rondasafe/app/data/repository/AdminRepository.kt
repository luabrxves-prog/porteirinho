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
    private const val DEFAULT_PAGE_SIZE = 40
    private const val MAX_PAGE_SIZE = 100
    private val client get() = SupabaseProvider.client

    private fun currentAdminId(): String =
        client.auth.currentUserOrNull()?.id ?: error("Sessão administrativa não encontrada.")

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

    suspend fun checkpointOptions(
        buildingId: String? = null,
        floorId: String? = null,
    ): List<AdminCheckpointOptionDto> = client.postgrest.rpc(
        function = "admin_checkpoint_options",
        parameters = buildJsonObject {
            buildingId?.let { put("p_building_id", it) }
            floorId?.let { put("p_floor_id", it) }
        },
    ).decodeList()

    suspend fun condominium(): BuildingDto {
        val existing = listBuildings().firstOrNull()
        if (existing != null) return existing
        return createBuilding("Condomínio Solar Carlos Gomes")
    }

    suspend fun defaultBlocks(): List<BlockDto> {
        val building = condominium()
        return listBlocks(building.id)
            .filter { it.active && it.systemFixed }
            .sortedBy { it.sortOrder }
    }

    suspend fun createBuilding(name: String): BuildingDto = client.from("buildings")
        .insert(CreateBuildingDto(name = name.trim(), createdBy = currentAdminId())) { select() }.decodeSingle()

    suspend fun createBlock(buildingId: String, name: String): BlockDto = client.from("blocks")
        .insert(CreateBlockDto(buildingId = buildingId, name = name.trim(), createdBy = currentAdminId())) { select() }.decodeSingle()

    suspend fun createFloor(blockId: String, name: String): FloorDto = client.from("floors")
        .insert(CreateFloorDto(blockId = blockId, name = name.trim(), createdBy = currentAdminId())) { select() }.decodeSingle()

    suspend fun createCheckpoint(floorId: String, name: String, description: String?): CheckpointDto =
        client.from("checkpoints").insert(
            CreateCheckpointDto(
                floorId = floorId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = currentAdminId(),
            )
        ) { select() }.decodeSingle()

    suspend fun archive(table: String, id: String) {
        client.from(table).update(
            ArchiveDto(active = false, archivedAt = Instant.now().toString(), archivedBy = currentAdminId())
        ) { filter { eq("id", id) } }
    }

    suspend fun restore(table: String, id: String) {
        client.from(table).update(RestoreDto(active = true)) { filter { eq("id", id) } }
    }

    suspend fun deleteArchived(entityType: String, id: String) {
        client.postgrest.rpc("admin_delete_archived_entity", buildJsonObject {
            put("p_entity_type", entityType)
            put("p_id", id)
        })
    }

    suspend fun getActiveQr(checkpointId: String): ActiveQrDto? {
        val response = client.functions.invoke(
            function = "admin-qr",
            body = QrFunctionRequest(action = "get_active", checkpointId = checkpointId),
        )
        return response.body<QrFunctionResponse>().also { it.error?.let(::error) }.qr
    }

    suspend fun createQr(checkpointId: String): QrFunctionResponse = invokeQr("create", checkpointId)
    suspend fun replaceQr(checkpointId: String): QrFunctionResponse = invokeQr("replace", checkpointId)

    suspend fun alertsPage(
        from: Instant,
        to: Instant,
        resolution: String = "PENDING",
        severity: String? = null,
        guardId: String? = null,
        deviceId: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        cursor: PageCursor? = null,
    ): CursorPage<AlertDto> {
        require(!from.isAfter(to)) { "A data inicial não pode ser posterior à data final." }
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val loaded = client.postgrest.rpc(
            function = "admin_alerts_page",
            parameters = buildJsonObject {
                put("p_from", from.toString())
                put("p_to", to.toString())
                put("p_resolution", resolution)
                severity?.let { put("p_severity", it) }
                guardId?.let { put("p_guard_id", it) }
                deviceId?.let { put("p_device_id", it) }
                put("p_page_size", safePageSize)
                cursor?.let {
                    put("p_cursor_created_at", it.timestamp)
                    put("p_cursor_id", it.id)
                }
            },
        ).decodeList<AlertDto>()
        val hasMore = loaded.size > safePageSize
        val items = loaded.take(safePageSize)
        val last = items.lastOrNull()
        return CursorPage(
            items = items,
            hasMore = hasMore,
            nextCursor = if (hasMore && last != null) PageCursor(last.createdAt, last.id) else null,
        )
    }

    suspend fun listAlerts(includeResolved: Boolean = false): List<AlertDto> = alertsPage(
        from = Instant.now().minusSeconds(90L * 24L * 60L * 60L),
        to = Instant.now(),
        resolution = if (includeResolved) "ALL" else "PENDING",
        pageSize = DEFAULT_PAGE_SIZE,
    ).items

    suspend fun dashboardMetrics(from: Instant, to: Instant, buildingId: String? = null): AdminDashboardMetricsDto =
        client.postgrest.rpc(
            function = "admin_dashboard_metrics",
            parameters = buildJsonObject {
                put("p_from", from.toString())
                put("p_to", to.toString())
                buildingId?.let { put("p_building_id", it) }
            },
        ).decodeSingle()

    suspend fun operationsAggregate(
        from: Instant,
        to: Instant,
        granularity: String,
        buildingId: String? = null,
    ): List<AdminOperationsAggregateDto> = client.postgrest.rpc(
        function = "admin_operations_aggregate",
        parameters = buildJsonObject {
            put("p_from", from.toString())
            put("p_to", to.toString())
            put("p_granularity", granularity)
            buildingId?.let { put("p_building_id", it) }
        },
    ).decodeList()

    suspend fun markAlertRead(alertId: String) {
        client.from("alerts").update(AlertReadDto(readAt = Instant.now().toString())) { filter { eq("id", alertId) } }
    }

    suspend fun resolveAlert(alertId: String) {
        client.from("alerts").update(
            AlertResolveDto(resolvedAt = Instant.now().toString(), resolvedBy = currentAdminId())
        ) { filter { eq("id", alertId) } }
    }

    private suspend fun invokeQr(action: String, checkpointId: String): QrFunctionResponse {
        val response = client.functions.invoke(
            function = "admin-qr",
            body = QrFunctionRequest(action = action, checkpointId = checkpointId),
        )
        return response.body<QrFunctionResponse>().also { it.error?.let(::error) }
    }
}
