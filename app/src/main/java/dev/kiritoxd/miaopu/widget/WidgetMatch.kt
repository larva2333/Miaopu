package dev.kiritoxd.miaopu.widget

import dev.kiritoxd.miaopu.data.EsportCatalog
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.Team
import dev.kiritoxd.miaopu.data.isLive
import dev.kiritoxd.miaopu.data.isTerminal
import dev.kiritoxd.miaopu.data.relativeDateTimeLabel
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
internal data class WidgetTeam(
    val name: String,
    val logoUrl: String?,
    val score: String?,
    /** Kept so a match opened from the widget can still resolve team records by id. */
    val id: String = "",
)

/** Only public schedule data is persisted; no account or comment data. */
@Serializable
internal data class WidgetMatch(
    val id: String,
    val businessId: String,
    val name: String,
    val competition: String,
    val status: String,
    val statusCode: String?,
    val startsAt: Long,
    val timeLabel: String,
    val teams: List<WidgetTeam>,
    val outBizType: String?,
    val outBizNo: String?,
    val uniqueKey: String,
) {
    val identity: String get() = uniqueKey.takeIf { it.isNotBlank() && it != id } ?: "$businessId:$id"
    val sportLabel: String get() = EsportCatalog.byBusinessId(businessId)?.shortTitle.orEmpty()
    val live: Boolean get() = toModel().isLive
    val terminal: Boolean get() = toModel().isTerminal
    val hasRatings: Boolean get() = !outBizType.isNullOrBlank() && !outBizNo.isNullOrBlank()

    fun toModel() = MatchSummary(
        id = id,
        esport = requireNotNull(EsportCatalog.byBusinessId(businessId)),
        name = name,
        introduction = competition,
        status = status,
        statusCode = statusCode,
        startTimeMillis = startsAt,
        startTimeLabel = timeLabel,
        teams = teams.map { Team(it.id, it.name, it.logoUrl, it.score, false) },
        outBizType = outBizType,
        outBizNo = outBizNo,
        uniqueKey = uniqueKey,
        scoreCountText = null,
        featuredPlayer = null,
    )
}

internal fun MatchSummary.toWidgetMatch() = WidgetMatch(
    id, esport.businessId, name, introduction, status, statusCode, startTimeMillis,
    startTimeLabel, teams.take(2).map { WidgetTeam(it.name, it.logoUrl, it.score, it.id) },
    outBizType, outBizNo, uniqueKey,
)

/** Live first, then upcoming, then recent results. Old seasons never fill an empty card. */
internal fun selectWidgetMatches(
    matches: List<WidgetMatch>, now: Long, limit: Int = 3, historyHoursBack: Long = 48,
): List<WidgetMatch> {
    val hour = 60 * 60 * 1000L
    val candidates = matches.filter {
        EsportCatalog.byBusinessId(it.businessId) != null &&
            it.startsAt <= now + WIDGET_FIXTURE_WINDOW_HOURS * hour &&
            // Results use their own lookback instead of the fixture window: a break in the
            // schedule should not erase the last thing that happened.
            it.startsAt >= now - maxOf(48 * hour, historyHoursBack.coerceAtLeast(0) * hour) &&
            (!it.live || it.startsAt >= now - 12 * hour)
    }.distinctBy(WidgetMatch::identity)
    val live = candidates.filter(WidgetMatch::live).sortedBy(WidgetMatch::startsAt)
    val next = candidates.filter { !it.live && !it.terminal && it.startsAt >= now }
        .sortedBy(WidgetMatch::startsAt)
    val recent = candidates.filter { it.terminal && it.startsAt <= now }.sortedByDescending(WidgetMatch::startsAt)
    return (live + next + recent).take(limit.coerceAtLeast(0))
}

/** Keep both sections before applying a display limit, so upcoming matches cannot crowd out history. */
internal fun selectWidgetScheduleMatches(
    matches: List<WidgetMatch>, now: Long, sectionLimit: Int = 3,
    historyHoursBack: Long = 48,
): List<WidgetMatch> {
    val candidates = selectWidgetMatches(matches, now, Int.MAX_VALUE, historyHoursBack)
    val limit = sectionLimit.coerceAtLeast(0)
    // Preserve the existing priority for the single-match and narrow schedule widgets.
    return candidates.filterNot(WidgetMatch::terminal).take(limit) +
        candidates.filter(WidgetMatch::terminal).take(limit)
}

