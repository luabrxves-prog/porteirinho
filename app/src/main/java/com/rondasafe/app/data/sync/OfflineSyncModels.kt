package com.rondasafe.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class OfflineIngestResponse(
    val ack: Boolean = false,
    val retryable: Boolean? = null,
    val error: String? = null,
)

enum class OfflineSyncOutcome {
    SUCCESS,
    RETRY,
}

sealed interface OfflineSyncDecision {
    data object Synced : OfflineSyncDecision
    data class Retry(val reason: String) : OfflineSyncDecision
    data class PermanentFailure(val reason: String) : OfflineSyncDecision
}
