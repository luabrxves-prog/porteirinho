package com.rondasafe.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTimeTest {
    @Test
    fun `formats UTC instant in Sao Paulo timezone`() {
        assertEquals("14:57", AppTime.time("2026-09-07T17:57:00Z"))
        assertEquals("07/09/2026 • 14:57", AppTime.dateTime("2026-09-07T17:57:00Z"))
    }

    @Test
    fun `keeps invalid values readable instead of crashing`() {
        assertEquals("invalid", AppTime.time("invalid"))
    }
}
