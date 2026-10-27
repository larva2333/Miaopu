package dev.kiritoxd.miaopu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.ScheduleDay
import dev.kiritoxd.miaopu.data.Team
import dev.kiritoxd.miaopu.data.isLive
import dev.kiritoxd.miaopu.data.isTerminal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ScrollBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun ScheduleDateScrollBar(
    state: LazyListState,
    date: String,
    trackPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val adapter = rememberScrollBarAdapter(state)
    val scrollScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val trackHeight = (
            maxHeight - trackPadding.calculateTopPadding() - trackPadding.calculateBottomPadding()
            ).coerceAtLeast(0.dp)
        // Scroll offsets change on every frame. Read them only while the date bubble is visible,
        // otherwise the full schedule subtree would recompose throughout an ordinary fling.
        val bubbleOffset = if (isDragging) {
            val visibleFraction = if (adapter.contentSize <= 0.0) {
                1f
            } else {
                (adapter.viewportSize / adapter.contentSize).toFloat().coerceIn(0f, 1f)
            }
            val thumbHeight = (trackHeight * visibleFraction)
                .coerceAtLeast(ScrollBarDefaults.ThumbMinLength)
                .coerceAtMost(trackHeight)
            val maxScrollOffset = (adapter.contentSize - adapter.viewportSize).coerceAtLeast(0.0)
            val scrollFraction = if (maxScrollOffset == 0.0) {
                0f
            } else {
                (adapter.scrollOffset / maxScrollOffset).toFloat().coerceIn(0f, 1f)
            }
            val bubbleHeight = 40.dp
            val thumbOffset =
                trackPadding.calculateTopPadding() + (trackHeight - thumbHeight) * scrollFraction
            (thumbOffset + thumbHeight / 2 - bubbleHeight / 2).coerceIn(
                0.dp,
                (maxHeight - bubbleHeight).coerceAtLeast(0.dp),
            )
        } else {
            0.dp
        }

        VerticalScrollBar(
            adapter = adapter,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            thumbWidth = if (isDragging) ScrollBarDefaults.DragThumbWidth else ScrollBarDefaults.ThumbWidth,
            trackPadding = trackPadding,
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ScrollBarDefaults.TouchTargetWidth)
                .pointerInput(adapter, trackPadding) {
                    var scrollJob: Job? = null
                    val topPaddingPx = trackPadding.calculateTopPadding().toPx()
                    val bottomPaddingPx = trackPadding.calculateBottomPadding().toPx()
                    val minThumbSizePx = ScrollBarDefaults.ThumbMinLength.toPx()

                    fun scrollToPointer(pointerY: Float) {
                        val trackSize = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(0f)
                        val maxScrollOffset = (adapter.contentSize - adapter.viewportSize).coerceAtLeast(0.0)
                        if (trackSize <= 0f || maxScrollOffset <= 0.0) return
                        val visibleFraction = (adapter.viewportSize / adapter.contentSize)
                            .toFloat()
                            .coerceIn(0f, 1f)
                        val thumbSize = (trackSize * visibleFraction)
                            .coerceAtLeast(minThumbSizePx)
                            .coerceAtMost(trackSize)
                        val dragRange = (trackSize - thumbSize).coerceAtLeast(0f)
                        val thumbPosition = (pointerY - topPaddingPx - thumbSize / 2)
                            .coerceIn(0f, dragRange)
                        val fraction = if (dragRange == 0f) 0f else thumbPosition / dragRange
                        scrollJob?.cancel()
                        scrollJob = scrollScope.launch {
                            adapter.scrollTo(fraction * maxScrollOffset)
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        try {
                            down.consume()
                            scrollToPointer(down.position.y)
                            var pointerPressed = true
                            while (pointerPressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change?.pressed == true) {
                                    scrollToPointer(change.position.y)
                                    change.consume()
                                }
                                pointerPressed = change?.pressed == true
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                }
                .semantics {
                    contentDescription = "赛程日期滚动条，当前 $date"
                },
        )

        AnimatedVisibility(
            visible = isDragging && date.isNotBlank(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = bubbleOffset),
        ) {
            Card(
                modifier = Modifier
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "定位到 $date"
                    },
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 14.dp,
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
            ) {
                Text(
                    text = date,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun ScheduleDayBand(day: ScheduleDay, isFocused: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MiuixTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = day.label.ifBlank { day.date },
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${day.matches.size} 场",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun HupuScheduleMatchCard(match: MatchSummary, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    // Live matches get a tinted card so they read first; finished ones recede to grey.
    val finished = match.isTerminal
    val live = match.isLive
    val mutedColor = scheme.onSurfaceVariantSummary
    // A flat page-coloured fill made the card melt into the background, so keep it one step
    // darker than the page and add a hairline ring - recessed, but the block stays readable.
    val finishedFill = lerp(scheme.surface, scheme.onSurface, 0.05f)
    val finishedBorder = lerp(scheme.surface, scheme.onSurface, 0.10f)
    val liveFill = lerp(scheme.surface, scheme.primary, 0.14f)
    val cardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (finished) {
                    Modifier.border(width = 1.dp, color = finishedBorder, shape = cardShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    contentDescription = "查看 ${match.name.ifBlank { match.teams.joinToString(" 对 ") { it.name } }}"
                },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            cornerRadius = 16.dp,
            colors = when {
                live -> CardDefaults.defaultColors(color = liveFill, contentColor = scheme.onSurface)
                finished -> CardDefaults.defaultColors(color = finishedFill, contentColor = mutedColor)
                else -> CardDefaults.defaultColors()
            },
            onClick = onClick,
            pressFeedbackType = PressFeedbackType.Sink,
            showIndication = true,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(76.dp)) {
                    Text(
                        text = match.startTimeLabel.ifBlank { "待定" },
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = if (finished) mutedColor else Color.Unspecified,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = match.introduction.ifBlank { match.name },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.footnote2,
                        color = mutedColor,
                    )
                }

                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (match.teams.isEmpty()) {
                        Text(
                            text = match.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = if (finished) mutedColor else Color.Unspecified,
                        )
                    } else {
                        val visibleTeams = match.teams.take(2)
                        val hasWinner = visibleTeams.any { it.winner }
                        visibleTeams.forEach { team ->
                            ScheduleTeamLine(
                                team = team,
                                isLoser = hasWinner && !team.winner,
                                finished = finished,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(54.dp)
                        .background(mutedColor.copy(alpha = if (finished) 0.12f else 0.22f)),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.width(70.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = match.status.ifBlank { "待定" },
                        maxLines = 1,
                        style = MiuixTheme.textStyles.body2,
                        color = statusColor(match),
                        fontWeight = if (match.isLive) FontWeight.Bold else FontWeight.Normal,
                    )
                    match.scoreCountText?.takeIf { it.isNotBlank() }?.let { scoreCount ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = scoreCount,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.footnote2,
                            color = mutedColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTeamLine(team: Team, isLoser: Boolean, finished: Boolean) {
    val scheme = MiuixTheme.colorScheme
    // Score is the only saturated element: winner in blue, loser in red, no bold type scale.
    val scoreColor = when {
        team.winner -> scheme.primary
        isLoser -> scheme.error
        else -> scheme.onSurfaceVariantSummary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(team = team, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = team.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Normal,
            color = if (finished) scheme.onSurfaceVariantSummary else Color.Unspecified,
        )
        Text(
            text = team.score ?: "—",
            modifier = Modifier.width(36.dp),
            style = MiuixTheme.textStyles.body1,
            color = scoreColor,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            // Centre within the fixed column: right-aligning makes narrow glyphs like "1" sit
            // visibly right of wider ones like "0" on the row above.
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun statusColor(match: MatchSummary): Color = when {
    // Same blue as the selected sub-tab, so "进行中" reads as the app's accent rather than an alert.
    match.isLive -> MiuixTheme.colorScheme.primary
    match.isTerminal -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    else -> MiuixTheme.colorScheme.onSurface
}


