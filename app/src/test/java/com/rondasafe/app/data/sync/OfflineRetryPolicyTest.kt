package com.rondasafe.app.data.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRetryPolicyTest {
    private val policy = OfflineRetryPolicy(maxParentRetries = 6)

    @Test
    fun `ack is synced`() {
        assertTrue(policy.decide(OfflineIngestResponse(ack = true), 0) is OfflineSyncDecision.Synced)
    }

    @Test
    fun `server permanent rejection is not retried`() {
        val decision = policy.decide(
            OfflineIngestResponse(ack = false, retryable = false, error = "INVALID_EVENT"),
            attempts = 0,
        )
        assertTrue(decision is OfflineSyncDecision.PermanentFailure)
    }

    @Test
    fun `parent dependency retries before limit`() {
        val decision = policy.decide(
            OfflineIngestResponse(ack = false, retryable = true, error = "PARENT_RUN_NOT_SYNCED"),
            attempts = 2,
        )
        assertTrue(decision is OfflineSyncDecision.Retry)
    }

    @Test
    fun `parent dependency becomes permanent after retry limit`() {
        val decision = policy.decide(
            OfflineIngestResponse(ack = false, retryable = true, error = "PARENT_RUN_NOT_SYNCED"),
            attempts = 5,
        )
        assertTrue(decision is OfflineSyncDecision.PermanentFailure)
    }
}
