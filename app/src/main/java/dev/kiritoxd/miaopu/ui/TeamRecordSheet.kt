package dev.kiritoxd.miaopu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onOpenMatch: (MatchSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    if (team == null) return
    // Without this the system back would pop the match page out from under the open sheet.
    BackHandler { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            // Tapping the dimmed area closes; the panel below swallows its own taps.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier
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
                IconButton(onClick = onDismiss) {
                    Icon(LucideIcons.X, "关闭近期战绩", Modifier.size(20.dp))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamRecordHeader(team, results)
                if (results.isEmpty()) {
                    Text(
                        "赛程范围内还没有这支队伍的已结束比赛",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
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

@Composable
private fun TeamRecordHeader(team: Team, results: List<TeamResult>) {
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
                    results.recordSummary()?.let { "近 ${results.size} 场 $it" }
                        ?: "近 ${results.size} 场",
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
