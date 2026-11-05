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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.Team
import dev.kiritoxd.miaopu.data.TeamResult
import dev.kiritoxd.miaopu.data.recordSummary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SheetEnterEasing = CubicBezierEasing(0.15f, 0f, 0.1f, 1f)
private val SheetExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * Recent results for one team, opened from a crest on the match page. Rows come straight from the
 * schedules already in memory and open the full detail page on tap.
 *
 * Drawn as a plain overlay inside the caller's Box rather than a Miuix bottom sheet: the sheet
 * component silently rendered nothing here, and this needs no popup window to appear.
 */
@Composable
internal fun TeamRecordSheet(
    team: Team?,
    results: List<TeamResult>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenMatch: (MatchSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    if (team == null) return
    // AnimatedVisibility(Boolean) 会把首次组合时的值直接当成动画起点，传 true 进来的话
    // 永远不会播入场动画。必须从一个显式 false 的 MutableTransitionState 起步。
    val transitionState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    var closing by remember { mutableStateOf(false) }
    val requestClose = {
        if (!closing) {
            closing = true
            transitionState.targetState = false
        }
    }
    // Without this the system back would pop the match page out from under the open sheet.
    BackHandler { requestClose() }
    // 退场动画真正跑完（isIdle）才通知调用方移除，否则浮层会硬切消失。
    LaunchedEffect(transitionState.isIdle, closing) {
        if (closing && transitionState.isIdle) onDismiss()
    }
    Box(Modifier.fillMaxSize()) {
        // 遮罩只做淡入；面板再用 animateEnterExit 走自己的滑入轨迹。
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    // Tapping the dimmed area closes; the panel below swallows its own taps.
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
                            exit = slideOutVertically(tween(200, easing = SheetExitEasing)) { it } +
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
                        Text(
                            "${team.name} · 近期战绩",
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = requestClose) {
                            Icon(LucideIcons.X, "关闭近期战绩", Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TeamRecordHeader(team, results, refreshing)
                        if (results.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    if (refreshing) "正在拉取近期比赛…" else "赛程里还没有这支队伍的已结束比赛",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                if (refreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                } else {
                                    // The cached schedule may simply predate these results, so offer a pull
                                    // before telling the reader there is nothing to show.
                                    TextButton(text = "重新拉取", onClick = onRefresh)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(results, key = { "team-result-${it.match.uniqueKey}" }) { result ->
                                    TeamRecordRow(result) { onOpenMatch(result.match) }
                                    if (result !== results.last()) TeamRecordDivider()
                                }
                            }
                        }
                        Text(
                            "统计来自当前赛程范围内的已结束比赛",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamRecordHeader(team: Team, results: List<TeamResult>, refreshing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(team.name, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (refreshing && results.isEmpty()) "正在拉取…"
                    else results.recordSummary()?.let { "近 ${results.size} 场 $it" } ?: "近 ${results.size} 场",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun TeamRecordRow(result: TeamResult, onClick: () -> Unit) {
    val won = result.won
    val lost = won == false
    val day = remember(result.match.startTimeMillis) { result.match.startTimeMillis.formatDay() }
    // Which round the match belonged to ("LCK联赛淘汰赛"), so a run of results is not just names.
    val competition = remember(result.match) {
        result.match.introduction.ifBlank { result.match.esport.shortTitle }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(day, Modifier.width(46.dp), fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
        Text(if (won == true) "胜" else if (lost) "负" else "—",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (won) {
                        true -> MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                        false -> MiuixTheme.colorScheme.error.copy(alpha = 0.14f)
                        null -> MiuixTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            // Losses are red so a bad run reads at a glance without checking every score.
            color = when (won) {
                true -> MiuixTheme.colorScheme.primary
                false -> MiuixTheme.colorScheme.error
                null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            result.opponent?.let {
                TeamLogo(it, 22.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(result.opponentName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(result.scoreLine ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                competition,
                modifier = Modifier.widthIn(max = 118.dp),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun TeamRecordDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f)))
}

private fun Long.formatDay(): String {
    if (this <= 0L) return "—"
    return SimpleDateFormat("MM-dd", Locale.ROOT).format(Date(this))
}
