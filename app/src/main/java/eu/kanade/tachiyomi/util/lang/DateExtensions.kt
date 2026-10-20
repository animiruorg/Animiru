package eu.kanade.tachiyomi.util.lang

import android.content.Context
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant

fun LocalDateTime.toDateTimestampString(dateTimeFormatter: DateTimeFormatter): String {
    val javaLocalDateTime = this.toJavaLocalDateTime()
    val date = dateTimeFormatter.format(javaLocalDateTime)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(javaLocalDateTime)
    return "$date $time"
}

fun Instant.toTimestampString(): String {
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime())
}

fun Long.convertEpochMillisZone(
    from: TimeZone,
    to: TimeZone,
): Long {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(from)
        .toInstant(to)
        .toEpochMilliseconds()
}

fun Long.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this).toLocalDate()
}

fun Instant.toLocalDate(): LocalDate {
    return toLocalDateTime(TimeZone.currentSystemDefault()).date
}

fun Instant.toJavaLocalDate(): java.time.LocalDate {
    return this.toLocalDate().toJavaLocalDate()
}

fun LocalDate.toRelativeString(
    context: Context,
    relative: Boolean = true,
    dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this.toJavaLocalDate())
    }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val difference = this.daysUntil(today)
    return when {
        difference < -7 -> dateFormat.format(this.toJavaLocalDate())
        difference < 0 -> context.pluralStringResource(
            MR.plurals.upcoming_relative_time,
            difference.absoluteValue,
            difference.absoluteValue,
        )
        difference < 1 -> context.stringResource(MR.strings.relative_time_today)
        difference < 7 -> context.pluralStringResource(
            MR.plurals.relative_time,
            difference,
            difference,
        )
        else -> dateFormat.format(this.toJavaLocalDate())
    }
}

// AY -->
// For use in episode release time
fun LocalDateTime.toRelativeString(
    context: Context,
    relative: Boolean = true,
    dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this.toJavaLocalDateTime())
    }

    val timeZone = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(timeZone)

    val difference = now.toInstant(timeZone) - this.toInstant(timeZone)
    val timeDifference = difference.inWholeDays
    val dateDifference = this.date.daysUntil(now.date)

    return when {
        timeDifference < -7 -> dateFormat.format(this.toJavaLocalDateTime())
        timeDifference < 0 -> context.pluralStringResource(
            MR.plurals.upcoming_relative_time,
            dateDifference.absoluteValue,
            dateDifference.absoluteValue,
        )
        timeDifference < 1 -> {
            val hourDifference = difference.inWholeHours
            when {
                hourDifference < 0 -> context.pluralStringResource(
                    AYMR.plurals.upcoming_relative_time_hours,
                    hourDifference.toInt().absoluteValue,
                    hourDifference.toInt().absoluteValue,
                )
                hourDifference < 1 -> {
                    val minuteDifference = difference.inWholeMinutes
                    when {
                        minuteDifference < 0 -> context.pluralStringResource(
                            AYMR.plurals.upcoming_relative_time_minutes,
                            minuteDifference.toInt().absoluteValue,
                            minuteDifference.toInt().absoluteValue,
                        )
                        minuteDifference == 0L -> context.stringResource(AYMR.strings.relative_time_now)
                        else -> context.pluralStringResource(
                            AYMR.plurals.relative_time_minutes,
                            minuteDifference.toInt(),
                            minuteDifference.toInt(),
                        )
                    }
                }
                else -> context.pluralStringResource(
                    AYMR.plurals.relative_time_hours,
                    hourDifference.toInt(),
                    hourDifference.toInt(),
                )
            }
        }
        timeDifference < 7 -> context.pluralStringResource(
            MR.plurals.relative_time,
            dateDifference,
            dateDifference,
        )
        else -> dateFormat.format(this.toJavaLocalDateTime())
    }
}
// <-- AY
