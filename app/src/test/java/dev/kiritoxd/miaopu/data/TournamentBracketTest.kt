package dev.kiritoxd.miaopu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentBracketTest {
    private val day = 86_400_000L
    private val base = 1_793_000_000_000L

    @Test fun `seven matches become a wired quarterfinal semifinal final tree`() {
        val bracket = buildTournamentBracket(sevenFinished(), "全球总决赛淘汰赛", focusKey = "7")!!
        assertEquals(BracketKind.KNOCKOUT, bracket.kind)
        assertEquals(listOf("四分之一决赛", "半决赛", "决赛"), bracket.columns.map { it.title })
        assertEquals(7, bracket.nodes.size)
        assertEquals(6, bracket.wires.size)
        assertEquals("BLG", bracket.champion?.name)
        assertEquals(1, bracket.nodes.count { it.match.isFocus })
        // 轮次标在每场自己头上，而不是列头：卡片按 column 排序后应逐场带出轮次名。
        assertEquals(
            listOf(
                "四分之一决赛", "四分之一决赛", "四分之一决赛", "四分之一决赛",
                "半决赛", "半决赛", "决赛",
            ),
            bracket.nodes.sortedBy { it.column }.map { it.title },
        )
        assertTrue(bracket.nodes.all { it.time.contains(":") })
    }

    @Test fun `the winners bracket slot is filled as soon as the previous round ends`() {
        // 现实里八强只打完了 TES/IG 一场，半决赛两格都还写着“待定”。官方晋级图会把已经出线的
        // IG 直接放进下一轮，对手位置留着待定，而不是整场空着等赛程更新。
        val bracket = buildTournamentBracket(liveQuarterfinals(), "全球总决赛淘汰赛", focusKey = "1")!!
        val semifinal = bracket.nodes.filter { it.column == 1 }.map { it.match }
        assertEquals("IG", semifinal.first().top.name)
        assertTrue(semifinal.first().top.projected)
        assertTrue(semifinal.first().bottom.pending)
        // 另一场半决赛的两边都还没打出来，保持待定。
        assertTrue(semifinal[1].top.pending)
        assertTrue(semifinal[1].bottom.pending)
        assertNull(bracket.champion)
    }

    @Test fun `next round order drives the feeder order not the calendar order`() {
        // 日历顺序是 TES/IG、GEN/LYON、DK/BLG、T1/KC，半决赛却是 IG 与 BLG、GEN 与 T1：
        // 不按晋级关系重排，连线就会指到错误的上一轮。
        val bracket = buildTournamentBracket(sevenFinished(), "全球总决赛淘汰赛", focusKey = "1")!!
        val quarters = bracket.nodes.filter { it.column == 0 }.map { it.match.match.id }
        assertEquals(listOf("1", "3", "2", "4"), quarters)
    }

    @Test fun `three matches still draw a two round tree with unknown sides`() {
        val rows = listOf(
            match("1", "TES", 1, "IG", 3, base),
            match("2", "待定", null, "待定", null, base + day, status = "未开始"),
            match("3", "待定", null, "待定", null, base + 2 * day, status = "未开始"),
        )
        val bracket = buildTournamentBracket(rows, "德玛西亚杯淘汰赛", focusKey = "1")!!
        assertEquals(listOf("半决赛", "决赛"), bracket.columns.map { it.title })
        val final = bracket.nodes.single { it.column == 1 }.match
        // 半决赛的胜者已经出来了，决赛这一格就能提前填。
        assertEquals("IG", final.top.name)
        assertTrue(final.bottom.pending)
        assertNull(bracket.champion)
    }

    @Test fun `four teams and six matches are drawn as a double elimination`() {
        // 入围赛真实形态：胜者组首轮 2 场、胜者组决赛 1 场、败者组两轮各 1 场、决赛 1 场。
        val rows = listOf(
            match("1", "C9", 2, "KC", 3, base),
            match("2", "FUR", 0, "MVK", 3, base),
            match("3", "KC", 3, "MVK", 0, base + day),
            match("4", "C9", 1, "FUR", 3, base + day),
            match("5", "MVK", 3, "FUR", 1, base + 2 * day),
            match("6", "KC", 3, "MVK", 0, base + 3 * day),
        )
        val bracket = buildTournamentBracket(rows, "全球总决赛入围赛", focusKey = "6")!!
        assertEquals(BracketKind.DOUBLE_ELIM, bracket.kind)
        // 官方入围赛只画三列，每列自上而下装 3 / 2 / 1 场，收成一个三角。
        assertEquals(
            listOf("胜者组首轮", "胜者组决赛", "总决赛"),
            bracket.columns.map { it.title },
        )
        assertEquals(2, bracket.nodes.maxOf { it.column })
        assertEquals(6, bracket.nodes.size)
        // 败者组首轮直接落在第一列（两场胜者组首轮的败者就地掉下来），第二列是两场决赛。
        assertEquals(
            listOf("胜者组首轮", "胜者组首轮", "败者组首轮", "胜者组决赛", "败者组决赛", "总决赛"),
            bracket.nodes
                .sortedWith(compareBy({ it.column }, { it.row }))
                .map { it.title },
        )
        assertEquals(3, bracket.nodes.count { it.column == 0 })
        assertEquals(2, bracket.nodes.count { it.column == 1 })
        assertEquals(1, bracket.nodes.count { it.column == 2 })
        // 双败只留晋级实线，败者下落的虚线通道不再画。
        assertEquals(0, bracket.wires.count { it.dashed })
        assertEquals(0, bracket.wires.count { it.vertical })
        assertTrue(bracket.wires.all { it.toColumn >= it.fromColumn })
        assertEquals("KC", bracket.champion?.name)
        val final = bracket.nodes.single { it.title == "总决赛" }.match
        assertEquals("KC", final.top.name)
        assertEquals("MVK", final.bottom.name)
    }

    @Test fun `six matches among eight teams converge into a triangle when the final is unplayed`() {
        // 德玛西亚杯淘汰赛的真实形态：八强四场、半决赛两场，决赛还没排期。
        val rows = listOf(
            match("1", "KT", 3, "VIT", 2, base),
            match("2", "BFX", 3, "JDG", 2, base),
            match("3", "WE", 3, "FLY", 2, base + day),
            match("4", "BRO", 3, "LGD", 1, base + day),
            match("5", "KT", 1, "BFX", 3, base + 2 * day),
            match("6", "WE", 3, "BRO", 2, base + 3 * day),
        )
        val bracket = buildTournamentBracket(rows, "德玛西亚杯淘汰赛", focusKey = "5")!!
        assertEquals(BracketKind.KNOCKOUT, bracket.kind)
        // 不再按比赛日分成四列，而是按“谁把谁打上来”收成两列。
        // 决赛还没排期，第二列未必就是半决赛：形状是推出来的，轮次只敢写第几轮。
        assertEquals(listOf("第 1 轮", "第 2 轮"), bracket.columns.map { it.title })
        assertEquals(
            listOf(0f, 1f, 2f, 3f),
            bracket.nodes.filter { it.column == 0 }.sortedBy { it.row }.map { it.row },
        )
        // 半决赛落在各自上游两场的重心上，正好是两个三角形。
        assertEquals(
            listOf(0.5f, 2.5f),
            bracket.nodes.filter { it.column == 1 }.sortedBy { it.row }.map { it.row },
        )
        assertEquals(4, bracket.wires.size)
        assertTrue(bracket.wires.all { it.fromColumn == 0 && it.toColumn == 1 })
        assertEquals("5", bracket.nodes.single { it.match.isFocus }.match.match.id)
        // 决赛还没打，冠军自然是空的。
        assertNull(bracket.champion)
    }

    @Test fun `a play in ladder still wires every step`() {
        // LCK 那种冒泡赛：赢一场往上爬一级，不会收成标准对称树，但每一步都要连上。
        val rows = listOf(
            match("1", "KT", 1, "DK", 3, base),
            match("2", "GEN", 3, "HLE", 1, base),
            match("3", "T1", 3, "DK", 1, base + day),
            match("4", "HLE", 3, "T1", 1, base + 2 * day),
            match("5", "GEN", 3, "HLE", 1, base + 3 * day),
        )
        val bracket = buildTournamentBracket(rows, "LCK联赛淘汰赛", focusKey = "5")!!
        assertEquals(BracketKind.KNOCKOUT, bracket.kind)
        assertEquals(4, bracket.columns.size)
        // 四条晋级线，外加 GEN 从首轮轮空直通决赛的那一条——轮空也是真的来路，不能丢。
        assertEquals(5, bracket.wires.size)
        assertTrue(bracket.wires.any { it.toColumn - it.fromColumn == 3 })
        // 阶梯图必须落在整数行：0 / 0.5 / 0.75 这种小数行会让卡片和头顶标注
        // 错开不到一个行高，整张图看着是歪的。
        assertTrue(bracket.nodes.all { it.row >= 0f && it.row == it.row.toInt().toFloat() })
        assertEquals(listOf(0f, 1f), bracket.nodes.filter { it.column == 0 }.sortedBy { it.row }.map { it.row })
        // 主链四场同处下方通道，支线占上方通道。
        assertTrue(bracket.nodes.filter { it.column > 0 }.all { it.row == 1f })
        // 跨列长线一律贴目标列下折，否则水平段会穿过中间列的卡片。
        assertEquals(2, bracket.wires.count { it.toColumn - it.fromColumn >= 2 })
        assertTrue(bracket.wires.filter { it.toColumn - it.fromColumn >= 2 }.all { it.endElbow })
        // 冒泡赛不是对称树：第一列两场也不能叫「半决赛」，只有收尾那场才是决赛。
        assertEquals(
            listOf("第 1 轮", "第 2 轮", "第 3 轮", "决赛"),
            bracket.columns.map { it.title },
        )
        // 最右边那一列只有决赛一场，所以它就是冠军。
        assertEquals("GEN", bracket.champion?.name)
    }

    @Test fun `double elimination with repeats falls back to a four per row schedule grid`() {
        // LPL 季后赛是双败：同一支队伍反复出现。旧版按比赛日摊成一整排窄卡片，
        // 阶梯布点又会把 9 场撒成 5 列 5 行的散点，两种都不可读，索性只按时间列网格。
        val rows = listOf(
            match("1", "AL", 3, "LGD", 0, base),
            match("2", "TES", 1, "IG", 3, base + day),
            match("3", "JDG", 1, "NIP", 3, base + day),
            match("4", "IG", 3, "WE", 0, base + 2 * day),
            match("5", "NIP", 1, "LGD", 3, base + 2 * day),
            match("6", "AL", 1, "BLG", 3, base + 3 * day),
            match("7", "LGD", 1, "IG", 3, base + 4 * day),
            match("8", "AL", 3, "IG", 1, base + 8 * day),
            match("9", "BLG", 1, "AL", 3, base + 9 * day),
        )
        val bracket = buildTournamentBracket(rows, "LPL2026赛季季后赛", focusKey = "9")!!
        assertEquals(BracketKind.GRID, bracket.kind)
        assertEquals(9, bracket.matchCount)
        assertEquals(9, bracket.cards.size)
        // 网格不假装画出了晋级关系：没有列、没有节点、没有连线，也不认冠军。
        assertTrue(bracket.columns.isEmpty())
        assertTrue(bracket.nodes.isEmpty())
        assertTrue(bracket.wires.isEmpty())
        assertNull(bracket.champion)
        // 按开赛时间排序、每排四张一路往下：9 场 = 4 + 4 + 1。
        assertEquals((1..9).map { it.toString() }, bracket.cards.map { it.match.match.id })
        assertEquals(listOf(4, 4, 1), bracket.cards.chunked(BracketCardsPerRow).map { it.size })
        assertEquals("9", bracket.cards.single { it.match.isFocus }.match.match.id)
        assertTrue(bracket.cards.all { it.title.isNotBlank() && it.time.contains(":") })
    }

    @Test fun `a double elimination that fits in four columns still is not drawn as a ladder`() {
        // 少一场就躲过「五列」判据，但第二列仍挤着三场：这不是阶梯赛，
        // 硬按两条通道摆会把卡片撒成散点，照样退回每排四张的网格。
        val rows = listOf(
            match("1", "AL", 3, "LGD", 0, base),
            match("2", "TES", 1, "IG", 3, base + day),
            match("3", "JDG", 1, "NIP", 3, base + day),
            match("4", "IG", 3, "WE", 0, base + 2 * day),
            match("5", "NIP", 1, "LGD", 3, base + 2 * day),
            match("6", "AL", 1, "BLG", 3, base + 3 * day),
            match("7", "LGD", 1, "IG", 3, base + 4 * day),
            match("8", "AL", 3, "IG", 1, base + 8 * day),
        )
        val bracket = buildTournamentBracket(rows, "LPL2026赛季季后赛", focusKey = "8")!!
        assertEquals(BracketKind.GRID, bracket.kind)
        assertEquals(8, bracket.cards.size)
        assertEquals(listOf(4, 4), bracket.cards.chunked(BracketCardsPerRow).map { it.size })
    }

    @Test fun `a crowded knockout day is listed as a grid instead of hiding the bracket`() {
        // 旧版按比赛日分列，一天超过四场就直接返回 null（晋级图入口都不出现）。
        // 现在照样列成每排四张的网格，首轮超过四场也走这条兜底。
        val rows = (1..6).map { index ->
            match("$index", "A$index", 3, "B$index", 1, base)
        }
        val bracket = buildTournamentBracket(rows, "全球总决赛淘汰赛", focusKey = "1")!!
        assertEquals(BracketKind.GRID, bracket.kind)
        assertEquals(6, bracket.cards.size)
        assertEquals(listOf(4, 2), bracket.cards.chunked(BracketCardsPerRow).map { it.size })
    }

    @Test fun `a non knockout stage never gets a made up schedule grid`() {
        val rows = listOf(
            match("1", "A", 3, "B", 1, base),
            match("2", "C", 3, "D", 1, base + day),
        )
        assertNull(buildTournamentBracket(rows, "表演赛全明星赛况", "1"))
    }

    @Test fun `a final filed under its own decider day still completes the bracket`() {
        val pool = demaciaPool()
        val knockoutFirst = pool.first { it.introduction == "德玛西亚杯淘汰赛" }
        // 小组赛和淘汰赛同属德玛西亚杯，但绝不能被并进来。
        val collected = tournamentPool(pool, knockoutFirst)
        assertEquals(7, collected.size)
        assertTrue(collected.none { it.introduction == "德玛西亚杯小组赛" })

        val bracket = buildTournamentBracket(collected, "德玛西亚杯淘汰赛", focusKey = "7")!!
        assertEquals(listOf("四分之一决赛", "半决赛", "决赛"), bracket.columns.map { it.title })
        assertEquals(6, bracket.wires.size)
        assertEquals("BFX", bracket.champion?.name)
    }

    @Test fun `opening the decider day still shows the whole stage`() {
        val pool = demaciaPool()
        val decider = pool.first { it.introduction == "德玛西亚杯对决日" }
        val collected = tournamentPool(pool, decider)
        assertEquals(7, collected.size)
        // 从决赛那场点进来，主分组要换成场次最多的淘汰赛分组。
        assertEquals(6, collected.count { it.introduction == "德玛西亚杯淘汰赛" })
        assertTrue(collected.any { it.uniqueKey == decider.uniqueKey })
    }

    @Test fun `stage suffixes fold into one competition name`() {
        assertEquals("德玛西亚杯", competitionFamily("德玛西亚杯淘汰赛"))
        assertEquals("德玛西亚杯", competitionFamily("德玛西亚杯对决日"))
        assertEquals("全球总决赛", competitionFamily("全球总决赛入围赛"))
        assertEquals("LPL2026赛季", competitionFamily("LPL2026赛季季后赛"))
        // 认不出阶段后缀时原样返回，不会把名字吃掉。
        assertEquals("表演赛全明星赛况", competitionFamily("表演赛全明星赛况"))
    }

    private fun demaciaPool(): List<MatchSummary> = listOf(
        match("1", "KT", 3, "VIT", 2, base, introduction = "德玛西亚杯淘汰赛"),
        match("2", "BFX", 3, "JDG", 2, base, introduction = "德玛西亚杯淘汰赛"),
        match("3", "WE", 3, "FLY", 2, base + day, introduction = "德玛西亚杯淘汰赛"),
        match("4", "BRO", 3, "LGD", 1, base + day, introduction = "德玛西亚杯淘汰赛"),
        match("5", "KT", 1, "BFX", 3, base + 2 * day, introduction = "德玛西亚杯淘汰赛"),
        match("6", "WE", 3, "BRO", 2, base + 3 * day, introduction = "德玛西亚杯淘汰赛"),
        match("7", "BFX", 3, "WE", 1, base + 5 * day, introduction = "德玛西亚杯对决日"),
        match("g1", "KT", 3, "VIT", 0, base - day, introduction = "德玛西亚杯小组赛"),
        match("g2", "WE", 3, "FLY", 0, base - 2 * day, introduction = "德玛西亚杯小组赛"),
    )

    @Test fun `swiss stage shows rounds and who advanced`() {
        val rows = listOf(
            match("1", "A", 1, "B", 0, base),
            match("2", "C", 1, "D", 0, base),
            match("3", "A", 1, "C", 0, base + day),
            match("4", "B", 1, "D", 0, base + day),
            match("5", "A", 1, "B", 0, base + 2 * day),
            match("6", "C", 1, "D", 0, base + 2 * day),
        )
        val bracket = buildTournamentBracket(rows, "全球总决赛瑞士轮", focusKey = "1")!!
        assertEquals(BracketKind.SWISS, bracket.kind)
        // 每队每轮只打一场：轮次 = 出场次数 + 1。
        assertEquals(listOf(1, 2, 3), bracket.swissRounds.map { it.index })
        assertEquals(listOf(2, 2, 2), bracket.swissRounds.map { it.matches.size })
        assertEquals(listOf("A"), bracket.standings.filter { it.advanced }.map { it.name })
        assertEquals(listOf("D"), bracket.standings.filter { it.eliminated }.map { it.name })
        val c = bracket.standings.single { it.name == "C" }
        assertEquals(2, c.wins)
        assertEquals(1, c.losses)
        // 瑞士轮的胜者不是“晋级”，不能像淘汰赛那样标出冠军。
        assertNull(bracket.champion)
    }

    @Test fun `group stage is summarised as a table like the swiss stage`() {
        val rows = listOf(
            match("g1", "RED", 3, "NAVI", 0, base, introduction = "德玛西亚杯小组赛"),
            match("g2", "LGD", 3, "FLY", 0, base + day, introduction = "德玛西亚杯小组赛"),
            match("g3", "RED", 0, "LGD", 3, base + 2 * day, introduction = "德玛西亚杯小组赛"),
        )
        val bracket = buildTournamentBracket(rows, "德玛西亚杯小组赛", "g1", setOf("LGD"))!!
        assertEquals(BracketKind.GROUP, bracket.kind)
        assertEquals(3, bracket.matchCount)
        // 小组赛按比赛日分批，不要套用瑞士轮那套“每队每轮一场”。
        assertEquals(3, bracket.swissRounds.size)
        assertEquals(listOf("LGD"), bracket.standings.filter { it.advanced }.map { it.name })
        assertEquals(setOf("RED", "NAVI", "FLY"), bracket.standings.filter { it.eliminated }.map { it.name }.toSet())
        assertEquals("LGD", bracket.standings.first().name)
        assertEquals(2, bracket.standings.first().wins)
        assertNull(bracket.champion)
    }

    @Test fun `a group table without a next round simply ranks the records`() {
        val rows = listOf(
            match("g1", "RED", 3, "NAVI", 0, base, introduction = "德玛西亚杯小组赛"),
            match("g2", "LGD", 3, "FLY", 0, base + day, introduction = "德玛西亚杯小组赛"),
        )
        val bracket = buildTournamentBracket(rows, "德玛西亚杯小组赛", "g1")!!
        assertEquals(BracketKind.GROUP, bracket.kind)
        // 没有后续阶段的名单就不下淘汰结论，宁可只排战绩。
        assertTrue(bracket.standings.none { it.advanced || it.eliminated })
        assertEquals(listOf("RED", "LGD", "NAVI", "FLY").sorted(), bracket.standings.map { it.name }.sorted())
    }

    @Test fun `advancing teams are the ones that show up in a later stage`() {
        val pool = listOf(
            match("g1", "RED", 3, "NAVI", 0, base, introduction = "德玛西亚杯小组赛"),
            match("g2", "LGD", 3, "FLY", 0, base + day, introduction = "德玛西亚杯小组赛"),
            match("q1", "RED", 3, "WE", 0, base + 10 * day, introduction = "德玛西亚杯淘汰赛"),
            match("q2", "LGD", 1, "KT", 3, base + 11 * day, introduction = "德玛西亚杯淘汰赛"),
            // 上一届的场次时间更早，不该被当成这一届的下一阶段。
            match("old", "JDG", 3, "BRO", 0, base - 30 * day, introduction = "德玛西亚杯淘汰赛"),
        )
        assertEquals(setOf("RED", "WE", "LGD", "KT"), advancingTeams(pool, "德玛西亚杯小组赛"))
        // 反过来问淘汰赛的“下一阶段”，赛程里没有更晚的阶段，返回空集。
        assertTrue(advancingTeams(pool, "德玛西亚杯淘汰赛").isEmpty())
    }

    @Test fun `a single match cannot be drawn as a bracket`() {
        assertNull(buildTournamentBracket(listOf(match("1", "T1", 3, "GEN", 1, base)), "LPL", "1"))
        assertNull(buildTournamentBracket(emptyList(), "LPL", "1"))
    }

    /** 完整打完的七场，用来验证树形、连线、冠军。 */
    private fun sevenFinished(): List<MatchSummary> = listOf(
        match("1", "TES", 1, "IG", 3, base),
        match("2", "GEN", 3, "LYON", 0, base + day),
        match("3", "DK", 1, "BLG", 3, base + 2 * day),
        match("4", "T1", 3, "KC", 1, base + 3 * day),
        match("5", "IG", 2, "BLG", 3, base + 4 * day),
        match("6", "GEN", 3, "T1", 2, base + 5 * day),
        match("7", "BLG", 3, "GEN", 1, base + 6 * day),
    )

    /** 八强只打完了第一场，半决赛和决赛都还是“待定”。 */
    private fun liveQuarterfinals(): List<MatchSummary> = listOf(
        match("1", "TES", 1, "IG", 3, base),
        match("2", "GEN", null, "LYON", null, base + day, status = "未开始"),
        match("3", "DK", null, "BLG", null, base + 2 * day, status = "未开始"),
        match("4", "T1", null, "KC", null, base + 3 * day, status = "未开始"),
        match("5", "待定", null, "待定", null, base + 4 * day, status = "未开始"),
        match("6", "待定", null, "待定", null, base + 5 * day, status = "未开始"),
        match("7", "待定", null, "待定", null, base + 6 * day, status = "未开始"),
    )

    private fun match(
        id: String,
        homeName: String,
        homeScore: Int?,
        awayName: String,
        awayScore: Int?,
        start: Long,
        status: String = "已结束",
        introduction: String = "全球总决赛淘汰赛",
    ): MatchSummary {
        val homeWon = homeScore != null && awayScore != null && homeScore > awayScore
        val awayWon = homeScore != null && awayScore != null && awayScore > homeScore
        return MatchSummary(
            id = id,
            esport = Esport.LOL,
            name = "$homeName vs $awayName",
            introduction = introduction,
            status = status,
            startTimeMillis = start,
            startTimeLabel = "20:00",
            teams = listOf(
                Team("h$id", homeName, logoUrl = null, score = homeScore?.toString(), winner = homeWon),
                Team("a$id", awayName, logoUrl = null, score = awayScore?.toString(), winner = awayWon),
            ),
            scoreCountText = null,
            outBizType = "lol_match",
            outBizNo = id,
            featuredPlayer = null,
            uniqueKey = id,
        )
    }
}
