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
import dev.personal.ledger.i18n.tr

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

/**
 * Date formatting in the active language. Vietnamese follows local convention: dd/MM, "Tháng 9", "T7" for Saturday,
 * "CN" for Sunday.
 */
object Fmt {
    private val en = Locale.ENGLISH
    private val vi get() = dev.personal.ledger.i18n.I18n.vi
    private val viWeekShort = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    private val viWeekLong = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")

    fun dayMonth(d: LocalDate): String = if (vi) "%02d/%02d".format(d.dayOfMonth, d.monthValue) else d.format(DateTimeFormatter.ofPattern("dd MMM", en))
    fun monthDay(d: LocalDate): String = if (vi) "%d/%d".format(d.dayOfMonth, d.monthValue) else d.format(DateTimeFormatter.ofPattern("MMM d", en))
    fun weekday(d: LocalDate): String = if (vi) viWeekShort[d.dayOfWeek.value - 1] else d.format(DateTimeFormatter.ofPattern("EEE", en))
    fun month(m: YearMonth): String = if (vi) "Tháng ${m.monthValue}" else m.format(DateTimeFormatter.ofPattern("MMMM", en))
    fun monthShort(m: YearMonth): String = if (vi) "T${m.monthValue}" else m.format(DateTimeFormatter.ofPattern("MMM", en))
    fun monthYear(m: YearMonth): String = if (vi) "Tháng ${m.monthValue}, ${m.year}" else m.format(DateTimeFormatter.ofPattern("MMMM yyyy", en))
    fun time(millis: Long): String = millis.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm", en))
    fun full(d: LocalDate): String = if (vi) "${viWeekLong[d.dayOfWeek.value - 1]}, %02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
        else d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", en))

    /** "Hôm nay" / "Today", "Hôm qua", "Ngày mai", "T7, 26/09". */
    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (daysBetween(today, d)) {
        0L -> tr("Today")
        -1L -> tr("Yesterday")
        1L -> tr("Tomorrow")
        else -> "${weekday(d)}, ${dayMonth(d)}"
    }

    /** "còn 3 ngày" / "in 3 days", "trễ 2 ngày" / "2 days late". */
    fun dueIn(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val n = daysBetween(today, d)
        return when {
            n == 0L -> tr("due today")
            n == 1L -> tr("due tomorrow")
            n > 1 -> tr("in %d days", n)
            n == -1L -> tr("1 day late")
            else -> tr("%d days late", -n)
        }
    }
}
