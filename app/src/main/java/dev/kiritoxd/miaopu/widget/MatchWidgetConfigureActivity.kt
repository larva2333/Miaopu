package dev.kiritoxd.miaopu.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Picks the match a single-match widget shows.
 *
 * Reached from the launcher's "edit widget" entry (long-press the placed widget) and when the
 * widget is added on launchers that support a configuration activity. The choice is stored per
 * widget id, so several single-match widgets can each show a different match.
 */
class MatchWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var confirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        appWidgetId = intent
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Start as "not added". Every real exit below flips this to RESULT_OK, so this screen can
        // never silently block the widget from being added.
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                MatchPickerScreen(
                    selected = MatchWidgetConfigStore(this@MatchWidgetConfigureActivity)
                        .selectedIdentity(appWidgetId),
                    onPick = ::confirm,
                )
            }
        }
    }

    /** Persist the choice and let the launcher add (or keep) the widget. */
    private fun confirm(identity: String?) {
        if (confirmed) return
        confirmed = true
        MatchWidgetConfigStore(this).select(appWidgetId, identity)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        lifecycleScope.launch {
            // A failure here must never leave the user stuck on the picker; the widget's own
            // onUpdate will render again once the launcher binds it.
            runCatching { WidgetRenderer.renderOne(applicationContext, appWidgetId) }
            finish()
        }
    }
}

@Composable
private fun MatchPickerScreen(selected: String?, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var matches by remember { mutableStateOf<List<WidgetMatch>?>(null) }
    LaunchedEffect(Unit) {
        matches = withContext(Dispatchers.IO) {
            WidgetScheduleStore(context.applicationContext).candidates(now)
        }
    }
    // Backing out still adds the widget with the default match instead of dropping it.
    BackHandler { onPick(null) }
    Scaffold(containerColor = MiuixTheme.colorScheme.surface) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp)) {
                Text("选择单场赛况", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "把一场比赛固定在这个小部件上。以后长按桌面小部件，选择编辑即可重新挑选。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            when (val list = matches) {
                null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "正在读取赛程…",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SelectableRow(
                            title = "自动（按优先级显示）",
                            subtitle = "进行中优先，其次最早开赛，再次最近结束",
                            chosen = selected == null,
                            onClick = { onPick(null) },
                        )
                    }
                    if (list.isEmpty()) {
                        item {
                            Text(
                                "暂无可选场次。先在喵扑里加载赛程，再回来挑选；现在也可以直接按默认添加。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(list, key = { it.identity }) { match ->
                            SelectableRow(
                                title = match.matchTitle(),
                                subtitle = "${match.sportLabel} · ${match.competition.ifBlank { match.name }} · ${match.statusLine(now)}",
                                chosen = selected == match.identity,
                                onClick = { onPick(match.identity) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, chosen: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        cornerRadius = 16.dp,
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chosen) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "已选",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun WidgetMatch.matchTitle(): String =
    teams.mapNotNull { it.name.takeIf(String::isNotBlank) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("  vs  ")
        ?: name.ifBlank { "待定" }

private fun WidgetMatch.statusLine(now: Long): String = when {
    live -> "● $status"
    terminal -> status.ifBlank { "已结束" }
    else -> dateTimeLabel(now)
}
