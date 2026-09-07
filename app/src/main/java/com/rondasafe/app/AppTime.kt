package com.rondasafe.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppTime {
    val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy • HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun nowDate(): LocalDate = LocalDate.now(zone)

    fun dateTime(value: String): String = runCatching {
        Instant.parse(value).atZone(zone).format(dateTimeFormatter)
    }.getOrElse { value }

    fun time(value: String): String = runCatching {
        Instant.parse(value).atZone(zone).format(timeFormatter)
    }.getOrElse { value }

    fun date(value: LocalDate): String = value.format(dateFormatter)

    fun scheduledLabel(value: String): String = runCatching {
        val dateTime = Instant.parse(value).atZone(zone)
        if (dateTime.toLocalDate() == nowDate()) {
            "Prevista para ${dateTime.format(timeFormatter)}"
        } else {
            "Prevista em ${dateTime.format(dateTimeFormatter)}"
        }
    }.getOrElse { "Prevista em $value" }
}
