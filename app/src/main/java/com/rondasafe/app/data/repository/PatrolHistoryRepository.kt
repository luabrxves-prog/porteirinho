package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.PatrolHistoryItemDto
import com.rondasafe.app.data.model.PatrolHistoryParams
import com.rondasafe.app.data.model.PatrolHistoryPointDto
import com.rondasafe.app.data.model.PatrolHistoryPointsParams
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.temporal.ChronoUnit

object PatrolHistoryRepository {
    private val client get() = SupabaseProvider.client

    suspend fun list(
        days: Int = 30,
        status: String? = null,
        guardId: String? = null,
        buildingId: String? = null,
        blockId: String? = null,
        floorId: String? = null,
    ): List<PatrolHistoryItemDto> {
        val now = Instant.now()
        return client.postgrest.rpc(
            function = "admin_patrol_history",
            parameters = PatrolHistoryParams(
                from = now.minus(days.toLong(), ChronoUnit.DAYS).toString(),
                to = now.toString(),
                status = status,
                guardId = guardId,
                buildingId = buildingId,
                blockId = blockId,
                floorId = floorId,
            ),
        ).decodeList()
    }

    suspend fun points(item: PatrolHistoryItemDto): List<PatrolHistoryPointDto> =
        client.postgrest.rpc(
            function = "admin_patrol_history_points",
            parameters = PatrolHistoryPointsParams(
                runId = item.patrolRunId,
                patrolTemplateId = if (item.patrolRunId == null) item.patrolTemplateId else null,
                scheduledFor = item.scheduledFor,
            ),
        ).decodeList()
}
