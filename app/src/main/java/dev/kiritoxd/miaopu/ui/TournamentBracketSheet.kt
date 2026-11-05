package dev.kiritoxd.miaopu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiritoxd.miaopu.data.BracketCardsPerRow
import dev.kiritoxd.miaopu.data.BracketKind
import dev.kiritoxd.miaopu.data.BracketMatch
import dev.kiritoxd.miaopu.data.BracketSide
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.SwissRound
import dev.kiritoxd.miaopu.data.SwissStanding
import dev.kiritoxd.miaopu.data.TournamentBracket
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 卡片宽度不再写死：按浮层可用宽度均分，左边缘和赛事标题对齐，右边几乎不留白。
// 上限只是防「整棵树只有一列」时单张卡拉成一条横幅。
private val CardMaxWidth = 168.dp
private val CardMinWidth = 84.dp
private val CardHeight = 50.dp
private val RowGap = 10.dp
/**
 * 列距直接决定折线两段水平线的长度（各占一半）。8dp 时一段只有 4dp，
 * 看起来像两张卡贴在一起、没有连线，所以放大到 20dp（每段 10dp）。
 */
private val ColumnGap = 20.dp
private val ColumnMinGap = 6.dp
/** 卡片正上方那行「轮次 · 日期时间」。官方晋级图里是逐场标注的，不是列头。 */
private val CaptionHeight = 13.dp
private val CaptionGap = 3.dp
private val RowStep = CaptionHeight + CaptionGap + CardHeight + RowGap

/** 瑞士轮一轮打四场，就排成四列一行；形状推不出来的淘汰赛同样每排四张。 */
private const val SwissColumns = BracketCardsPerRow
private val SwissColumnGap = 6.dp

private val SheetEnterEasing = CubicBezierEasing(0.15f, 0f, 0.1f, 1f)
private val SheetExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * 赛事晋级图浮层。数据完全来自已加载的赛程（虎扑没有 bracket 接口）：淘汰赛按场次数和时间
 * 推轮次，瑞士轮按出场次数分轮、按比分算战绩。
 */
