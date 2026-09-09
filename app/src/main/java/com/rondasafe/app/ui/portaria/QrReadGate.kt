package com.rondasafe.app.ui.portaria

/** Original 15-second duplicate guard, shared by camera callbacks and UI tests. */
class QrReadGate(private val cooldownMs: Long = 15_000L) {
    private var inFlight = false
    private var lastQr: String? = null
    private var lastAt = 0L
    @Synchronized fun acquire(qr: String, now: Long): Boolean {
        if (inFlight || qr.isBlank()) return false
        if (lastQr == qr && now >= lastAt && now - lastAt < cooldownMs) return false
        lastQr = qr
        lastAt = now
        inFlight = true
        return true
    }
    @Synchronized fun release() { inFlight = false }
}
