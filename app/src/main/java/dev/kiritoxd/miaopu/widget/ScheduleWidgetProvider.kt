package dev.kiritoxd.miaopu.widget

import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keep the direct tap inside the receiver's goAsync window; the scheduled job covers the rest. */
private const val MANUAL_FETCH_TIMEOUT_MILLIS = 8_000L

class MatchWidgetProvider : BaseScheduleWidgetProvider()
class ScheduleWidgetProvider : BaseScheduleWidgetProvider()

open class BaseScheduleWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetIntents.REFRESH) {
            // A tap must fetch data itself: on some launchers the queued job never starts.
            // The scheduled job stays as a system-protected fallback and drives the footer state.
            WidgetRefreshService.enqueue(context, manual = true)
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    WidgetDataRefresh.run(context.applicationContext, timeoutMillis = MANUAL_FETCH_TIMEOUT_MILLIS)
                } catch (cancelled: CancellationException) {
                    android.util.Log.w("MiaopuWidget", "Manual refresh was cancelled")
                } catch (error: Exception) {
                    android.util.Log.w("MiaopuWidget", "Manual refresh failed", error)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRefreshService.enqueue(context)
        render(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Drop each removed instance's pinned match so a later id reuse starts from the default.
        val store = MatchWidgetConfigStore(context)
        appWidgetIds.forEach(store::remove)
    }

    override fun onDisabled(context: Context) {
        if (WidgetRenderer.ids(context).isEmpty()) {
            context.getSystemService(JobScheduler::class.java).cancel(WidgetRefreshService.JOB_ID)
        }
    }

    private fun render(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
