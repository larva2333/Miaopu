package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.Schedule
import dev.kiritoxd.miaopu.data.searchSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun EventsPageContent(
    viewModel: MiaopuViewModel,
    esport: Esport,
    innerPadding: PaddingValues,
    searchQuery: String,
    focusRequest: Int,
    onFocusHandled: (Int) -> Unit,
) {
    val bottomPadding = innerPadding.calculateBottomPadding()
    val state = viewModel.scheduleStateFor(esport)
    val incoming = (state as? LoadState.Ready)?.value
    var displayed by remember(esport) { mutableStateOf(viewModel.cachedScheduleFor(esport)) }
    val schedule = displayed ?: incoming
    // Background polling must stay invisible: refreshed data is applied quietly, with no toast.
    LaunchedEffect(incoming, esport) {
        if (incoming != null && incoming != displayed) displayed = incoming
    }
    val retry = { viewModel.refreshScheduleFor(esport) }
    Box(Modifier.fillMaxSize()) {
        if (schedule == null) {
            when (state) {
                is LoadState.Failed -> ErrorPane(state.message, state.retryable, retry,
                    Modifier.fillMaxSize().padding(bottom = bottomPadding))
                else -> LoadingPane("正在同步${esport.title}赛程", Modifier.fillMaxSize().padding(bottom = bottomPadding))
            }
        } else {
            if (searchQuery.isBlank()) {
                EventsContent(
                    viewModel,
                    schedule,
                    esport,
                    bottomPadding,
                    focusRequest,
                    onFocusHandled,
                )
            } else {
                val result by produceState<Triple<Schedule, String, Schedule>?>(null, schedule, searchQuery) {
                    delay(120)
                    value = Triple(schedule, searchQuery, withContext(Dispatchers.Default) {
                        schedule.searchSchedule(searchQuery) { ensureActive() }
                    })
                }
                val current = result?.takeIf { it.first === schedule && it.second == searchQuery }
                if (current == null) LoadingPane("正在搜索赛程", Modifier.fillMaxSize().padding(bottom = bottomPadding))
                else EventsScheduleSearchResults(current.third, searchQuery, bottomPadding, viewModel::openMatch)
            }
            // A failed background refresh keeps the previous schedule on screen with no banner or
            // button: only the explicit refresh button may report failure (through a toast).
        }
    }
}
