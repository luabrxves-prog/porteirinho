package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.CursorPage
import com.rondasafe.app.data.model.PageCursor
import com.rondasafe.app.data.model.PatrolHistoryItemDto
import com.rondasafe.app.data.model.PatrolHistoryPointDto
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PatrolHistoryRepository {
    private const val DEFAULT_PAGE_SIZE = 40
    private const val MAX_PAGE_SIZE = 100
    private val client get() = SupabaseProvider.client

    suspend fun page(
        from: Instant,
        to: Instant,
        status: String? = null,
        guardId: String? = null,
        buildingId: String? = null,
        blockId: String? = null,
        floorId: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        cursor: PageCursor? = null,
    ): CursorPage<PatrolHistoryItemDto> {
        require(!from.isAfter(to)) { "A data inicial não pode ser posterior à data final." }
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val parameters = buildJsonObject {
            put("p_from", from.toString())
            put("p_to", to.toString())
            status?.let { put("p_status", it) }
            guardId?.let { put("p_guard_id", it) }
            buildingId?.let { put("p_building_id", it) }
            blockId?.let { put("p_block_id", it) }
            floorId?.let { put("p_floor_id", it) }
            put("p_page_size", safePageSize)
            cursor?.let {
                put("p_cursor_scheduled_for", it.timestamp)
                put("p_cursor_id", it.id)
            }
        }
        val loaded = client.postgrest.rpc(
            function = "admin_patrol_history_page",
            parameters = parameters,
        ).decodeList<PatrolHistoryItemDto>()
        val hasMore = loaded.size > safePageSize
        val items = loaded.take(safePageSize)
        val last = items.lastOrNull()
        return CursorPage(
            items = items,
            hasMore = hasMore,
            nextCursor = if (hasMore && last != null) PageCursor(last.scheduledFor, last.id) else null,
        )
    }

    /** Compatibility helper. Intentionally returns only the first bounded page. */
    suspend fun listRange(
        from: Instant,
        to: Instant,
        status: String? = null,
        guardId: String? = null,
        buildingId: String? = null,
        blockId: String? = null,
        floorId: String? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): List<PatrolHistoryItemDto> = page(
        from = from,
        to = to,
        status = status,
        guardId = guardId,
        buildingId = buildingId,
        blockId = blockId,
        floorId = floorId,
        pageSize = limit.coerceAtMost(MAX_PAGE_SIZE),
    ).items

    suspend fun list(
        days: Int = 30,
        status: String? = null,
        guardId: String? = null,
        buildingId: String? = null,
        blockId: String? = null,
        floorId: String? = null,
    ): List<PatrolHistoryItemDto> {
        val now = Instant.now()
        return listRange(
            from = now.minus(days.toLong(), ChronoUnit.DAYS),
            to = now,
            status = status,
            guardId = guardId,
            buildingId = buildingId,
            blockId = blockId,
            floorId = floorId,
        )
    }

    suspend fun points(item: PatrolHistoryItemDto): List<PatrolHistoryPointDto> {
        val parameters = buildJsonObject {
            item.patrolRunId?.let { put("p_run_id", it) }
            if (item.patrolRunId == null) put("p_patrol_template_id", item.patrolTemplateId)
            put("p_scheduled_for", item.scheduledFor)
        }
        return client.postgrest.rpc(
            function = "admin_patrol_history_points",
            parameters = parameters,
        ).decodeList()
    }
}
