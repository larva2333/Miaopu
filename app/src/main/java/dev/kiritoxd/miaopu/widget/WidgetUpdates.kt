package dev.kiritoxd.miaopu.widget

import android.content.Context
import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object WidgetUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queue: WidgetRenderQueue? = null

    @Synchronized
    private fun enqueueRender(context: Context) {
        val app = context.applicationContext
        // Rebuild a dead consumer: otherwise every later refresh is silently sent into a channel
        // nobody reads, and only re-adding the widget (which renders directly) shows fresh data.
        val worker = queue?.takeIf { it.isActive } ?: WidgetRenderQueue(scope) {
            try {
                WidgetRenderer.renderAll(app)
                loadCrests(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MiaopuWidget", "Widget update did not complete", error)
            }
        }.also { queue = it }
        worker.request()
    }

    /**
     * Second pass that downloads any crest the card still lacks.
     *
     * It runs on this process-wide scope rather than the caller's, because a manual refresh does
     * its work inside a broadcast receiver with a hard [WidgetDataRefresh] budget: sharing that
     * budget cancelled the download whenever the schedule fetch used it up, which left every team
     * that newly appeared on the card showing its letter placeholder indefinitely.
     */
    fun requestCrests(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                loadCrests(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MiaopuWidget", "Crest update did not complete", error)
            }
        }
    }

    suspend fun loadCrests(context: Context) {
        val state = WidgetScheduleStore(context).state(System.currentTimeMillis())
        val matches = WidgetRenderer.matchesForLogos(context, state)
        if (WidgetLogoStore(context).load(matches)) WidgetRenderer.renderAll(context)
    }

    suspend fun publish(context: Context, sport: Esport, schedule: Schedule?, startedAt: Long) = withContext(Dispatchers.IO) {
        WidgetScheduleStore(context).record(sport, schedule, startedAt)
        if (WidgetRenderer.ids(context).isNotEmpty()) enqueueRender(context)
    }

    suspend fun subscriptionsChanged(context: Context) {
        enqueueRender(context)
        WidgetRefreshService.enqueue(context)
    }
}
