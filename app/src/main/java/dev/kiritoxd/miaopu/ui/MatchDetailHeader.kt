package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.Team
import dev.kiritoxd.miaopu.data.matchdetail.MatchLiveScore
import dev.kiritoxd.miaopu.data.relativeDateTimeLabel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MatchHero(
    match: MatchSummary,
    liveScore: MatchLiveScore? = null,
    onTeamClick: ((Team) -> Unit)? = null,
    onCompetitionClick: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 赛事名兼作晋级图入口：点它看整段赛事的晋级路径。
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onCompetitionClick != null) {
                            Modifier
                                .clickable(role = Role.Button, onClick = onCompetitionClick)
                                .padding(horizontal = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(match.introduction.ifBlank { match.name }, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false))
                if (onCompetitionClick != null) {
                    Spacer(Modifier.width(3.dp))
                    Icon(LucideIcons.ChevronRight, contentDescription = null, Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Text(listOf(match.name.takeUnless { it == match.introduction }.orEmpty(), match.status)
                .filter(String::isNotBlank).joinToString(" · "), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (match.teams.size == 2) Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(match.teams.getOrNull(0), Modifier.weight(1f), onTeamClick)
                // Wrap the score instead of giving it a fixed weight: a basketball score ("122 : 96")
                // is twice as wide as a football one, and a 1.2 share cut the second half off.
                Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(match.startMomentLabel(), fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    liveScore?.currentMap?.let {
                        Text(it, modifier = Modifier.background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(overallMatchScore(match) ?: "VS", fontSize = 36.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                    liveScore?.currentScore?.let {
                        Text(buildAnnotatedString {
                            append("当前比分 ")
                            withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(it) }
                        }, fontSize = 12.sp, textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                HeroTeam(match.teams.getOrNull(1), Modifier.weight(1f), onTeamClick)
            } else {
                Text(match.teams.joinToString(" · ") { it.name }.ifBlank { match.esport.title },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), fontSize = 20.sp,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroTeam(team: Team?, modifier: Modifier, onTeamClick: ((Team) -> Unit)? = null) {
    // Crest and name share one tap target: a 44dp circle alone is easy to miss on a phone, and
    // the pressed tint is the only feedback the card can show before the sheet slides in.
    // A widget or notification launch rebuilds the roster without team ids, so the name is
    // enough to identify the side being tapped.
    val target = team?.takeIf { onTeamClick != null && (it.id.isNotBlank() || it.name.isNotBlank()) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (target != null) {
                    Modifier
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            onTeamClick?.invoke(target)
                        }
                        .background(if (pressed) MiuixTheme.colorScheme.surfaceVariant else Color.Transparent)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (team != null) TeamLogo(team, 44.dp)
        Text(team?.name ?: "待定", fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 比分区上方那行时间：未开始的场次也要有，否则中间只剩一个 VS，看不出什么时候打。 */
private fun MatchSummary.startMomentLabel(): String {
    if (startTimeMillis <= 0L) return startTimeLabel.ifBlank { "时间待定" }
    return relativeDateTimeLabel(startTimeMillis)
}

internal fun overallMatchScore(match: MatchSummary): String? {
    if (match.teams.size != 2) return null
    val bigScores = match.teams.map { it.bigScore?.takeIf(String::isNotBlank) }
    val scores = bigScores.takeIf { values -> values.all { it != null } }
        ?: match.teams.map { it.score?.takeIf(String::isNotBlank) }
    return scores.takeIf { values -> values.all { it != null } }?.joinToString(" : ") { it.orEmpty() }
}