/**
 * The pool a card picks its rows from: live and upcoming matches first, then the newest results.
 * It is deliberately wider than one card — every widget size derives its own row count from it —
 * and the order is kept as [selectWidgetScheduleMatches] left it, so the single-match widget still
 * opens the next fixture rather than an old result.
 */
internal fun widgetCardPool(
    matches: List<WidgetMatch>,
    now: Long,
    perSection: Int = WidgetScheduleSizing.MAX_SECTION_ROWS,
): List<WidgetMatch> = selectWidgetScheduleMatches(
    matches,
    now,
    perSection,
    // Results use the same month-long lookback as the fixtures, so a gap between rounds does
    // not leave the card with only upcoming rows and nothing to report.
    WIDGET_RESULT_WINDOW_HOURS,
)

/**
 * The rows a card actually paints.
 *
 * The newest [WidgetScheduleSizing.LEAD_RESULTS] results always claim the top rows — the last
 * thing that happened is the reason the card is on the home screen — and the nearest live or
 * upcoming matches fill whatever is left. Only when the fixtures run out (the tail of a round,
 * the gap before a play-off series) do older results back-fill the card instead of leaving it
 * short, so a card with nothing left to play fills up entirely with history.
 *
 * Rows read top to bottom in time order: results oldest-first, then the fixtures.
 */
internal fun widgetVisibleRows(matches: List<WidgetMatch>, rowCount: Int): List<WidgetMatch> {
    val capacity = rowCount.coerceAtLeast(0)
    if (capacity == 0) return emptyList()
    val results = matches.filter(WidgetMatch::terminal).sortedByDescending(WidgetMatch::startsAt)
    val fixtures = matches.filterNot(WidgetMatch::terminal).sortedBy(WidgetMatch::startsAt)
    val lead = results.take(minOf(WidgetScheduleSizing.LEAD_RESULTS, capacity))
    val fixtureSlots = (capacity - lead.size).coerceAtLeast(0)
    val next = fixtures.take(fixtureSlots)
    val filler = results.drop(lead.size).take((fixtureSlots - next.size).coerceAtLeast(0))
    return (lead + filler).sortedBy(WidgetMatch::startsAt) + next
}

/** How far ahead a widget looks for fixtures: a whole month, so a break between rounds or the
 *  gap before a play-off series still surfaces the next block of matches. */
internal const val WIDGET_FIXTURE_WINDOW_HOURS = 30 * 24L

/** How far back a widget looks for results: the same month as the fixtures, so an off-season
 *  gap does not leave the card with nothing to report. */
internal const val WIDGET_RESULT_WINDOW_HOURS = 30 * 24L

internal fun WidgetMatch.scoreLabel(): String = when {
    !live && !terminal -> "VS"
    teams.size == 2 -> "${teams[0].score?.takeIf(String::isNotBlank) ?: "—"} : ${teams[1].score?.takeIf(String::isNotBlank) ?: "—"}"
    else -> status.ifBlank { "暂无比分" }
}

/** 和 App 内其它位置同一套：近几天写「今天 10:00」，再远才写「11月7日 10:00」。 */
internal fun WidgetMatch.dateTimeLabel(now: Long): String {
    if (startsAt <= 0L) return timeLabel.ifBlank { "时间待定" }
    return relativeDateTimeLabel(startsAt, now)
}

/** Calendar days in the device timezone, including DST and month/year boundaries. */
internal fun WidgetMatch.startDayOffset(now: Long): Int {
    fun dayIndex(timestamp: Long): Long {
        val local = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis / 86_400_000L
    }
    return if (startsAt > 0) (dayIndex(startsAt) - dayIndex(now)).toInt() else 0
}

internal fun WidgetMatch.clockLabel(): String = if (startsAt > 0) {
    SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(startsAt))
} else timeLabel.ifBlank { "待定" }
