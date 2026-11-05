package dev.kiritoxd.miaopu.data

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 赛事的几种形态：单败淘汰树、双败（入围赛那种）、瑞士轮、循环赛、赛程网格。 */
/** [GROUP] 是小组赛/入围赛这类循环赛：没有晋级路径，只能按战绩汇总。 */
/** [GRID] 是晋级关系推不出来时的赛程网格：不画连线，按时间每排 4 张往下排。 */
enum class BracketKind { KNOCKOUT, DOUBLE_ELIM, SWISS, GROUP, GRID }

/** 和瑞士轮/小组赛一排 4 张对齐：形状推不出来的淘汰赛也用这个列数往下排。 */
const val BracketCardsPerRow = 4

/**
 * 赛事的一个参赛方。
 *
 * [score] 是该队在这一轮拿到的大比分，未开打时为 null；[pending] 表示这一格还没产生
 * （赛程里写成“待定”），只占位不参与胜负判断。[projected] 表示赛程里虽然写着“待定”，
 * 但上一轮已经打完、胜者确定，于是提前把晋级队填了进来。
 */
@Immutable
data class BracketSide(
    val name: String,
    val logoUrl: String?,
    val score: String?,
    val isWinner: Boolean,
    val pending: Boolean,
    val projected: Boolean = false,
)

@Immutable
data class BracketMatch(
    val match: MatchSummary,
    val top: BracketSide,
    val bottom: BracketSide,
    /** 打开晋级图的那一场，画面上高亮它。 */
    val isFocus: Boolean,
)

@Immutable
data class BracketColumn(val title: String, val caption: String)

/** 推不出晋级关系时的一张纯赛程卡片：只有日期/时间标注，不参与任何连线。 */
@Immutable
data class BracketCard(
    val match: BracketMatch,
    val title: String,
    val time: String,
)

/**
 * 一张对阵卡片在网格里的位置：第 [column] 列，纵向落在第 [row] 个行高上（可以是小数）。
 *
 * [title] 和 [time] 显示在这张卡片的正上方，跟官方晋级图一样每场单独标注：
 * 列标题放顶部时，同一列里可能混着两轮（入围赛的“胜者组决赛”和“败者组首轮”同列），
 * 放不到每场头上就分不清哪场是哪轮。
 */
@Immutable
data class BracketNode(
    val match: BracketMatch,
    val column: Int,
    val row: Float,
    val title: String,
    val time: String,
)

/** 网格里的一条连线。[dashed] 表示败者掉进败者组这类“被淘汰下去”的关系。 */
@Immutable
data class BracketWire(
    val fromColumn: Int,
    val fromRow: Float,
    val toColumn: Int,
    val toRow: Float,
    val dashed: Boolean = false,
    /** 同一列内上下衔接（败者往下掉），画在卡片外侧而不是横跨两列。 */
    val vertical: Boolean = false,
    /** 竖线走卡片左侧：第一列左边没有别的列，走左侧才不会和晋级折线挤在一起。 */
    val left: Boolean = false,
    /**
     * 轮空队的跨列长线：折角贴到目标列左沿，水平段全程走空着的上半区。
     * 默认取两列中点时，后半段水平线会正好穿过中间列下半区的卡片。
     */
    val endElbow: Boolean = false,
)

/** 瑞士轮的一轮：同一轮里每队各打一场。 */
@Immutable
data class SwissRound(val index: Int, val caption: String, val matches: List<BracketMatch>)

/** 瑞士轮战绩。先拿到 3 胜晋级，先吃到 3 负淘汰。 */
@Immutable
data class SwissStanding(
    val name: String,
    val logoUrl: String?,
    val wins: Int,
    val losses: Int,
    val advanced: Boolean,
    val eliminated: Boolean,
)

@Immutable
data class TournamentBracket(
    val title: String,
    val kind: BracketKind,
    val columns: List<BracketColumn> = emptyList(),
    val nodes: List<BracketNode> = emptyList(),
    val wires: List<BracketWire> = emptyList(),
    val swissRounds: List<SwissRound> = emptyList(),
    val standings: List<SwissStanding> = emptyList(),
    val cards: List<BracketCard> = emptyList(),
    val champion: BracketSide? = null,
) {
    val matchCount: Int
        get() = when (kind) {
            BracketKind.SWISS, BracketKind.GROUP -> swissRounds.sumOf { it.matches.size }
            BracketKind.GRID -> cards.size
            else -> nodes.size
        }
}

/** 只有这两种场次数（外加队伍都是 2 的幂）能拼成干净的 2^k-1 单败树：3 = 2+1，7 = 4+2+1。 */
private val BracketTreeSizes = setOf(3, 7)