@Composable
internal fun TournamentBracketSheet(
    title: String,
    bracket: TournamentBracket?,
    loading: Boolean,
    onOpenMatch: (MatchSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    val requestClose = {
        if (!closing) {
            closing = true
            transitionState.targetState = false
        }
    }
    BackHandler { requestClose() }
    LaunchedEffect(transitionState.isIdle, closing) {
        if (closing && transitionState.isIdle) onDismiss()
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { requestClose() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Card(
                    modifier = Modifier
                        .animateEnterExit(
                            enter = slideInVertically(tween(200, easing = SheetEnterEasing)) { it } +
                                fadeIn(tween(200)),
                            exit = slideOutVertically(tween(220, easing = SheetExitEasing)) { it } +
                                fadeOut(tween(200)),
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .navigationBarsPadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                    cornerRadius = 24.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = bracket?.summaryText() ?: "晋级路径",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        IconButton(onClick = requestClose) {
                            Icon(LucideIcons.X, "关闭晋级图", Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (bracket == null) {
                        BracketPlaceholder(loading)
                    } else {
                        when (bracket.kind) {
                            BracketKind.SWISS, BracketKind.GROUP -> SwissBody(
                                bracket = bracket,
                                onOpenMatch = onOpenMatch,
                                maxHeight = screenHeight * 0.54f,
                            )
                            BracketKind.GRID -> KnockoutGridBody(
                                bracket = bracket,
                                onOpenMatch = onOpenMatch,
                                maxHeight = screenHeight * 0.54f,
                            )
                            else -> BracketGrid(bracket, onOpenMatch)
                        }
                        bracket.champion?.let { champion ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "冠军 · ${champion.name}",
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bracket?.footnote() ?: "数据仅覆盖当前赛程范围",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private fun TournamentBracket.summaryText(): String = when (kind) {
    BracketKind.SWISS -> "$matchCount 场 · ${swissRounds.size} 轮"
    BracketKind.GROUP -> "$matchCount 场 · ${standings.size} 队"
    BracketKind.DOUBLE_ELIM -> "$matchCount 场 · 双败淘汰"
    BracketKind.KNOCKOUT -> "$matchCount 场 · 单败淘汰"
    BracketKind.GRID -> "$matchCount 场 · 按时间顺序"
}

private fun TournamentBracket.footnote(): String = when (kind) {
    BracketKind.SWISS -> "战绩按赛程比分统计，数据仅覆盖当前赛程范围"
    BracketKind.GROUP -> "战绩按赛程比分统计，晋级以后续阶段的参赛队伍为准"
    BracketKind.DOUBLE_ELIM -> "数据仅覆盖当前赛程范围"
    BracketKind.KNOCKOUT -> "晋级路径由赛程时间与场次推断，数据仅覆盖当前赛程范围"
    BracketKind.GRID -> "晋级关系无法从赛程确定，按比赛时间排列"
}

@Composable
private fun BracketPlaceholder(loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
            Text(
                "正在拉取这项赛事的全部对局",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Text(
                "赛程里还凑不出这项赛事的对阵",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 淘汰赛网格：连线画在底层，卡片按“第几列 / 第几行”绝对定位。位置由数据层算好，
 * 单败树和双败（败者组）用的是同一套绘制。轮次与时间逐场标在卡片正上方。
 */
@Composable
private fun BracketGrid(bracket: TournamentBracket, onOpenMatch: (MatchSummary) -> Unit) {
    if (bracket.nodes.isEmpty()) return
    val maxRow = bracket.nodes.maxOf { it.row }
    val maxColumn = bracket.nodes.maxOf { it.column }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = this.maxWidth
        val columns = maxColumn + 1
        // 同列竖直连线画在卡片外侧，网格要给左右两边都留出这几 dp，否则会被 Canvas 裁掉。
        val padLeft = if (bracket.wires.any { it.vertical && it.left }) 7.dp else 0.dp
        val padRight = if (bracket.wires.any { it.vertical && !it.left }) 6.dp else 0.dp
        // 卡片宽度按可用宽度均分：宁可卡片宽一点，也不要一堆空白挤在两侧。
        val available = viewport - padLeft - padRight
        var columnGap = ColumnGap
        var cardWidth = ((available - columnGap * (columns - 1)) / columns)
            .coerceIn(CardMinWidth, CardMaxWidth)
        // 列太多、压到最小宽度还放不下时才收窄列距，剩下的交给横向滚动。
        if (cardWidth * columns + columnGap * (columns - 1) > available) {
            columnGap = ColumnMinGap
            cardWidth = ((available - columnGap * (columns - 1)) / columns)
                .coerceIn(CardMinWidth, CardMaxWidth)
        }
        val columnStep = cardWidth + columnGap
        val gridWidth = columnStep * maxColumn + cardWidth + padLeft + padRight
        val gridHeight = RowStep * maxRow + CaptionHeight + CaptionGap + CardHeight
        val fits = gridWidth <= viewport
        Box(
            modifier = if (fits) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            },
        ) {
            Box(
                Modifier
                    .width(gridWidth)
                    .height(gridHeight),
            ) {
                BracketWires(bracket, cardWidth = cardWidth, columnStep = columnStep, originX = padLeft)
                bracket.nodes.forEach { node ->
                    Column(
                        modifier = Modifier
                            .offset(x = padLeft + columnStep * node.column, y = RowStep * node.row)
                            .width(cardWidth),
                    ) {
                        MatchCaption(title = node.title, time = node.time)
                        Spacer(Modifier.height(CaptionGap))
                        BracketMatchCard(
                            match = node.match,
                            onOpenMatch = onOpenMatch,
                            modifier = Modifier.width(cardWidth).height(CardHeight),
                        )
                    }
                }
            }
        }
    }
}

/** 每场比赛头顶那一行：左边轮次（半决赛／胜者组决赛），右边日期时间。 */
@Composable
private fun MatchCaption(title: String, time: String) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(CaptionHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = time,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            color = scheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun BracketWires(bracket: TournamentBracket, cardWidth: Dp, columnStep: Dp, originX: Dp) {
    if (bracket.wires.isEmpty()) return
    val lineColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.32f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = 1.1.dp.toPx()
        fun x(index: Int, edge: Float): Float =
            originX.toPx() + (columnStep * index).toPx() + cardWidth.toPx() * edge
        fun y(row: Float): Float =
            (RowStep * row + CaptionHeight + CaptionGap + CardHeight / 2).toPx()

        bracket.wires.forEach { wire ->
            if (wire.vertical) {
                // 同一列里的上下衔接：败者沿本列外侧往下掉。
                // 走哪一侧由数据层决定——第一列走左侧，避免和右侧的晋级折线挤在一起；
                // 后面的列走右侧（紧贴卡片，折线走列间中线），两条线才分得开。
                val cx = if (wire.left) {
                    x(wire.fromColumn, 0f) - 5.dp.toPx()
                } else {
                    x(wire.fromColumn, 1f) + 3.dp.toPx()
                }
                drawLine(
                    color = lineColor,
                    start = Offset(cx, y(wire.fromRow) + CardHeight.toPx() / 2),
                    end = Offset(cx, y(wire.toRow) - CardHeight.toPx() / 2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = if (wire.dashed) dash else null,
                )
                return@forEach
            }
            val x1 = x(wire.fromColumn, 1f)
            val x2 = x(wire.toColumn, 0f)
            val y1 = y(wire.fromRow)
            val y2 = y(wire.toRow)
            // 轮空长线：折角贴到目标列左沿，水平段全程走空着的上半区，
            // 默认中点会让后半段水平线正好穿过中间列下半区的卡片。
            val joint = if (wire.endElbow) x2 - 6.dp.toPx() else (x1 + x2) / 2f
            drawPath(
                Path().apply {
                    moveTo(x1, y1)
                    lineTo(joint, y1)
                    lineTo(joint, y2)
                    lineTo(x2, y2)
                },
                color = lineColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = if (wire.dashed) dash else null,
                ),
            )
        }
    }
}

@Composable
private fun BracketMatchCard(
    match: BracketMatch,
    onOpenMatch: (MatchSummary) -> Unit,
    modifier: Modifier = Modifier.height(CardHeight),
) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val label = "${match.top.name} 对 ${match.bottom.name}"
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (match.isFocus) scheme.primary.copy(alpha = 0.10f) else scheme.surfaceContainerHigh)
            .then(if (match.isFocus) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
            .semantics {
                role = Role.Button
                contentDescription = if (match.isFocus) "正在查看：$label" else "查看 $label"
            }
            .clickable(role = Role.Button) { onOpenMatch(match.match) },
    ) {
        BracketSideRow(match.top, top = true)
        BracketSideRow(match.bottom, top = false)
    }
}

@Composable
private fun BracketSideRow(side: BracketSide, top: Boolean) {
    val scheme = MiuixTheme.colorScheme
    // 胜者整行垫一层主色底：光靠把名字染成主色不够显眼，尤其卡片本身已经是高亮态时。
    // 高亮块横向铺满整张卡、上下各占一半，只把外侧两角做成和卡片一致的 12dp，
    // 内侧两角按直角收口，中间那条分隔线才不会出现两个圆角缺口。
    val highlightShape = if (top) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight / 2)
            .then(
                if (side.isWinner) {
                    Modifier.background(scheme.primary.copy(alpha = 0.38f), highlightShape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BracketTeamMark(side, 15.dp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = side.name,
            modifier = Modifier.weight(1f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (side.isWinner) FontWeight.Bold else FontWeight.Normal,
            color = when {
                side.isWinner -> scheme.primary
                side.pending -> scheme.onSurfaceVariantSummary
                else -> scheme.onSurface.copy(alpha = 0.72f)
            },
        )
        side.score?.let { score ->
            Spacer(Modifier.width(3.dp))
            Text(
                text = score,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (side.isWinner) scheme.primary else scheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun BracketTeamMark(side: BracketSide, size: Dp) {
    if (side.logoUrl.isNullOrBlank()) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(MiuixTheme.colorScheme.surfaceVariant),
        )
    } else {
        LogoImage(url = side.logoUrl, modifier = Modifier.size(size))
    }
}

/**
 * 晋级关系推不出来的淘汰赛：不画连线，按开赛时间每排四张一路往下排。
 * 列宽、卡片和头顶的日期时间都跟瑞士轮/小组赛那套一致，最后一行缺位补空保持列对齐。
 */
@Composable
private fun KnockoutGridBody(
    bracket: TournamentBracket,
    onOpenMatch: (MatchSummary) -> Unit,
    maxHeight: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        bracket.cards.chunked(BracketCardsPerRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(BracketCardsPerRow) { index ->
                    if (index > 0) Spacer(Modifier.width(SwissColumnGap))
                    val card = row.getOrNull(index)
                    if (card == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Column(Modifier.weight(1f)) {
                            MatchCaption(title = card.title, time = card.time)
                            Spacer(Modifier.height(CaptionGap))
                            BracketMatchCard(
                                match = card.match,
                                onOpenMatch = onOpenMatch,
                                modifier = Modifier.fillMaxWidth().height(CardHeight),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(RowGap))
        }
    }
}

/**
 * 瑞士轮：官方没有晋级图，但有多轮和出线名单。上面按 3 胜晋级 / 3 负淘汰分组列战绩，
 * 下面按轮次排每轮的对局。
 */
@Composable
private fun SwissBody(
    bracket: TournamentBracket,
    onOpenMatch: (MatchSummary) -> Unit,
    maxHeight: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        val advanced = bracket.standings.filter { it.advanced }
        val eliminated = bracket.standings.filter { it.eliminated }
        val playing = bracket.standings.filter { !it.advanced && !it.eliminated }
        // 小组赛没有“打满几胜”的规则，没出线名单时这一组就只是战绩榜，措辞也得跟着变。
        val group = bracket.kind == BracketKind.GROUP
        if (advanced.isNotEmpty()) {
            StandingGroup("晋级", advanced, MiuixTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        if (playing.isNotEmpty()) {
            StandingGroup(
                label = if (group) "战绩" else "进行中",
                standings = playing,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (eliminated.isNotEmpty()) {
            StandingGroup(if (group) "未晋级" else "淘汰", eliminated, MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        bracket.swissRounds.forEach { round ->
            Spacer(Modifier.height(12.dp))
            SwissRoundHeader(round)
            Spacer(Modifier.height(6.dp))
            // 一轮四场就该排成一行：两列排下来一轮要占两排，浮层里翻半天才看得到下一轮。
            round.matches.chunked(SwissColumns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(SwissColumns) { index ->
                        if (index > 0) Spacer(Modifier.width(SwissColumnGap))
                        val match = row.getOrNull(index)
                        if (match != null) {
                            BracketMatchCard(
                                match = match,
                                onOpenMatch = onOpenMatch,
                                modifier = Modifier.weight(1f).height(CardHeight),
                            )
                        } else {
                            // 场次不满一行时留空位，卡片宽度和上面几行保持一致。
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SwissRoundHeader(round: SwissRound) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "第 ${round.index} 轮",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = round.caption,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun StandingGroup(label: String, standings: List<SwissStanding>, tint: Color) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "$label ${standings.size} 队",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Spacer(Modifier.height(4.dp))
        // 和下面的对局卡片同一套列宽：一排四队，缺的位补空，四行卡片的列始终对齐。
        standings.chunked(SwissColumns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(SwissColumns) { index ->
                    if (index > 0) Spacer(Modifier.width(SwissColumnGap))
                    val standing = row.getOrNull(index)
                    if (standing == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        StandingChip(standing, tint, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun StandingChip(standing: SwissStanding, tint: Color, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    // 一排四队后每枚 chip 只有 80 dp 出头，内边距和队徽间距都收一点，把宽度留给队名。
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BracketTeamMark(
            side = BracketSide(
                name = standing.name,
                logoUrl = standing.logoUrl,
                score = null,
                isWinner = false,
                pending = false,
            ),
            size = 14.dp,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = standing.name,
            modifier = Modifier.weight(1f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurface,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "${standing.wins}-${standing.losses}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = tint,
        )
    }
}
