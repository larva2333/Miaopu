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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MatchHero(match: MatchSummary, liveScore: MatchLiveScore? = null, onTeamClick: ((Team) -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(match.introduction.ifBlank { match.name }, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(listOf(match.name.takeUnless { it == match.introduction }.orEmpty(), match.status)
                .filter(String::isNotBlank).joinToString(" · "), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (match.teams.size == 2) Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(match.teams.getOrNull(0), Modifier.weight(1f), onTeamClick)
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
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
    val target = team?.takeIf { it.id.isNotBlank() && onTeamClick != null }
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

internal fun overallMatchScore(match: MatchSummary): String? {
    if (match.teams.size != 2) return null
    val bigScores = match.teams.map { it.bigScore?.takeIf(String::isNotBlank) }
    val scores = bigScores.takeIf { values -> values.all { it != null } }
        ?: match.teams.map { it.score?.takeIf(String::isNotBlank) }
    return scores.takeIf { values -> values.all { it != null } }?.joinToString(" : ") { it.orEmpty() }
}
