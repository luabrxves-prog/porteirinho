package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PortariaGuardDto(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("pin_state") val pinState: String,
)

@Serializable
data class DeviceProvisionRequest(
    val action: String,
    @SerialName("installation_id") val installationId: String,
    @SerialName("building_id") val buildingId: String,
    val name: String,
    val model: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class DeviceProvisionedDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("building_id") val buildingId: String,
    @SerialName("credential_version") val credentialVersion: Int,
)

@Serializable
data class DeviceProvisionResponse(
    val device: DeviceProvisionedDto? = null,
    @SerialName("device_secret") val deviceSecret: String? = null,
    val error: String? = null,
)

@Serializable
data class PortariaRequest(
    val action: String,
    @SerialName("guard_id") val guardId: String? = null,
    val pin: String? = null,
    @SerialName("new_pin") val newPin: String? = null,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("patrol_template_id") val patrolTemplateId: String? = null,
    @SerialName("schedule_window_id") val scheduleWindowId: String? = null,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("is_late") val isLate: Boolean? = null,
    @SerialName("run_id") val runId: String? = null,
    val qr: String? = null,
    @SerialName("captured_at_local") val capturedAtLocal: String? = null,
    @SerialName("captured_monotonic_ms") val capturedMonotonicMs: Long? = null,
    @SerialName("started_at_local") val startedAtLocal: String? = null,
    @SerialName("finished_at_local") val finishedAtLocal: String? = null,
    @SerialName("ended_at_local") val endedAtLocal: String? = null,
    @SerialName("client_event_id") val clientEventId: String? = null,
)

@Serializable
data class GuardLoginDto(val id: String, val name: String, @SerialName("pin_state") val pinState: String)

@Serializable
data class GuardLoginResponse(
    val guard: GuardLoginDto? = null,
    @SerialName("must_change_pin") val mustChangePin: Boolean = false,
    @SerialName("guard_session") val guardSession: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val error: String? = null,
    @SerialName("remaining_attempts") val remainingAttempts: Int? = null,
)

@Serializable
data class GuardListResponse(val guards: List<PortariaGuardDto> = emptyList(), val error: String? = null)

@Serializable
data class ShiftDto(@SerialName("shift_id") val shiftId: String, @SerialName("started_at_server") val startedAtServer: String)
@Serializable
data class ShiftResponse(val shift: ShiftDto? = null, val error: String? = null)

@Serializable
data class AvailablePatrolDto(
    @SerialName("patrol_template_id") val patrolTemplateId: String,
    @SerialName("schedule_window_id") val scheduleWindowId: String,
    @SerialName("patrol_name") val patrolName: String,
    @SerialName("scheduled_for") val scheduledFor: String,
    @SerialName("available_until") val availableUntil: String,
    @SerialName("is_late") val isLate: Boolean,
    @SerialName("required_points") val requiredPoints: Int,
)
@Serializable
data class AvailablePatrolsResponse(val patrols: List<AvailablePatrolDto> = emptyList(), val error: String? = null)

@Serializable
data class PatrolRunDto(
    @SerialName("run_id") val runId: String,
    @SerialName("required_points") val requiredPoints: Int,
    @SerialName("started_at_server") val startedAtServer: String,
)
@Serializable
data class PatrolRunResponse(val run: PatrolRunDto? = null, val error: String? = null)

@Serializable
data class ScanDto(
    @SerialName("scan_result") val scanResult: String,
    @SerialName("checkpoint_id") val checkpointId: String? = null,
    @SerialName("visited_points") val visitedPoints: Int,
    @SerialName("total_points") val totalPoints: Int,
    @SerialName("checkpoint_name") val checkpointName: String? = null,
)
@Serializable
data class ScanResponse(val scan: ScanDto? = null, val error: String? = null)

@Serializable
data class FinishPatrolDto(
    val status: String,
    @SerialName("visited_points") val visitedPoints: Int,
    @SerialName("total_points") val totalPoints: Int,
    @SerialName("missing_checkpoint_ids") val missingCheckpointIds: List<String> = emptyList(),
)
@Serializable
data class FinishPatrolResponse(val result: FinishPatrolDto? = null, val error: String? = null)

@Serializable
data class SimplePortariaResponse(val ok: Boolean = false, val error: String? = null)
