package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    val id: String,
    @SerialName("alert_type") val alertType: String,
    val severity: String,
    @SerialName("patrol_run_id") val patrolRunId: String? = null,
    @SerialName("guard_id") val guardId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val title: String,
    val message: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
)

@Serializable
data class AlertReadDto(
    @SerialName("read_at") val readAt: String,
)

@Serializable
data class AlertResolveDto(
    @SerialName("resolved_at") val resolvedAt: String,
    @SerialName("resolved_by") val resolvedBy: String,
)
