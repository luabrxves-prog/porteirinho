package com.rondasafe.app.domain.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class PatrolWindowOccurrence(
    val scheduledDate: LocalDate,
    val availableUntilDate: LocalDate,
)

object PatrolWindowEvaluator {
    fun activeOccurrence(
        dayOfWeek: Int,
        start: LocalTime,
        end: LocalTime,
        now: LocalDateTime,
    ): PatrolWindowOccurrence? {
        val isoDay = now.dayOfWeek.value
        val localNow = now.toLocalTime()

        val active = when {
            end > start -> dayOfWeek == isoDay && !localNow.isBefore(start) && !localNow.isAfter(end)
            else -> {
                val previousDay = if (isoDay == DayOfWeek.MONDAY.value) 7 else isoDay - 1
                (dayOfWeek == isoDay && !localNow.isBefore(start)) ||
                    (dayOfWeek == previousDay && !localNow.isAfter(end))
            }
        }
        if (!active) return null

        val scheduledDate = when {
            end > start -> now.toLocalDate()
            !localNow.isBefore(start) -> now.toLocalDate()
            else -> now.toLocalDate().minusDays(1)
        }
        val availableUntilDate = if (end > start) scheduledDate else scheduledDate.plusDays(1)

        return PatrolWindowOccurrence(
            scheduledDate = scheduledDate,
            availableUntilDate = availableUntilDate,
        )
    }
}
