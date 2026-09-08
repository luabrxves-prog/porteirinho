package com.rondasafe.app.data.sync

class OfflineRetryPolicy(
    private val maxParentRetries: Int = 6,
) {
    fun decide(response: OfflineIngestResponse, attempts: Int): OfflineSyncDecision {
        if (response.ack) return OfflineSyncDecision.Synced

        val errorCode = response.error.orEmpty()
        if (response.retryable == false) {
            return OfflineSyncDecision.PermanentFailure(
                errorCode.ifBlank { "O servidor rejeitou definitivamente este evento." }
            )
        }

        val parentNotSynced = errorCode == "PARENT_SHIFT_NOT_SYNCED" ||
            errorCode == "PARENT_RUN_NOT_SYNCED"

        if (parentNotSynced && attempts >= maxParentRetries - 1) {
            return OfflineSyncDecision.PermanentFailure(
                "Falha de vínculo não recuperada após várias tentativas ($errorCode)."
            )
        }

        return OfflineSyncDecision.Retry(
            errorCode.ifBlank { "Falha temporária de sincronização." }
        )
    }
}
