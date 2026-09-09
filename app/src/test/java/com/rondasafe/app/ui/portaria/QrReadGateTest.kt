package com.rondasafe.app.ui.portaria

import org.junit.Assert.*
import org.junit.Test

class QrReadGateTest {
    @Test fun acceptsFirstRead() { assertTrue(QrReadGate().acquire("qr-a", 100)) }
    @Test fun blocksOverlappingFramesEvenFromAnotherCode() {
        val gate = QrReadGate(); assertTrue(gate.acquire("a", 100)); assertFalse(gate.acquire("b", 101))
    }
    @Test fun repeatedImageIsNotEnqueuedAgainDuringCooldown() {
        val gate = QrReadGate(); gate.acquire("a", 100); gate.release(); assertFalse(gate.acquire("a", 14_999))
    }
    @Test fun nextPointDoesNotWaitForCooldown() {
        val gate = QrReadGate(); gate.acquire("a", 100); gate.release(); assertTrue(gate.acquire("b", 101))
    }
    @Test fun samePointCanRetryAfterCooldown() {
        val gate = QrReadGate(); gate.acquire("a", 100); gate.release(); assertTrue(gate.acquire("a", 15_100))
    }
    @Test fun blankImageIsNotSubmitted() { assertFalse(QrReadGate().acquire(" ", 100)) }
}
