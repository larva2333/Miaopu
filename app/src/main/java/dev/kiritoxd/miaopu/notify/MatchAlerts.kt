package dev.kiritoxd.miaopu.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.kiritoxd.miaopu.data.EsportSubscriptionStore
import dev.kiritoxd.miaopu.widget.WidgetMatch
import dev.kiritoxd.miaopu.widget.WidgetScheduleStore
import dev.kiritoxd.miaopu.widget.clockLabel

/**
 * Schedules one alarm per upcoming match of the subscribed esports. Alarms are one-shot: the
 * receiver posts the notification and asks for the next round, so a cancelled or finished match
 * never leaves a stale alarm behind.
 */
internal object MatchAlerts {
    const val ACTION_MATCH_ALERT = "dev.kiritoxd.miaopu.MATCH_ALERT"
    const val CHANNEL_ID = "match_start"

    private const val RESCHEDULE_THROTTLE_MS = 5_000L

    @Volatile private var lastScheduledAt = 0L

    fun isEnabled(context: Context): Boolean = MatchAlertSettings(context).enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        MatchAlertSettings(app).enabled = enabled
        if (enabled) ensureChannel(app)
        reschedule(app, force = true)
    }

    /** Recomputes the alarm set from the cached schedule; safe to call on every refresh. */
    fun reschedule(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastScheduledAt < RESCHEDULE_THROTTLE_MS) return
        lastScheduledAt = now

        val settings = MatchAlertSettings(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        cancelAll(app, settings, alarmManager)
        if (!settings.enabled || alarmManager == null) return
        ensureChannel(app)

        val upcoming = upcomingMatches(app, now)
        if (upcoming.isEmpty()) return
        val live = upcoming.mapTo(hashSetOf()) { it.identity }
        val notified = settings.notified().intersect(live)

        val scheduled = linkedSetOf<Int>()
        upcoming.forEach { match ->
            if (match.identity in notified || match.startsAt <= now) return@forEach
            val requestCode = match.identity.hashCode()
            val pendingIntent = alarmPendingIntent(app, match, requestCode)
            // setAndAllowWhileIdle wakes from Doze without needing the exact-alarm permission.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, match.startsAt, pendingIntent)
            scheduled += requestCode
        }
        settings.saveScheduled(scheduled)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "开赛提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "订阅的赛事开始时提醒"
            },
        )
    }

    private fun cancelAll(context: Context, settings: MatchAlertSettings, alarmManager: AlarmManager?) {
        if (alarmManager != null) {
            settings.scheduled().forEach { code ->
                val intent = Intent(context, MatchAlertReceiver::class.java).apply {
                    action = ACTION_MATCH_ALERT
                    data = Uri.parse("miaopu://match-alert/$code")
                }
                alarmManager.cancel(
                    PendingIntent.getBroadcast(
                        context, code, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
        settings.saveScheduled(emptySet())
    }

    private fun alarmPendingIntent(context: Context, match: WidgetMatch, requestCode: Int): PendingIntent {
        val intent = Intent(context, MatchAlertReceiver::class.java).apply {
            action = ACTION_MATCH_ALERT
            data = Uri.parse("miaopu://match-alert/$requestCode")
            putExtra(EXTRA_IDENTITY, match.identity)
            putExtra(EXTRA_BUSINESS_ID, match.businessId)
            putExtra(EXTRA_MATCH_ID, match.id)
            putExtra(EXTRA_TITLE, match.alertTitle())
            putExtra(EXTRA_BODY, match.alertBody())
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun upcomingMatches(context: Context, now: Long): List<WidgetMatch> {
        val store = WidgetScheduleStore(context)
        return upcomingAlertMatches(
            EsportSubscriptionStore(context).subscriptions().flatMap { esport -> store.snapshot(esport).matches },
            now,
        )
    }

    const val EXTRA_IDENTITY = "match_identity"
    const val EXTRA_BUSINESS_ID = "match_business_id"
    const val EXTRA_MATCH_ID = "match_id"
    const val EXTRA_TITLE = "match_title"
    const val EXTRA_BODY = "match_body"
}

/** "BLG vs T1" — falls back to the match name when team data is missing. */
internal fun WidgetMatch.alertTitle(): String = when {
    teams.size >= 2 -> "${teams[0].name} vs ${teams[1].name}"
    else -> name.ifBlank { "比赛即将开始" }
}

/**
 * Upcoming kickoffs worth an alarm: subscribed esports only, deduplicated, soonest first.
 * Finished and already-started matches are skipped, as are ids the catalog no longer knows.
 */
internal fun upcomingAlertMatches(
    matches: List<WidgetMatch>,
    now: Long,
    window: Long = ALERT_WINDOW_MS,
    limit: Int = MAX_ALARMS,
): List<WidgetMatch> = matches
    .filter { dev.kiritoxd.miaopu.data.EsportCatalog.byBusinessId(it.businessId) != null }
    .distinctBy(WidgetMatch::identity)
    .filter { it.startsAt in now..(now + window) && !it.terminal }
    .sortedBy(WidgetMatch::startsAt)
    .take(limit.coerceAtLeast(0))

private const val ALERT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
private const val MAX_ALARMS = 12

/** "英雄联盟 · LPL · 20:00 开赛" */
internal fun WidgetMatch.alertBody(): String {
    val parts = buildList {
        sportLabel.takeIf(String::isNotBlank)?.let(::add)
        competition.takeIf(String::isNotBlank)?.let(::add)
        add("${clockLabel()} 开赛")
    }
    return parts.joinToString(" · ")
}
