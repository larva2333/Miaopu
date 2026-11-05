package dev.kiritoxd.miaopu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamResultsTest {
    private val now = 1_790_640_000_000L
    private val hour = 3_600_000L

    @Test fun `matches by team id and reports the opponent`() {
        val results = recentTeamResults(
            listOf(
                match("a", won = true, start = now - hour),
                match("b", won = false, start = now - 2 * hour),
            ),
            Team("t1", "T1", null, null, false),
        )
        assertEquals(listOf("a", "b"), results.map { it.match.id })
        assertEquals("GEN", results.first().opponentName)
        assertEquals("2 : 1", results.first().scoreLine)
        assertEquals(true, results.first().won)
        assertEquals(false, results.last().won)
    }

    @Test fun `falls back to the team name when the id was lost`() {
        // A widget launch rebuilds the roster with empty ids; the name still identifies the side.
        val team = Team("", "T1", null, null, false)
        val results = recentTeamResults(listOf(match("a", won = true, start = now - hour)), team)
        assertEquals(1, results.size)
        assertEquals("T1", results.first().team?.name)
        assertEquals("GEN", results.first().opponent?.name)
    }

    @Test fun `prefers the id over a name clash`() {
        val results = recentTeamResults(
            listOf(
                match("mine", won = true, start = now - hour, homeId = "t1", homeName = "T1"),
                match("other", won = true, start = now - 2 * hour, homeId = "t9", homeName = "T1"),
            ),
            Team("t1", "T1", null, null, false),
        )
        assertEquals(listOf("mine"), results.map { it.match.id })
    }

    @Test fun `ignores unfinished matches and empty identities`() {
        val upcoming = match("next", won = null, start = now + hour, status = "未开始")
        assertTrue(recentTeamResults(listOf(upcoming), Team("t1", "T1", null, null, false)).isEmpty())
        assertTrue(recentTeamResults(listOf(match("a", true, now - hour)), Team("", "", null, null, false)).isEmpty())
    }

    @Test fun `summary counts only decided matches`() {
        val results = recentTeamResults(
            listOf(match("a", true, now - hour), match("b", false, now - 2 * hour), match("c", null, now - 3 * hour)),
            Team("t1", "T1", null, null, false),
        )
        assertEquals("1 胜 1 负", results.recordSummary())
        assertNull(emptyList<TeamResult>().recordSummary())
    }

    private fun match(
        id: String,
        won: Boolean?,
        start: Long,
        status: String = "已结束",
        homeId: String = "t1",
        homeName: String = "T1",
    ) = MatchSummary(
        id = id,
        esport = Esport.LOL,
        name = "T1 vs GEN",
        introduction = "全球总决赛",
        status = status,
        startTimeMillis = start,
        startTimeLabel = "20:00",
        teams = listOf(
            Team(homeId, homeName, null, if (won == null) null else if (won) "2" else "1", false),
            Team("t2", "GEN", null, if (won == null) null else if (won) "1" else "2", false),
        ),
        scoreCountText = null,
        outBizType = "lol",
        outBizNo = "match-$id",
        featuredPlayer = null,
        uniqueKey = id,
    )
}
