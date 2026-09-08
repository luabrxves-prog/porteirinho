package com.rondasafe.app.domain.service

import com.rondasafe.app.core.constants.PatrolScanResult

object QrScanEvaluator {
    fun evaluate(
        checkpointId: String?,
        requiredCheckpointIds: Set<String>,
        alreadyVisited: Boolean,
    ): PatrolScanResult = when {
        checkpointId == null -> PatrolScanResult.UNKNOWN_QR
        checkpointId !in requiredCheckpointIds -> PatrolScanResult.NOT_IN_ROUND
        alreadyVisited -> PatrolScanResult.DUPLICATE
        else -> PatrolScanResult.ACCEPTED
    }
}