/**
 * 场次凑不满树时只能按比赛日分组，那条路只对淘汰赛有意义：小组赛、瑞士轮同一天动辄五六场，
 * 画出来既不是晋级路径也塞不进一屏。
 */
private val KnockoutTitlePattern = Regex("淘汰|季后赛|季後賽|资格|資格|决赛|決賽|playoff", RegexOption.IGNORE_CASE)

private val SwissTitlePattern = Regex("瑞士|swiss", RegexOption.IGNORE_CASE)

/**
 * 小组赛、入围赛这类循环赛：官方给的是积分表而不是对阵树，画不出连线的晋级图，
 * 但同样能像瑞士轮那样把整段战绩汇总成一屏。
 */
private val GroupTitlePattern = Regex("小组|入围|积分|循环|group", RegexOption.IGNORE_CASE)

/** 阶段后缀：「德玛西亚杯淘汰赛」「德玛西亚杯对决日」都属于「德玛西亚杯」。 */
private val CompetitionStageSuffix = Regex(
    // 只去一层后缀：「全球总决赛入围赛」去掉的是「入围赛」，不是连着把「决赛」也吃掉。
    "(?:淘汰赛|小组赛|入围赛|瑞士轮|季后赛|季後賽|资格赛|資格賽|冒泡赛|对决日|决赛日|决赛圈|决赛)$",
)

internal fun competitionFamily(introduction: String): String {
    val name = introduction.trim()
    return name.replace(CompetitionStageSuffix, "").trim().ifEmpty { name }
}

/** 单独另起的「决赛 / 对决日」分组：它往往就是淘汰赛缺的那个顶点。 */
internal fun isDeciderStage(introduction: String): Boolean {
    val name = introduction.trim()
    return name.contains("决") || name.contains("冠军")
}

/**
 * 同一项赛事的全部对局。
 *
 * 虎扑把「淘汰赛」和「对决日」拆成两个分组，决赛常常单独挂在后者里（德玛西亚杯就是这样：
 * 八强四场、半决赛两场在「淘汰赛」，决赛那场在「对决日」）。只按 introduction 精确匹配会
 * 把决赛丢掉，晋级图上就少一场、也就连不成三角。这里按赛事名归组，把另起的决赛分组并进来；
 * 小组赛、入围赛不会被误并——它们的标题里没有「决」字。
 *
 * @param pool 同一电竞项目的全部已加载对局。
 */
internal fun tournamentPool(pool: List<MatchSummary>, match: MatchSummary): List<MatchSummary> {
    val introduction = match.introduction.trim()
    if (introduction.isEmpty()) return listOf(match)
    val family = competitionFamily(introduction)
    val stages = pool.map { it.introduction.trim() }.filter(String::isNotEmpty).distinct()
    // 从「对决日」那场点进来时，主分组换成同赛事里真正的那个阶段（通常是场次最多的淘汰赛）。
    val primary = if (isDeciderStage(introduction)) {
        stages.filter { it != introduction && competitionFamily(it) == family && !isDeciderStage(it) }
            .maxByOrNull { stage -> pool.count { it.introduction.trim() == stage } }
            ?: introduction
    } else {
        introduction
    }
    val primaryMatches = pool.filter { it.introduction.trim() == primary }
    val participants = primaryMatches.flatMap { it.teams }.map { it.name.trim() }.toSet()
    val deciders = if (isDeciderStage(introduction) && primary == introduction) {
        // 自己就是那个决赛分组，又没有别的阶段可挂，单场照画。
        emptyList()
    } else {
        pool.filter {
            val stage = it.introduction.trim()
            stage != primary &&
                competitionFamily(stage) == family &&
                isDeciderStage(stage) &&
                participants.isNotEmpty() &&
                it.teams.isNotEmpty() &&
                it.teams.all { team -> team.name.trim() in participants }
        }
    }
    return (primaryMatches + deciders + match).distinctBy { it.uniqueKey }
}

private fun isPendingName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isEmpty() ||
        trimmed == "待定" ||
        trimmed.equals("TBD", ignoreCase = true) ||
        trimmed.contains("待定")
}

private fun Long.bracketDateKey(): String =
    if (this <= 0L) "日期待定" else relativeDayLabel(this)

/** 卡片上方那行需要“几号几点”，只给时间会分不清是哪一天；近几天用口语日期。 */
private fun Long.bracketDateTimeKey(): String =
    if (this <= 0L) "时间待定" else relativeDateTimeLabel(this)

private fun Long.bracketClockKey(): String =
    if (this <= 0L) "" else SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(this))

