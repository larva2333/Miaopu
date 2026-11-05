package dev.kiritoxd.miaopu.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 赛程、比赛详情、晋级图共用的口语日期：昨天／今天／明天／后天，再远才写「几月几号」。
 * 按 0 点对齐算自然日差，同一天的早场晚场不会被算成两天。
 */
internal fun relativeDayLabel(millis: Long, now: Long = System.currentTimeMillis()): String =
    when (daysFromToday(millis, now)) {
        -1L -> "昨天"
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(millis))
    }

/** 「今天 06:00」这种一行式标签：口语日期 + 24 小时制时间。 */
internal fun relativeDateTimeLabel(millis: Long, now: Long = System.currentTimeMillis()): String =
    "${relativeDayLabel(millis, now)} ${clockLabel(millis)}"

internal fun clockLabel(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))

/** 「11月7日 周六」：中文月日 + 星期，列表日期带和小组件共用。 */
internal fun monthDayWeekdayLabel(millis: Long): String =
    SimpleDateFormat("M月d日 EEE", Locale.CHINA).format(Date(millis))

/** 「今天 周四」：近几天用口语日期，星期照旧跟上。 */
internal fun relativeDayWeekdayLabel(millis: Long, now: Long = System.currentTimeMillis()): String =
    "${relativeDayLabel(millis, now)} ${SimpleDateFormat("EEE", Locale.CHINA).format(Date(millis))}"

internal fun daysFromToday(millis: Long, now: Long = System.currentTimeMillis()): Long {
    fun startOfDay(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return (startOfDay(millis) - startOfDay(now)) / 86_400_000L
}
