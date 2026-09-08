package com.rondasafe.app.core.constants

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PatrolExecutionStatus {
    @SerialName("AVAILABLE") AVAILABLE,
    @SerialName("IN_PROGRESS") IN_PROGRESS,
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("INCOMPLETE") INCOMPLETE,
}

@Serializable
enum class PatrolScanResult {
    @SerialName("ACCEPTED") ACCEPTED,
    @SerialName("DUPLICATE") DUPLICATE,
    @SerialName("REVOKED_QR") REVOKED_QR,
    @SerialName("NOT_IN_ROUND") NOT_IN_ROUND,
    @SerialName("UNKNOWN_QR") UNKNOWN_QR,
}

@Serializable
enum class OfflineEventType {
    @SerialName("SHIFT_STARTED") SHIFT_STARTED,
    @SerialName("PATROL_STARTED") PATROL_STARTED,
    @SerialName("QR_SCANNED") QR_SCANNED,
    @SerialName("PATROL_FINISHED") PATROL_FINISHED,
    @SerialName("SHIFT_ENDED") SHIFT_ENDED,
    @SerialName("GUARD_OCCURRENCE") GUARD_OCCURRENCE,
}

@Serializable
enum class OfflineEventState {
    @SerialName("PENDING") PENDING,
    @SerialName("FAILED_PERMANENT") FAILED_PERMANENT,
}

@Serializable
enum class GuardPinState {
    @SerialName("TEMPORARY") TEMPORARY,
    @SerialName("PERSONAL") PERSONAL,
}
