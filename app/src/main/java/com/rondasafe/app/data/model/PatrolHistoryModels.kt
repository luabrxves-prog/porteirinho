package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PatrolHistoryItemDto(
    val id: String,
    val source: String,
    @SerialName("patrol_run_id") val patrolRunId: String? = null,
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("schedule_window_id") val scheduleWindowId: String? = null,
    @SerialName("patrol_name") val patrolName: String,
    @SerialName("guard_id") val guardId: String? = null,
    @SerialName("guard_name") val guardName: String? = null,
    @SerialName("building_id") val buildingId: String,
    @SerialName("building_name") val buildingName: String,
    @SerialName("scheduled_for") val scheduledFor: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("display_status") val displayStatus: String,
    @SerialName("is_late") val isLate: Boolean,
    @SerialName("captured_offline") val capturedOffline: Boolean,
    val suspicious: Boolean,
    @SerialName("required_points") val requiredPoints: Int,
    @SerialName("visited_points") val visitedPoints: Int,
    @SerialName("missing_points") val missingPoints: Int,
)

@Serializable
data class PatrolHistoryPointDto(
    @SerialName("checkpoint_id") val checkpointId: String,
    @SerialName("checkpoint_name") val checkpointName: String,
    @SerialName("building_name") val buildingName: String,
    @SerialName("block_name") val blockName: String,
    @SerialName("floor_name") val floorName: String,
    val visited: Boolean,
    /** Absolute event instant captured by Android. */
    @SerialName("first_scan_at") val firstScanAt: String? = null,
    /** Server receipt instant; intentionally separate from the event instant. */
    @SerialName("server_received_at") val serverReceivedAt: String? = null,
    /** Android ZoneId active when the QR was scanned, e.g. America/Sao_Paulo. */
    @SerialName("captured_zone_id") val capturedZoneId: String? = null,
    /** Android UTC offset in seconds active when the QR was scanned. */
    @SerialName("captured_offset_seconds") val capturedOffsetSeconds: Int? = null,
    /** Human/audit copy of the local wall-clock value at capture time. */
    @SerialName("captured_local_datetime") val capturedLocalDateTime: String? = null,
)

@Serializable
data class PatrolHistoryParams(
    @SerialName("p_from") val from: String,
    @SerialName("p_to") val to: String,
    @SerialName("p_status") val status: String? = null,
    @SerialName("p_guard_id") val guardId: String? = null,
    @SerialName("p_building_id") val buildingId: String? = null,
    @SerialName("p_block_id") val blockId: String? = null,
    @SerialName("p_floor_id") val floorId: String? = null,
    @SerialName("p_limit") val limit: Int = 200,
)

@Serializable
data class PatrolHistoryPointsParams(
    @SerialName("p_run_id") val runId: String? = null,
    @SerialName("p_patrol_template_id") val patrolTemplateId: String? = null,
    @SerialName("p_scheduled_for") val scheduledFor: String? = null,
)
