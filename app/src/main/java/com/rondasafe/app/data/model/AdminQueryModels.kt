package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdminDashboardMetricsDto(
    val scheduled: Int = 0,
    @SerialName("completed_ok") val completedOk: Int = 0,
    val attention: Int = 0,
    @SerialName("in_progress") val inProgress: Int = 0,
    val missed: Int = 0,
    @SerialName("open_alerts") val openAlerts: Int = 0,
)

@Serializable
data class AdminOperationsAggregateDto(
    @SerialName("bucket_start") val bucketStart: String,
    val scheduled: Int = 0,
    val completed: Int = 0,
    val incomplete: Int = 0,
    val late: Int = 0,
    val suspicious: Int = 0,
    val missed: Int = 0,
    val alerts: Int = 0,
    val occurrences: Int = 0,
)

@Serializable
data class AdminCheckpointOptionDto(
    @SerialName("checkpoint_id") val checkpointId: String,
    @SerialName("checkpoint_name") val checkpointName: String,
    @SerialName("floor_id") val floorId: String,
    @SerialName("floor_name") val floorName: String,
    @SerialName("block_id") val blockId: String,
    @SerialName("block_name") val blockName: String,
    @SerialName("building_id") val buildingId: String,
    @SerialName("building_name") val buildingName: String,
    @SerialName("qr_ready") val qrReady: Boolean,
)
