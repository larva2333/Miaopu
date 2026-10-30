package dev.kiritoxd.miaopu.widget

import android.content.Context

/**
 * Remembers which match a single-match widget instance is pinned to, keyed by its launcher id.
 *
 * Each widget id owns an independent entry, so the widget can be added several times and every
 * copy keeps a different match. No entry means "follow the default priority" (live → upcoming →
 * latest result), which is what a freshly added widget does.
 */
internal class MatchWidgetConfigStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("miaopu_match_widget_config", Context.MODE_PRIVATE)

    fun selectedIdentity(appWidgetId: Int): String? =
        preferences.getString(key(appWidgetId), null)?.takeIf { it.isNotBlank() }

    fun select(appWidgetId: Int, identity: String?) {
        val editor = preferences.edit()
        if (identity.isNullOrBlank()) editor.remove(key(appWidgetId))
        else editor.putString(key(appWidgetId), identity)
        editor.apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "match_$appWidgetId"
}

/**
 * Resolve the match a widget should show. Prefers the pinned match, then the first candidate when
 * nothing is pinned. The pinned match is looked up in the widget's visible candidates first and
 * only then in the full schedule, so a taller list trim never hides an explicit choice.
 */
internal fun resolvePinnedMatch(
    context: Context,
    appWidgetId: Int,
    matches: List<WidgetMatch>,
    now: Long,
): WidgetMatch? {
    val identity = MatchWidgetConfigStore(context).selectedIdentity(appWidgetId) ?: return null
    return matches.firstOrNull { it.identity == identity }
        ?: WidgetScheduleStore(context).candidates(now).firstOrNull { it.identity == identity }
}