private fun Team?.toBracketSide(): BracketSide {
    if (this == null) {
        return BracketSide(name = "待定", logoUrl = null, score = null, isWinner = false, pending = true)
    }
    val pending = isPendingName(name)
    return BracketSide(
        name = name.trim().ifBlank { "待定" },
        logoUrl = logoUrl,
        score = if (pending) null else (bigScore ?: score)?.takeIf(String::isNotBlank),
        isWinner = winner,
        pending = pending,
    )
}

private fun BracketMatch.winnerSide(): BracketSide? = when {
    top.isWinner -> top
    bottom.isWinner -> bottom
    else -> null
}

/** 赛程里是“待定”但上一轮胜者已定时，把晋级队提前填进来——官方晋级图就是这么显示 IG 的。 */
private fun BracketSide.orProjected(winner: BracketSide?): BracketSide =
    if (pending && winner != null && !winner.pending) {
        winner.copy(score = null, isWinner = false, pending = false, projected = true)
    } else {
        this
    }

private fun knockoutTitle(level: Int): String = when (level) {
    1 -> "决赛"
    2 -> "半决赛"
    3 -> "四分之一决赛"
    4 -> "八分之一决赛"
    else -> "第 ${5 - level} 轮"
}

/**
 * 推断出来的树只保证「第几列」，不保证这一列是几强：LCK 冒泡赛第一列两场并不是半决赛，
 * 德杯决赛还没排期时第二列也还不能叫半决赛。按场次数硬套「半决赛 / 四分之一决赛」
 * 会标错轮次，所以这里一律写第几轮；只有最后一列单独一场收尾时才敢叫「决赛」。
 * 结构确定的标准单败树（3 / 7 场）走 [knockoutTitle]，不在这里。
 */
private fun roundTitleFor(count: Int, level: Int, depth: Int): String =
    if (level == depth && count == 1) "决赛" else "第 ${level + 1} 轮"

private fun MatchSummary.toBracketMatch(focusKey: String): BracketMatch {
    val sides = teams.take(2)
    return BracketMatch(
        match = this,
        top = sides.getOrNull(0).toBracketSide(),
        bottom = sides.getOrNull(1).toBracketSide(),
        isFocus = uniqueKey == focusKey || id == focusKey,
    )
}

private fun List<MatchSummary>.toBracketMatches(focusKey: String): List<BracketMatch> =
    map { it.toBracketMatch(focusKey) }

/** 按 2^(k-1) 场切分，例如 7 场 → 4 / 2 / 1。 */
private fun treeRounds(rows: List<MatchSummary>, depth: Int): MutableList<MutableList<MatchSummary>> {
    var cursor = 0
    return (depth downTo 1).map { level ->
        val count = 1 shl (level - 1)
        rows.subList(cursor, cursor + count).toMutableList().also { cursor += count }
    }.toMutableList()
}

/**
 * 让上一轮的两场比赛对齐到下一轮的一场：晋级图的连线只按位置画，顺序错了就会把胜者连到
 * 别的队身上。下一轮队伍已经确定时用它反查上一轮，未确定的“待定”保持原始顺序。
 */
private fun alignRounds(rounds: MutableList<MutableList<MatchSummary>>): List<MutableList<MatchSummary>> {
    for (index in 0 until rounds.lastIndex) {
        val feeder = rounds[index]
        val consumers = rounds[index + 1]
        val remaining = feeder.indices.toMutableList()
        val ordered = ArrayList<MatchSummary>(feeder.size)
        consumers.forEach { consumer ->
            val names = consumer.teams.mapNotNull { team ->
                team.name.trim().takeIf { it.isNotBlank() && !isPendingName(it) }
            }
            repeat(2) {
                if (remaining.isEmpty()) return@repeat
                val hit = if (names.isEmpty()) -1 else remaining.indexOfFirst { position ->
                    feeder[position].teams.any { it.name.trim() in names }
                }
                ordered += feeder[remaining.removeAt(if (hit >= 0) hit else 0)]
            }
        }
        remaining.forEach { ordered += feeder[it] }
        rounds[index] = ordered
    }
    return rounds
}

/**
 * 把同一赛事的全部对局排成横向晋级图。
 *
 * 数据源只有赛程接口（虎扑没有暴露 bracket 端点），所以轮次要靠推：
 * 标题带“瑞士轮”按战绩和出场次数分轮；4 队 6 场按双败摆位；场次凑成 2^k-1 按单败树；
 * 场次不凑巧但形状规整的淘汰赛按“谁把谁打上来”连成三角/阶梯；形状推不出来就退回
 * 每排 4 张的赛程网格，不硬连、也不横着平铺成一排。
 * 连比赛日都凑不出来的非淘汰赛分组返回 null，界面上连入口都不出现。
 *
 * @param focusKey 当前正在看的比赛 uniqueKey（或 id），用来高亮。
 */
