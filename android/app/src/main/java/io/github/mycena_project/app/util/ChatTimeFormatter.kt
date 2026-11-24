// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object ChatTimeFormatter {
    private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

    fun format(timestampMs: ULong): String {
        val zone = ZoneId.systemDefault()
        val whenSent = Instant.ofEpochMilli(timestampMs.toLong()).atZone(zone)
        val now = Instant.now().atZone(zone)
        val daysApart = ChronoUnit.DAYS.between(whenSent.toLocalDate(), now.toLocalDate())
        return when {
            daysApart == 0L -> whenSent.format(timeOnly)
            daysApart == 1L -> "Yesterday, ${whenSent.format(timeOnly)}"
            daysApart < 7L -> "${whenSent.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}, ${whenSent.format(timeOnly)}"
            else -> whenSent.format(dateTime)
        }
    }
}
