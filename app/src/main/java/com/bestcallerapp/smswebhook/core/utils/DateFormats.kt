package com.bestcallerapp.smswebhook.core.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateFormats {
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** ISO-8601 with offset, e.g. 2026-10-05T20:30:00+05:30. */
    fun isoOffset(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(epochMillis).atZone(zone).withNano(0).toOffsetDateTime(),
        )

    fun display(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DISPLAY.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
