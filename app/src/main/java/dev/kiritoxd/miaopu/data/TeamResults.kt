package dev.kiritoxd.miaopu.data

import androidx.compose.runtime.Immutable

/**
 * One finished match seen from a single team's side. Built on the fly from the schedule rows we
 * already hold, so tapping a team logo costs no extra network round trip.
 */
@Immutable
data class TeamResult(
    val match: MatchSummary,
    val teamId: String,
    /** Kept alongside the id: a widget or notification launch rebuilds teams without one. */
    val teamName: String = "",
) {
    val team: Team?
        get() = match.teams.firstOrNull { teamId.isNotBlank() && it.id == teamId }
            ?: match.teams.firstOrNull { teamName.isNotBlank() && it.name.trim().equals(teamName.trim(), true) }
    val opponent: Team? get() = team?.let { me -> match.teams.firstOrNull { it !== me } }
    val opponentName: String get() = opponent?.name ?: "待定"
    val scoreFor: String? get() = team?.score?.takeIf(String::isNotBlank)
    val scoreAgainst: String? get() = opponent?.score?.takeIf(String::isNotBlank)

    /** Null when the source never marked a winner and both sides share a score. */
    val won: Boolean?
        get() {
            val me = team ?: return null
            val rival = opponent ?: return null
            if (me.winner) return true
            if (rival.winner) return false
            val mine = me.score?.trim()?.toIntOrNull() ?: return null
            val theirs = rival.score?.trim()?.toIntOrNull() ?: return null
            return when {
                mine > theirs -> true
                mine < theirs -> false
                else -> null
            }
        }

    /** "3 : 1", or null when the score column is still empty. */
    val scoreLine: String?
        get() {
            val mine = scoreFor ?: return null
            val theirs = scoreAgainst ?: "—"
            return "$mine : $theirs"
        }
}

/**
 * Finished matches for [team] across [matches], newest first.
 *
 * [now] keeps a kickoff that has not happened yet from being counted as a result when the source
 * has already flagged it terminal.
 *
 * Matching uses the team id first. Rows that lost their id — a widget or notification launch
 * rebuilds the roster from a trimmed cache — fall back to the display name, otherwise the sheet
 * would claim the team never played a match.
 */
fun recentTeamResults(
    matches: List<MatchSummary>,
    team: Team,
    now: Long = System.currentTimeMillis(),
    limit: Int = 12,
): List<TeamResult> {
    val id = team.id.trim()
    val name = team.name.trim()
    if (limit <= 0 || (id.isEmpty() && name.isEmpty())) return emptyList()
    val headToHead = matches.asSequence()
        // Head-to-head rows only: a multi-team event has no single "opponent" to show.
        .filter { match -> match.teams.size == 2 }
        .filter { match -> match.isTerminal && (match.startTimeMillis <= 0L || match.startTimeMillis <= now) }
        .distinctBy { it.uniqueKey }
        .sortedByDescending { it.startTimeMillis }
        .toList()
    val byId = if (id.isEmpty()) emptyList() else
        headToHead.filter { match -> match.teams.count { it.id.trim() == id } == 1 }
    val pool = byId.ifEmpty {
        if (name.isEmpty()) emptyList()
        else headToHead.filter { match -> match.teams.count { it.name.trim().equals(name, true) } == 1 }
    }
    return pool.take(limit).map { TeamResult(it, id, name) }
}

/** "5 胜 3 负"，只统计能判定的场次。 */
fun List<TeamResult>.recordSummary(): String? {
    val decided = mapNotNull { it.won }
    if (decided.isEmpty()) return null
    val wins = decided.count { it }
    return "${wins} 胜 ${decided.size - wins} 负"
}
