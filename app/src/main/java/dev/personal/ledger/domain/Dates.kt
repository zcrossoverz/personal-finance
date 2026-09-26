package dev.personal.ledger.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
fun LocalDate.atMillis(time: LocalTime): Long = atTime(time).atZone(zone).toInstant().toEpochMilli()
fun YearMonth.startMillis(): Long = atDay(1).startMillis()
fun YearMonth.endMillis(): Long = atEndOfMonth().endMillis()
fun Long.epochDayToDate(): LocalDate = LocalDate.ofEpochDay(this)

fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
val LocalDate.isWeekend get() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/** Same day-of-month in another month, clamped to that month's length. */
fun YearMonth.dayClamped(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))

object Fmt {
    private val dayMonth = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val monthLong = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val full = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun dayMonth(d: LocalDate): String = d.format(dayMonth)
    fun monthDay(d: LocalDate): String = d.format(monthDay)
    fun weekday(d: LocalDate): String = d.format(weekday)
    fun month(m: YearMonth): String = m.format(monthLong)
    fun monthShort(m: YearMonth): String = m.format(monthShort)
    fun monthYear(m: YearMonth): String = m.format(monthYear)
    fun time(millis: Long): String = millis.toLocalDateTime().format(time)
    fun full(d: LocalDate): String = d.format(full)

    /** "Today", "Yesterday", "Tomorrow", "Mon, 28 Sep". */
    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (daysBetween(today, d)) {
        0L -> "Today"
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> "${weekday(d)}, ${dayMonth(d)}"
    }

    /** "in 3 days", "today", "2 days late". */
    fun dueIn(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val n = daysBetween(today, d)
        return when {
            n == 0L -> "today"
            n == 1L -> "tomorrow"
            n > 1 -> "in $n days"
            n == -1L -> "1 day late"
            else -> "${-n} days late"
        }
    }
}
