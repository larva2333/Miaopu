package dev.kiritoxd.miaopu.data

import androidx.compose.runtime.Immutable

/**
 * One finished match seen from a single team's side. Built on the fly from the schedule rows we
 * already hold, so tapping a team logo costs no extra network round trip.
 */
@Immutable
data class TeamResult(val match: MatchSummary, val teamId: String) {
    val team: Team? get() = match.teams.firstOrNull { it.id == teamId }
    val opponent: Team? get() = match.teams.firstOrNull { it.id != teamId }
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
 * Finished matches for [teamId] across [matches], newest first.
 *
 * [now] keeps a kickoff that has not happened yet from being counted as a result when the source
 * has already flagged it terminal.
 */
fun recentTeamResults(
    matches: List<MatchSummary>,
    teamId: String,
    now: Long = System.currentTimeMillis(),
    limit: Int = 12,
): List<TeamResult> {
    if (teamId.isBlank() || limit <= 0) return emptyList()
    return matches.asSequence()
        // Head-to-head rows only: a multi-team event has no single "opponent" to show.
        .filter { match -> match.teams.size == 2 && match.teams.count { it.id == teamId } == 1 }
        .filter { match -> match.isTerminal && (match.startTimeMillis <= 0L || match.startTimeMillis <= now) }
        .distinctBy { it.uniqueKey }
        .sortedByDescending { it.startTimeMillis }
        .take(limit)
        .map { TeamResult(it, teamId) }
        .toList()
}

/** "5 胜 3 负"，只统计能判定的场次。 */
fun List<TeamResult>.recordSummary(): String? {
    val decided = mapNotNull { it.won }
    if (decided.isEmpty()) return null
    val wins = decided.count { it }
    return "${wins} 胜 ${decided.size - wins} 负"
}
