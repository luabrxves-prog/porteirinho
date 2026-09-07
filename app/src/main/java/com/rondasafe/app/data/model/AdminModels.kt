package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuildingDto(
    val id: String,
    val name: String,
    val active: Boolean,
)

@Serializable
data class BlockDto(
    val id: String,
    @SerialName("building_id") val buildingId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val active: Boolean,
)

@Serializable
data class FloorDto(
    val id: String,
    @SerialName("block_id") val blockId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val active: Boolean,
)

@Serializable
data class CheckpointDto(
    val id: String,
    @SerialName("floor_id") val floorId: String,
    val name: String,
    val description: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val active: Boolean,
)

@Serializable
data class CreateBuildingDto(
    val name: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreateBlockDto(
    @SerialName("building_id") val buildingId: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreateFloorDto(
    @SerialName("block_id") val blockId: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreateCheckpointDto(
    @SerialName("floor_id") val floorId: String,
    val name: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class ArchiveDto(
    val active: Boolean,
    @SerialName("archived_at") val archivedAt: String,
    @SerialName("archived_by") val archivedBy: String,
)

@Serializable
data class RestoreDto(
    val active: Boolean,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("archived_by") val archivedBy: String? = null,
)

@Serializable
data class QrFunctionRequest(
    val action: String,
    @SerialName("checkpoint_id") val checkpointId: String,
)

@Serializable
data class QrFunctionResponse(
    @SerialName("qr_token_id") val qrTokenId: String? = null,
    val version: Int? = null,
    val fingerprint: String? = null,
    @SerialName("token_value") val tokenValue: String? = null,
    val qr: ActiveQrDto? = null,
    val error: String? = null,
)

@Serializable
data class ActiveQrDto(
    @SerialName("qr_token_id") val qrTokenId: String,
    val version: Int,
    @SerialName("token_value") val tokenValue: String,
    val fingerprint: String,
    @SerialName("created_at") val createdAt: String,
)
