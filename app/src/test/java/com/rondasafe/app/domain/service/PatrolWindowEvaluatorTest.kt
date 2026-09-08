package com.rondasafe.app.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PatrolWindowEvaluatorTest {
    @Test
    fun `normal window is available only on configured day and interval`() {
        val monday = LocalDate.of(2026, 9, 7)
        val active = PatrolWindowEvaluator.activeOccurrence(
            dayOfWeek = 1,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
            now = LocalDateTime.of(monday, LocalTime.of(15, 30)),
        )
        assertNotNull(active)
        assertEquals(monday, active?.scheduledDate)
        assertEquals(monday, active?.availableUntilDate)

        val outside = PatrolWindowEvaluator.activeOccurrence(
            dayOfWeek = 1,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(22, 0),
            now = LocalDateTime.of(monday, LocalTime.of(23, 0)),
        )
        assertNull(outside)
    }

    @Test
    fun `overnight window keeps previous day schedule after midnight`() {
        val monday = LocalDate.of(2026, 9, 7)
        val tuesday = monday.plusDays(1)
        val active = PatrolWindowEvaluator.activeOccurrence(
            dayOfWeek = 1,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(2, 0),
            now = LocalDateTime.of(tuesday, LocalTime.of(1, 15)),
        )
        assertNotNull(active)
        assertEquals(monday, active?.scheduledDate)
        assertEquals(tuesday, active?.availableUntilDate)
    }
}
