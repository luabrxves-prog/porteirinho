package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PatrolTemplateDto(
    val id: String,
    @SerialName("building_id") val buildingId: String,
    val name: String,
    val description: String? = null,
    val active: Boolean,
)

@Serializable
data class PatrolScheduleWindowDto(
    val id: String,
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("late_tolerance_minutes") val lateToleranceMinutes: Int,
    val active: Boolean,
)

@Serializable
data class UpdatePatrolScheduleWindowTimeDto(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
data class PatrolTemplateCheckpointDto(
    val id: String,
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("checkpoint_id") val checkpointId: String,
    val required: Boolean,
    val active: Boolean,
)

@Serializable
data class PatrolScheduleAssignmentDto(
    val id: String,
    @SerialName("schedule_window_id") val scheduleWindowId: String,
    @SerialName("guard_id") val guardId: String,
    val active: Boolean,
)

@Serializable
data class CreatePatrolTemplateDto(
    @SerialName("building_id") val buildingId: String,
    val name: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreatePatrolScheduleWindowDto(
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("late_tolerance_minutes") val lateToleranceMinutes: Int,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreatePatrolTemplateCheckpointDto(
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("checkpoint_id") val checkpointId: String,
    val required: Boolean = true,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class CreatePatrolScheduleAssignmentDto(
    @SerialName("schedule_window_id") val scheduleWindowId: String,
    @SerialName("guard_id") val guardId: String,
    val active: Boolean = true,
    @SerialName("created_by") val createdBy: String,
)

data class PatrolDayConfig(
    val dayOfWeek: Int,
    val enabled: Boolean,
    val startTime: String,
    val endTime: String,
)

data class PatrolCheckpointOption(
    val checkpoint: CheckpointDto,
    val label: String,
)
