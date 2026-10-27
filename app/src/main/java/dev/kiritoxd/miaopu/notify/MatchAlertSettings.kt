package dev.kiritoxd.miaopu.notify

import android.content.Context

/** Persisted switch state plus the bookkeeping needed to avoid duplicate notifications. */
internal class MatchAlertSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    fun notified(): Set<String> = preferences.getStringSet(KEY_NOTIFIED, null).orEmpty()

    /** Keeps the record bounded so it cannot grow forever across seasons. */
    fun rememberNotified(identity: String) {
        val next = notified().toMutableSet()
        next += identity
        if (next.size > MAX_NOTIFIED) next.clear()
        preferences.edit().putStringSet(KEY_NOTIFIED, next.toCollection(linkedSetOf())).apply()
    }

    fun scheduled(): Set<Int> = preferences.getStringSet(KEY_SCHEDULED, null)
        .orEmpty().mapNotNull(String::toIntOrNull).toSet()

    fun saveScheduled(codes: Set<Int>) {
        preferences.edit().putStringSet(KEY_SCHEDULED, codes.mapTo(linkedSetOf()) { it.toString() }).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "miaopu_match_alerts"
        const val KEY_ENABLED = "enabled"
        const val KEY_NOTIFIED = "notified_matches"
        const val KEY_SCHEDULED = "scheduled_request_codes"
        const val MAX_NOTIFIED = 120
    }
}
