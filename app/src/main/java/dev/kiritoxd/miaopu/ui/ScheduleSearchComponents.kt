package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kiritoxd.miaopu.R
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.Schedule
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar

@Composable
internal fun EventsScheduleSearchBar(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    SearchBar(
        modifier = Modifier.fillMaxWidth(),
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                // Results filter as you type. IME "search" only dismisses the keyboard and drops
                // focus: the field stays put (tap it again to keep editing), no summary bar.
                onSearch = { text ->
                    onQueryChange(text)
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = "搜索赛事、轮次、战队或选手",
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {}
}

/** The launcher artwork (red gradient + paw) used by every loading and empty state. */
@Composable
internal fun AppIconBadge() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFF1A2A), Color(0xFFE60012)))),
        contentAlignment = Alignment.Center,
    ) {
        // The foreground asset is drawn on the 108dp adaptive-icon canvas; crop to the 66dp
        // safe zone so the paw keeps the same apparent size as the real launcher icon.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(86.dp),
        )
    }
}

@Composable
internal fun EventsScheduleSearchResults(
    schedule: Schedule,
    query: String,
    bottomPadding: Dp,
    onMatchClick: (MatchSummary) -> Unit,
) {
    if (schedule.days.isEmpty()) {
        EmptyPane(
            message = "没有找到“${query.trim()}”相关赛程",
            modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        schedule.days.forEach { day ->
            item(key = "search-day-${day.date}", contentType = "day") {
                ScheduleDayBand(day = day, isFocused = false)
            }
            itemsIndexed(
                items = day.matches,
                key = { index, match -> "search-${day.date}-${match.uniqueKey}-$index" },
                contentType = { _, _ -> "schedule-match" },
            ) { _, match ->
                HupuScheduleMatchCard(match = match, onClick = { onMatchClick(match) })
            }
        }
    }
}
