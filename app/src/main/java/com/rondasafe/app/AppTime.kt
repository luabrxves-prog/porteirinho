package com.rondasafe.app

import java.time.LocalDate
import java.time.ZoneId

/**
 * Compatibility facade while screens migrate to core.time.AppTime.
 * New code should import com.rondasafe.app.core.time.AppTime directly.
 */
@Deprecated("Use com.rondasafe.app.core.time.AppTime")
object AppTime {
    val zone: ZoneId get() = com.rondasafe.app.core.time.AppTime.zone

    fun nowDate(): LocalDate = com.rondasafe.app.core.time.AppTime.nowDate()
    fun dateTime(value: String): String = com.rondasafe.app.core.time.AppTime.dateTime(value)
    fun time(value: String): String = com.rondasafe.app.core.time.AppTime.time(value)
    fun date(value: LocalDate): String = com.rondasafe.app.core.time.AppTime.date(value)
    fun scheduledLabel(value: String): String = com.rondasafe.app.core.time.AppTime.scheduledLabel(value)
}