fun buildTournamentBracket(
    matches: List<MatchSummary>,
    title: String,
    focusKey: String,
    advancedNames: Set<String> = emptySet(),
): TournamentBracket? {
    val rows = matches
        .filter { it.teams.size == 2 }
        .distinctBy { it.uniqueKey }
        .sortedWith(
            compareBy<MatchSummary> { it.startTimeMillis.takeIf { time -> time > 0L } ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )
    if (rows.size < 2) return null

    if (SwissTitlePattern.containsMatchIn(title)) return buildSwiss(rows, title, focusKey)
    // 入围赛可能是 4 队 6 场的双败，先让形态更具体的双败判断一次。
    buildDoubleElim(rows, title, focusKey)?.let { return it }
    // 循环赛不具备树形结构，别让下面的单败推断把它硬拼成一棵错误的树。
    if (GroupTitlePattern.containsMatchIn(title)) return buildGroupTable(rows, title, focusKey, advancedNames)
    buildKnockoutTree(rows, title, focusKey)?.let { return it }
    buildWinnerTree(rows, title, focusKey)?.let { return it }
    return buildKnockoutGrid(rows, title, focusKey)
}

/**
 * 单败树：场次正好 3 或 7。第二轮起，上一轮胜者会被填进下一轮的“待定”格子，
 * 所以出现“IG 已经进半决赛、对手还没定”的样子。
 */
private fun buildKnockoutTree(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
): TournamentBracket? {
    if (rows.size !in BracketTreeSizes) return null
    val depth = if (rows.size == 3) 2 else 3
    val rounds = alignRounds(treeRounds(rows, depth))

    val nodes = ArrayList<BracketNode>(rows.size)
    val built = ArrayList<List<BracketMatch>>(depth)
    rounds.forEachIndexed { column, group ->
        val span = rounds.first().size.toFloat() / group.size
        val roundTitle = knockoutTitle(depth - column)
        val matches = group.mapIndexed { index, summary ->
            val match = summary.toBracketMatch(focusKey)
            if (column == 0) {
                match
            } else {
                val feeders = built[column - 1]
                match.copy(
                    top = match.top.orProjected(feeders.getOrNull(index * 2)?.winnerSide()),
                    bottom = match.bottom.orProjected(feeders.getOrNull(index * 2 + 1)?.winnerSide()),
                )
            }
        }
        built += matches
        matches.forEachIndexed { index, match ->
            nodes += BracketNode(
                match = match,
                column = column,
                row = index * span + (span - 1f) / 2f,
                title = roundTitle,
                time = match.match.startTimeMillis.bracketDateTimeKey(),
            )
        }
    }

    val columns = rounds.mapIndexed { index, group ->
        BracketColumn(
            title = knockoutTitle(depth - index),
            caption = group.firstOrNull()?.let { "${it.startTimeMillis.bracketDateKey()} 起" }.orEmpty(),
        )
    }

    val wires = ArrayList<BracketWire>(rows.size - 1)
    for (column in 0 until depth - 1) {
        val source = nodes.filter { it.column == column }
        val targetSpan = rounds.first().size.toFloat() / rounds[column + 1].size
        source.forEachIndexed { index, node ->
            val slot = index / 2
            wires += BracketWire(
                fromColumn = column,
                fromRow = node.row,
                toColumn = column + 1,
                toRow = slot * targetSpan + (targetSpan - 1f) / 2f,
            )
        }
    }

    val champion = nodes
        .lastOrNull { it.column == depth - 1 }
        ?.match
        ?.match
        ?.takeIf { it.isTerminal }
        ?.teams
        ?.firstOrNull { it.winner }
        ?.toBracketSide()

    return TournamentBracket(
        title = title,
        kind = BracketKind.KNOCKOUT,
        columns = columns,
        nodes = nodes,
        wires = wires,
        champion = champion,
    )
}

/**
 * 双败：入围赛那种 4 队 6 场。按“开打前双方各输过几场”分类——
 * 两场 (0,0) 是胜者组首轮，第三场 (0,0) 是胜者组决赛，两场 (1,1) 依次是败者组首轮、
 * 败者组决赛，最后一场是决赛。结构对不上就不画，免得把别的赛制硬塞进来。
 */
private fun buildDoubleElim(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
): TournamentBracket? {
    if (rows.size != 6) return null
    val teamCount = rows.flatMap { it.teams }.map { it.name.trim() }.distinct().size
    if (teamCount != 4) return null

    val losses = HashMap<String, Int>()
    // 逐场累加败场数，标签取“开打前”的输过几场。
    val tags = rows.map { summary ->
        val sides = summary.teams.take(2)
        val before = sides.map { losses[it.name.trim()] ?: 0 }
        sides.filterNot { it.winner }.forEach { losses[it.name.trim()] = (losses[it.name.trim()] ?: 0) + 1 }
        (before.getOrNull(0) ?: 0) to (before.getOrNull(1) ?: 0)
    }
    val zero = tags.indices.filter { tags[it].first == 0 && tags[it].second == 0 }
    val oneOne = tags.indices.filter { tags[it].first == 1 && tags[it].second == 1 }
    if (zero.size != 3 || oneOne.size != 2) return null
    val grandIndex = rows.lastIndex
    if (grandIndex in zero || grandIndex in oneOne) return null

    val ubFirst = rows[zero[0]].toBracketMatch(focusKey)
    val ubSecond = rows[zero[1]].toBracketMatch(focusKey)
    val ubFinal = rows[zero[2]].toBracketMatch(focusKey)
    val lbFirst = rows[oneOne[0]].toBracketMatch(focusKey)
    val lbFinal = rows[oneOne[1]].toBracketMatch(focusKey)
    val grand = rows[grandIndex].toBracketMatch(focusKey)

    fun node(match: BracketMatch, column: Int, row: Float, label: String) = BracketNode(
        match = match,
        column = column,
        row = row,
        title = label,
        time = match.match.startTimeMillis.bracketDateTimeKey(),
    )

    // 官方只画三列，每列自上而下装 3 / 2 / 1 场，正好是一个收敛的三角：
    //   第一列 胜者组首轮 ×2 + 败者组首轮（两场胜者组首轮的败者就地掉下来，所以同列）
    //   第二列 胜者组决赛 + 败者组决赛
    //   第三列 总决赛
    // 行坐标按“一场一行”的紧凑网格排，列里不留空档，图才不歪。
    val nodes = listOf(
        node(ubFirst, column = 0, row = 0f, label = "胜者组首轮"),
        node(ubSecond, column = 0, row = 1f, label = "胜者组首轮"),
        node(lbFirst, column = 0, row = 2f, label = "败者组首轮"),
        node(ubFinal, column = 1, row = 0.5f, label = "胜者组决赛"),
        node(lbFinal, column = 1, row = 1.5f, label = "败者组决赛"),
        node(
            match = grand.copy(
                top = grand.top.orProjected(ubFinal.winnerSide()),
                bottom = grand.bottom.orProjected(lbFinal.winnerSide()),
            ),
            column = 2,
            row = 1f,
            label = "总决赛",
        ),
    )
    val wires = listOf(
        // 胜者组：首轮两场汇到胜者组决赛。
        BracketWire(0, 0f, 1, 0.5f),
        BracketWire(0, 1f, 1, 0.5f),
        // 败者组首轮胜者上行到败者组决赛。
        BracketWire(0, 2f, 1, 1.5f),
        // 胜者组冠军与败者组冠军汇入总决赛。
        BracketWire(1, 0.5f, 2, 1f),
        BracketWire(1, 1.5f, 2, 1f),
    )
    val columns = listOf(
        BracketColumn("胜者组首轮", ubFirst.match.startTimeMillis.bracketDateKey()),
        BracketColumn("胜者组决赛", ubFinal.match.startTimeMillis.bracketDateKey()),
        BracketColumn("总决赛", grand.match.startTimeMillis.bracketDateKey()),
    )
    val champion = grand.winnerSide()?.takeIf { grand.match.isTerminal }

    return TournamentBracket(
        title = title,
        kind = BracketKind.DOUBLE_ELIM,
        columns = columns,
        nodes = nodes,
        wires = wires,
        champion = champion,
    )
}

/**
 * 场次凑不成 2^k-1 的淘汰赛（德玛西亚杯八强打到一半、LCK 那种冒泡赛）以前只能按比赛日分列、
 * 列与列之间一根线都没有。这里换成按“谁把谁打上来”建图：某队最近赢下的那场就是它的来路，
 * 顺着来路把上游两场并成一场，一路收敛成一个三角形。
 *
 * 只对标题像淘汰赛的赛事生效；形状对不上（双败、重置赛、小数行、五列以上）就返回 null，
 * 交给每排四张的赛程网格兜底——宁可不连，也不能把胜者连到错误的上一场。
 */
private fun buildWinnerTree(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
): TournamentBracket? {
    if (rows.size !in 4..16) return null
    if (!KnockoutTitlePattern.containsMatchIn(title)) return null

    val winners = rows.map { summary ->
        summary.teams.firstOrNull { it.winner }?.name?.trim()
            ?.takeIf { it.isNotEmpty() && !isPendingName(it) }
    }
    // 逐场推进：本场的双方各自最近一次打赢的比赛，就是它们两个的来路。还没打完的场次没有胜者，
    // 退回“最近一次出场”，这样没开打的半决赛也能挂到已经结束的上一轮上。
    val lastWin = HashMap<String, Int>()
    val lastOut = HashMap<String, Int>()
    val upstreamOf = ArrayList<List<Int>>(rows.size)
    rows.forEachIndexed { index, summary ->
        val names = summary.teams.take(2).map { it.name.trim() }.filterNot(::isPendingName)
        val upstream = LinkedHashSet<Int>()
        names.forEach { name ->
            val from = lastWin[name] ?: lastOut[name]
            if (from != null && from < index) upstream += from
        }
        upstreamOf += upstream.toList()
        names.forEach { lastOut[it] = index }
        winners[index]?.let { lastWin[it] = index }
    }

    val level = IntArray(rows.size) { -1 }
    fun levelOf(index: Int): Int {
        if (level[index] >= 0) return level[index]
        level[index] = 0
        val upstream = upstreamOf[index]
        level[index] = if (upstream.isEmpty()) 0 else upstream.maxOf(::levelOf) + 1
        return level[index]
    }
    val depth = rows.indices.maxOf(::levelOf)
    // 五列以上横着放不下（卡片被压到最小宽度还要横向滚动），也不再像一张晋级图。
    if (depth > 3) return null
    val perColumn = (0..depth).map { column -> rows.indices.count { levelOf(it) == column } }
    // 首轮超过四场时整张图会高过一屏，交给每排四张的赛程网格。
    if (perColumn.first() > BracketCardsPerRow) return null
    // 后面的列比前面还多，说明推出来的根本不是收敛的树，硬画只会歪。
    if (perColumn.zipWithNext().any { (previous, next) -> next > previous }) return null

    // 从最后一场沿“最深的上游”回溯出主链，剩下的都是轮空或掉级过来的支线。
    val tip = rows.indices.maxByOrNull { levelOf(it) }!!
    val mainChain = LinkedHashSet<Int>()
    var cursor: Int? = tip
    while (cursor != null) {
        val current = cursor
        mainChain += current
        cursor = upstreamOf[current].maxByOrNull { levelOf(it) }
    }
    val branches = rows.indices.filter { it !in mainChain }
    // 冒泡/阶梯赛（LCK 淘汰赛）的特征：有支线，且存在跨两列以上的长线（轮空队直进决赛）。
    val isLadder = branches.isNotEmpty() && rows.indices.any { index ->
        upstreamOf[index].any { from -> levelOf(index) - levelOf(from) >= 2 }
    }
    if (isLadder) {
        // 阶梯/冒泡赛只在起点分叉：第一列若干场，之后每列恰好一场。
        // 后面还有分叉（LPL 双败那种掉级、重置）说明晋级路径推不准，
        // 两条通道会把卡片撒成一片散点，宁可退回每排四张的网格。
        if (branches.size > 2 || perColumn.drop(1).any { it != 1 }) return null
    }

    val row = FloatArray(rows.size) { -1f }
    if (isLadder) {
        // 重心法会把这种赛制压成 0 / 0.5 / 0.75 的小数行：卡片和头顶标注错开不到一个
        // 行高，整张图看着是歪的。改成两条整数通道——支线在上、主链在下，连线横平竖直。
        branches.forEachIndexed { index, match -> row[match] = index.toFloat() }
        val mainRow = branches.size.toFloat()
        mainChain.forEach { row[it] = mainRow }
    } else {
        // 行坐标以“首轮第几场”为单位：一场比赛落在它上游两场的重心上，正好是官方晋级图那种三角形。
        var slot = 0f
        fun place(index: Int): Float {
            if (row[index] >= 0f) return row[index]
            val upstream = upstreamOf[index]
            row[index] = if (upstream.isEmpty()) {
                slot += 1f
                slot - 1f
            } else {
                upstream.map(::place).average().toFloat()
            }
            return row[index]
        }
        rows.indices.forEach(::place)
        // 重心法算出的行只能落在整行/半行上才是规整三角。0.25 / 0.75 这类小数
        // 说明形状推错了（LCK 旧版就是 0.75），硬画必然歪斜，不如退回网格。
        if (row.any { value -> value * 2f != (value * 2f).toInt().toFloat() }) return null
    }

    // 同一列里两张卡压在一起，说明这不是一棵能收敛的树（双败、冒泡赛），别硬画。
    for (column in 0..depth) {
        val rowsInColumn = rows.indices.filter { levelOf(it) == column }.map { row[it] }.sorted()
        if (rowsInColumn.zipWithNext().any { (first, second) -> second - first < 0.99f }) return null
    }

    val columns = (0..depth).map { column ->
        val indexes = rows.indices.filter { levelOf(it) == column }
        BracketColumn(
            title = roundTitleFor(indexes.size, column, depth),
            caption = rows[indexes.min()].startTimeMillis.bracketDateKey(),
        )
    }
    val nodes = rows.indices.map { index ->
        BracketNode(
            match = rows[index].toBracketMatch(focusKey),
            column = level[index],
            row = row[index],
            title = columns[level[index]].title,
            time = rows[index].startTimeMillis.bracketDateTimeKey(),
        )
    }
    val wires = rows.indices.flatMap { index ->
        upstreamOf[index].map { from ->
            BracketWire(
                fromColumn = level[from],
                fromRow = row[from],
                toColumn = level[index],
                toRow = row[index],
                // 轮空长线的折角贴目标列左沿，别落在中间列正上方（会横穿那列的卡片）。
                endElbow = isLadder && level[index] - level[from] >= 2,
            )
        }
    }
    val finalIndex = rows.indices.singleOrNull { levelOf(it) == depth }
    val champion = finalIndex?.let { rows[it] }
        ?.takeIf { it.isTerminal }
        ?.teams?.firstOrNull { it.winner }
        ?.toBracketSide()

    return TournamentBracket(
        title = title,
        kind = BracketKind.KNOCKOUT,
        columns = columns,
        nodes = nodes,
        wires = wires,
        champion = champion,
    )
}

/**
 * 瑞士轮：官方没有“晋级图”，但有多轮和出线名单。轮次按“这队第几次出场”推——
 * 瑞士轮每队每轮只打一场，所以一场比赛所属轮次 = min(双方已出场次数) + 1，实测 33 场全部自洽。
 */
private fun buildSwiss(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
): TournamentBracket? {
    val played = HashMap<String, Int>()
    val grouped = LinkedHashMap<Int, MutableList<MatchSummary>>()
    rows.forEach { summary ->
        val names = summary.teams.take(2).map { it.name.trim() }
        val round = names.maxOf { (played[it] ?: 0) + 1 }
        grouped.getOrPut(round) { mutableListOf() } += summary
        names.forEach { played[it] = (played[it] ?: 0) + 1 }
    }
    if (grouped.isEmpty()) return null

    val rounds = grouped.entries.sortedBy { it.key }.map { (round, matches) ->
        SwissRound(
            index = round,
            caption = buildString {
                append(matches.first().startTimeMillis.bracketDateKey())
                append(" · ")
                append(matches.size)
                append(" 场")
            },
            matches = matches.toBracketMatches(focusKey),
        )
    }

    val wins = HashMap<String, Int>()
    val losses = HashMap<String, Int>()
    val logos = HashMap<String, String?>()
    rows.forEach { summary ->
        summary.teams.take(2).forEach { team ->
            val name = team.name.trim()
            if (name.isNotEmpty()) {
                logos.putIfAbsent(name, team.logoUrl)
                if (team.winner) wins[name] = (wins[name] ?: 0) + 1 else losses[name] = (losses[name] ?: 0) + 1
            }
        }
    }
    val standings = (wins.keys + losses.keys)
        .map { name ->
            val w = wins[name] ?: 0
            val l = losses[name] ?: 0
            SwissStanding(
                name = name,
                logoUrl = logos[name],
                wins = w,
                losses = l,
                advanced = w >= 3,
                eliminated = l >= 3,
            )
        }
        .sortedWith(
            compareByDescending<SwissStanding> { it.advanced }
                .thenByDescending { it.wins }
                .thenBy { it.losses }
                .thenBy { it.name },
        )

    return TournamentBracket(
        title = title,
        kind = BracketKind.SWISS,
        swissRounds = rounds,
        standings = standings,
    )
}

/**
 * 小组赛 / 入围赛：官方给的是战绩表而不是对阵树，画不出连线，但可以像瑞士轮一样汇总。
 * 每天的对局列成一批，顶部压一张战绩表。
 *
 * 晋级队列取同赛事**后续阶段实际登场的队伍**——这是数据里唯一可靠的出线证据。按胜场划线不行：
 * 小组赛各队场次并不相等（德玛西亚杯 12 支队、20 场，有的打 4 场有的打 3 场）。
 */
private fun buildGroupTable(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
    advancedNames: Set<String>,
): TournamentBracket? {
    val byDay = rows.groupBy { it.startTimeMillis.bracketDateKey() }
    val rounds = byDay.entries
        .sortedBy { entry ->
            entry.value.minOf { it.startTimeMillis.takeIf { time -> time > 0L } ?: Long.MAX_VALUE }
        }
        .mapIndexed { index, (date, dayMatches) ->
            SwissRound(
                index = index + 1,
                caption = "$date · ${dayMatches.size} 场",
                matches = dayMatches.toBracketMatches(focusKey),
            )
        }
    val standings = leagueStandings(rows, advancedNames)
    if (standings.isEmpty()) return null
    return TournamentBracket(
        title = title,
        kind = BracketKind.GROUP,
        swissRounds = rounds,
        standings = standings,
    )
}

/**
 * 循环赛战绩表。[advancedNames] 为空时只排胜场、不下淘汰结论——宁可少说，也不要把还在打
 * 的队伍提前划掉。
 */
private fun leagueStandings(
    rows: List<MatchSummary>,
    advancedNames: Set<String>,
): List<SwissStanding> {
    val wins = HashMap<String, Int>()
    val losses = HashMap<String, Int>()
    val open = HashMap<String, Int>()
    val logos = HashMap<String, String?>()
    rows.forEach { summary ->
        // 双方都没标 winner，说明这场还没分出胜负，不计入战绩。
        val decided = summary.teams.take(2).any { it.winner }
        summary.teams.take(2).forEach { team ->
            val name = team.name.trim()
            if (name.isEmpty()) return@forEach
            logos.putIfAbsent(name, team.logoUrl)
            if (!decided) {
                open[name] = (open[name] ?: 0) + 1
                return@forEach
            }
            if (team.winner) wins[name] = (wins[name] ?: 0) + 1 else losses[name] = (losses[name] ?: 0) + 1
        }
    }
    val names = wins.keys + losses.keys + open.keys
    if (names.isEmpty()) return emptyList()
    return names
        .map { name ->
            SwissStanding(
                name = name,
                logoUrl = logos[name],
                wins = wins[name] ?: 0,
                losses = losses[name] ?: 0,
                advanced = name in advancedNames,
                eliminated = advancedNames.isNotEmpty() &&
                    name !in advancedNames &&
                    (open[name] ?: 0) == 0,
            )
        }
        .sortedWith(
            compareByDescending<SwissStanding> { it.advanced }
                .thenByDescending { it.wins }
                .thenBy { it.losses }
                .thenBy { it.name },
        )
}

/**
 * 同一项赛事里，比 [stage] 更晚的阶段都派了哪些队。小组赛→淘汰赛、入围赛→淘汰赛都靠它
 * 判断谁出线：赛程里没有“出线”字段，但下一阶段的实际参赛名单就是答案。
 * 只有确实存在更晚阶段时才返回非空，否则调用方保持“只排战绩”的保守显示。
 */
internal fun advancingTeams(pool: List<MatchSummary>, stage: String): Set<String> {
    val title = stage.trim()
    if (title.isEmpty()) return emptySet()
    val family = competitionFamily(title)
    val mine = pool.filter { it.introduction.trim() == title }
    // 界线取本阶段的最后一场：下一阶段一定是它结束之后才开打。用首场当界线会把上一届
    // 同一阶段名下的老场次也算进来（旧数据不会随赛季清掉）。
    val lastAt = mine.maxOfOrNull { it.startTimeMillis } ?: 0L
    val cutoff = if (lastAt > 0L) lastAt - 86_400_000L else Long.MIN_VALUE
    return pool.filter { summary ->
        val other = summary.introduction.trim()
        other.isNotEmpty() &&
            other != title &&
            competitionFamily(other) == family &&
            (summary.startTimeMillis <= 0L || summary.startTimeMillis >= cutoff)
    }
        .flatMap { it.teams }
        .map { it.name.trim() }
        .filter { it.isNotEmpty() && !isPendingName(it) }
        .toSet()
}

/**
 * 形状推不出来时的兜底：不硬凑晋级关系，按开赛时间把每场摆成一张卡片，
 * 每排 [BracketCardsPerRow] 张一路往下排。
 *
 * 旧版按比赛日分列，一天一场时整张图就是横着平铺的一排窄卡片，列多到要横向滚动、
 * 卡片被压到最窄，还看不出任何轮次关系；双败/重置赛被硬连成树时更是一片散点。
 * 现在宁可老老实实列成网格，卡片宽度和瑞士轮那套一致，也不假装画出了晋级路径。
 */
private fun buildKnockoutGrid(
    rows: List<MatchSummary>,
    title: String,
    focusKey: String,
): TournamentBracket? {
    if (!KnockoutTitlePattern.containsMatchIn(title)) return null
    val cards = rows.toBracketMatches(focusKey).map { match ->
        BracketCard(
            match = match,
            title = match.match.startTimeMillis.bracketDateKey(),
            time = match.match.startTimeMillis.bracketClockKey(),
        )
    }
    return TournamentBracket(
        title = title,
        kind = BracketKind.GRID,
        cards = cards,
    )
}
