package com.rondasafe.app.core.constants

import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.ScanDto

enum class PatrolHistoryStatus {
    COMPLETED,
    INCOMPLETE,
    MISSED,
    LATE,
    IN_PROGRESS,
    UNKNOWN;

    companion object {
        fun fromWire(value: String): PatrolHistoryStatus =
            entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN
    }
}

val AvailablePatrolDto.executionStatusType: PatrolExecutionStatus
    get() = PatrolExecutionStatus.entries.firstOrNull { it.name == executionStatus.uppercase() }
        ?: PatrolExecutionStatus.AVAILABLE

val ScanDto.scanResultType: PatrolScanResult
    get() = PatrolScanResult.entries.firstOrNull { it.name == scanResult.uppercase() }
        ?: PatrolScanResult.UNKNOWN_QR

val FinishPatrolDto.statusType: PatrolExecutionStatus
    get() = PatrolExecutionStatus.entries.firstOrNull { it.name == status.uppercase() }
        ?: PatrolExecutionStatus.INCOMPLETE
