package com.rondasafe.app.domain.service

import com.rondasafe.app.core.constants.PatrolScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

class QrScanEvaluatorTest {
    private val required = setOf("checkpoint-a", "checkpoint-b")

    @Test
    fun `valid QR is accepted`() {
        assertEquals(
            PatrolScanResult.ACCEPTED,
            QrScanEvaluator.evaluate("checkpoint-a", required, alreadyVisited = false),
        )
    }

    @Test
    fun `already visited QR is duplicate`() {
        assertEquals(
            PatrolScanResult.DUPLICATE,
            QrScanEvaluator.evaluate("checkpoint-a", required, alreadyVisited = true),
        )
    }

    @Test
    fun `known QR outside patrol is rejected`() {
        assertEquals(
            PatrolScanResult.NOT_IN_ROUND,
            QrScanEvaluator.evaluate("checkpoint-x", required, alreadyVisited = false),
        )
    }

    @Test
    fun `unknown QR is rejected`() {
        assertEquals(
            PatrolScanResult.UNKNOWN_QR,
            QrScanEvaluator.evaluate(null, required, alreadyVisited = false),
        )
    }
}
