package com.rondasafe.app

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class CapturedDeviceTime(
    val instant: Instant,
    val zoneId: String,
    val offsetSeconds: Int,
    val localDateTime: String,
)

object AppTime {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy • HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val hourOnlyOffset = Regex("[+-]\\d{2}$")

    /**
     * The app does not own a fixed timezone. When a UI needs the device-local
     * timezone, Android's current system setting is the source of truth.
     */
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun zoneLabel(zone: ZoneId = zone()): String = zone.id

    fun nowDate(zone: ZoneId = zone()): LocalDate = LocalDate.now(zone)

    /**
     * Captures an operational event as an absolute Instant plus the Android
     * timezone/offset that was active at the exact moment of capture.
     */
    fun captureEvent(
        instant: Instant = Instant.now(),
        zone: ZoneId = zone(),
    ): CapturedDeviceTime {
        val zoned = instant.atZone(zone)
        return CapturedDeviceTime(
            instant = instant,
            zoneId = zone.id,
            offsetSeconds = zoned.offset.totalSeconds,
            localDateTime = zoned.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
    }

    /**
     * Postgres/Supabase may return timestamps with a space instead of T and an
     * hour-only offset such as +00. Normalize those forms before parsing.
     */
    fun parseInstant(value: String): Instant {
        var normalized = value.trim().replace(' ', 'T')
        if (hourOnlyOffset.containsMatchIn(normalized)) normalized += ":00"

        return runCatching { Instant.parse(normalized) }
            .recoverCatching { OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
            .getOrThrow()
    }

    fun eventZone(zoneId: String?, offsetSeconds: Int?): ZoneId {
        if (!zoneId.isNullOrBlank()) {
            runCatching { ZoneId.of(zoneId) }.getOrNull()?.let { return it }
        }
        if (offsetSeconds != null && offsetSeconds in -18 * 3600..18 * 3600) {
            runCatching { ZoneOffset.ofTotalSeconds(offsetSeconds) }.getOrNull()?.let { return it }
        }
        return zone()
    }

    fun dateTime(value: String, zone: ZoneId = zone()): String = runCatching {
        parseInstant(value).atZone(zone).format(dateTimeFormatter)
    }.getOrElse { value }

    fun time(value: String, zone: ZoneId = zone()): String = runCatching {
        parseInstant(value).atZone(zone).format(timeFormatter)
    }.getOrElse { value }

    fun date(value: LocalDate): String = value.format(dateFormatter)

    fun scheduledLabel(value: String, zone: ZoneId = zone()): String = runCatching {
        val dateTime = parseInstant(value).atZone(zone)
        if (dateTime.toLocalDate() == nowDate(zone)) {
            "Prevista para ${dateTime.format(timeFormatter)}"
        } else {
            "Prevista em ${dateTime.format(dateTimeFormatter)}"
        }
    }.getOrElse { "Prevista em $value" }
}
