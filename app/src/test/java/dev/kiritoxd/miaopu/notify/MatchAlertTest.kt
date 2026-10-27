package dev.kiritoxd.miaopu.notify

import dev.kiritoxd.miaopu.widget.WidgetMatch
import dev.kiritoxd.miaopu.widget.WidgetTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchAlertTest {
    private val now = 1_790_640_000_000L
    private val hour = 3_600_000L

    @Test fun `only future kickoffs get an alarm`() {
        val input = listOf(
            match("live", "进行中", now - hour),
            match("done", "已结束", now - 2 * hour),
            match("soon", "未开始", now + hour),
        )
        assertEquals(listOf("soon"), upcomingAlertMatches(input, now).map { it.id })
    }

    @Test fun `soonest kickoff comes first and duplicates collapse`() {
        val input = listOf(
            match("later", "未开始", now + 3 * hour),
            match("sooner", "未开始", now + 1 * hour),
            match("sooner", "未开始", now + 1 * hour),
        )
        assertEquals(listOf("sooner", "later"), upcomingAlertMatches(input, now).map { it.id })
    }

    @Test fun `matches outside the window or from unknown esports are skipped`() {
        val input = listOf(
            match("far", "未开始", now + 8 * 24 * hour),
            match("removed", "未开始", now + hour).copy(businessId = "not-a-sport"),
            match("kept", "未开始", now + 2 * hour),
        )
        assertEquals(listOf("kept"), upcomingAlertMatches(input, now).map { it.id })
    }

    @Test fun `limits how many alarms are armed`() {
        val input = (1..20).map { match("next-$it", "未开始", now + it * hour) }
        val scheduled = upcomingAlertMatches(input, now, limit = 5)
        assertEquals(5, scheduled.size)
        assertEquals("next-1", scheduled.first().id)
    }

    @Test fun `empty schedule arms nothing`() {
        assertTrue(upcomingAlertMatches(emptyList(), now).isEmpty())
    }

    private fun match(id: String, status: String, start: Long) = WidgetMatch(
        id, "lol", "T1 vs GEN", "全球总决赛", status, null, start, "20:00",
        listOf(WidgetTeam("T1", null, null), WidgetTeam("GEN", null, null)),
        "lol", "match-$id", id,
    )
}
