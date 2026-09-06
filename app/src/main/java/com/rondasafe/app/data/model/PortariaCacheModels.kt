package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PortariaCacheResponse(
    @SerialName("generated_at") val generatedAt: String,
    val building: CachedBuildingDto,
    val guards: List<CachedGuardDto> = emptyList(),
    val patrols: List<CachedPatrolDto> = emptyList(),
    val windows: List<CachedWindowDto> = emptyList(),
    val assignments: List<CachedAssignmentDto> = emptyList(),
    val checkpoints: List<CachedCheckpointDto> = emptyList(),
    @SerialName("patrol_checkpoints") val patrolCheckpoints: List<CachedPatrolCheckpointDto> = emptyList(),
    @SerialName("qr_tokens") val qrTokens: List<CachedQrDto> = emptyList(),
)

@Serializable
data class CachedBuildingDto(
    val id: String,
    val name: String,
    val timezone: String,
)

@Serializable
data class CachedGuardCredentialDto(
    @SerialName("pin_hash") val pinHash: String,
    @SerialName("pin_salt") val pinSalt: String,
    val iterations: Int,
    @SerialName("must_change_pin") val mustChangePin: Boolean,
    @SerialName("credential_version") val credentialVersion: Int,
)

@Serializable
data class CachedGuardDto(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("pin_state") val pinState: String,
    val credential: CachedGuardCredentialDto,
)

@Serializable
data class CachedPatrolDto(
    val id: String,
    @SerialName("building_id") val buildingId: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class CachedWindowDto(
    val id: String,
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("late_tolerance_minutes") val lateToleranceMinutes: Int,
)

@Serializable
data class CachedAssignmentDto(
    @SerialName("schedule_window_id") val scheduleWindowId: String,
    @SerialName("guard_id") val guardId: String,
)

@Serializable
data class CachedCheckpointDto(
    val id: String,
    @SerialName("floor_id") val floorId: String,
    val name: String,
)

@Serializable
data class CachedPatrolCheckpointDto(
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("checkpoint_id") val checkpointId: String,
    val required: Boolean,
)

@Serializable
data class CachedQrDto(
    @SerialName("qr_token_id") val qrTokenId: String,
    @SerialName("checkpoint_id") val checkpointId: String,
    val version: Int,
    @SerialName("token_hash") val tokenHash: String,
)
