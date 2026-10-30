package dev.kiritoxd.miaopu.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.kiritoxd.miaopu.MainActivity
import dev.kiritoxd.miaopu.R
import dev.kiritoxd.miaopu.data.EsportSubscriptionStore
import dev.kiritoxd.miaopu.widget.WidgetIntents
import dev.kiritoxd.miaopu.widget.WidgetScheduleStore

/**
 * Fires at kickoff for one match, posts the reminder, then asks for the next round of alarms.
 * Also re-arms everything after a reboot, since alarms do not survive one.
 */
class MatchAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                MatchAlerts.reschedule(app, force = true)
                if (MatchAlerts.isEnabled(app)) MatchAlertFallbackService.enqueue(app)
                return
            }
            MatchAlerts.ACTION_MATCH_ALERT -> Unit
            else -> return
        }

        val settings = MatchAlertSettings(app)
        if (!settings.enabled) return
        val identity = intent.getStringExtra(MatchAlerts.EXTRA_IDENTITY) ?: return
        if (identity in settings.notified()) return

        show(
            context = app,
            identity = identity,
            title = intent.getStringExtra(MatchAlerts.EXTRA_TITLE)?.takeIf(String::isNotBlank)
                ?: "比赛即将开始",
            body = intent.getStringExtra(MatchAlerts.EXTRA_BODY).orEmpty(),
            businessId = intent.getStringExtra(MatchAlerts.EXTRA_BUSINESS_ID),
            matchId = intent.getStringExtra(MatchAlerts.EXTRA_MATCH_ID),
        )
        settings.rememberNotified(identity)
        MatchAlerts.reschedule(app, force = true)
    }

    companion object {
        /** adb 调试用：`am start -a dev.kiritoxd.miaopu.action.PREVIEW_ALERT` */
        const val ACTION_PREVIEW = "dev.kiritoxd.miaopu.action.PREVIEW_ALERT"

        /** Posts one reminder right now, exactly as an alarm would. */
        fun show(
            context: Context,
            identity: String,
            title: String,
            body: String,
            businessId: String? = null,
            matchId: String? = null,
        ) {
            MatchAlerts.ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val open = Intent(context, MainActivity::class.java).apply {
                action = WidgetIntents.OPEN
                data = Uri.parse("miaopu://notify/${Uri.encode(identity)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(WidgetIntents.BUSINESS_ID, businessId)
                putExtra(WidgetIntents.MATCH_ID, matchId)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                identity.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, MatchAlerts.CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_notification_match)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            try {
                manager.notify(identity.hashCode(), notification)
            } catch (_: SecurityException) {
                // Notification permission was revoked after the switch was turned on.
            }
        }

        /**
         * Preview uses the soonest cached kickoff so the example looks like the real thing;
         * with an empty cache it falls back to a sample so the notification still renders.
         */
        fun preview(context: Context) {
            val now = System.currentTimeMillis()
            val store = WidgetScheduleStore(context)
            val sample = EsportSubscriptionStore(context).subscriptions()
                .flatMap { esport -> store.snapshot(esport).matches }
                .filter { !it.terminal && it.startsAt > now }
                .minByOrNull { it.startsAt }

            show(
                context = context,
                identity = sample?.identity ?: "preview/sample",
                title = sample?.alertTitle() ?: "BLG vs T1",
                body = sample?.alertBody() ?: "英雄联盟 · LPL · 20:00 开赛",
                businessId = sample?.businessId,
                matchId = sample?.id,
            )
        }
    }
}
