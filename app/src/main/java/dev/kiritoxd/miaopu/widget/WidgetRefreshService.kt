package dev.kiritoxd.miaopu.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dev.kiritoxd.miaopu.data.EsportSubscriptionStore
import dev.kiritoxd.miaopu.data.HupuAdapter
import dev.kiritoxd.miaopu.data.HupuCookieSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Scheduled refreshes run here so JobScheduler keeps the process alive; a manual tap may also
 * fetch directly through [WidgetDataRefresh] while this job acts as the system-level fallback. */
class WidgetRefreshService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refresh: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (WidgetRenderer.ids(this).isEmpty()) return false
        requests.started(params, params.extras.getString(REQUEST_ID).orEmpty())
        refresh = scope.launch {
            var completed = false
            var failed = false
            try {
                WidgetDataRefresh.run(this@WidgetRefreshService)
                completed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MiaopuWidget", "Widget refresh did not complete", error)
                failed = true
                completed = true
            } finally {
                requests.finishing(params, failed)
                // Read current state: a stopped run must not clear a newer run's feedback.
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        try {
                            WidgetRenderer.renderAll(this@WidgetRefreshService)
                        } catch (error: Exception) {
                            android.util.Log.w("MiaopuWidget", "Could not render refresh result", error)
                        }
                    }
                } finally {
                    if (requests.finished(params, failed = failed)) {
                        refresh = null
                        // Keep JobScheduler's process protection until the result is rendered.
                        if (completed && currentCoroutineContext().isActive) jobFinished(params, false)
                    }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val retry = WidgetRenderer.ids(this).isNotEmpty()
        // Stop callbacks arrive through Binder with a new JobParameters instance.
        if (!requests.stopped(params.extras.getString(REQUEST_ID).orEmpty(), reschedule = retry)) return false
        refresh?.cancel()
        refresh = null
        return retry
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal const val JOB_ID = 27102
        private const val REQUEST_ID = "widget_refresh_request"
        private const val MANUAL = "widget_refresh_manual"
        private val requests = WidgetRefreshRequests()

        internal fun enqueue(context: Context, manual: Boolean = false) {
            if (WidgetRenderer.ids(context).isEmpty()) return
            val scheduler = context.getSystemService(JobScheduler::class.java)
            requests.enqueue(manual, Build.VERSION.SDK_INT >= 31, { pending(scheduler) }) { request, expedited ->
                try {
                    val builder = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetRefreshService::class.java))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                        // A queued refresh must survive a reboot; otherwise a launcher that
                        // throttles jobs can leave the widget stale until the app is opened.
                        .setPersisted(true)
                        .setExtras(PersistableBundle().apply {
                            putString(REQUEST_ID, request.id)
                            putBoolean(MANUAL, request.manual)
                        })
                    if (expedited && Build.VERSION.SDK_INT >= 31) builder.setExpedited(true)
                    else if (manual && Build.VERSION.SDK_INT in 28..30) {
                        @Suppress("DEPRECATION")
                        builder.setImportantWhileForeground(true)
                    }
                    val accepted = scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
                    if (!accepted) android.util.Log.w("MiaopuWidget", "Refresh scheduling rejected (manual=$manual, expedited=$expedited)")
                    accepted
                } catch (error: RuntimeException) {
                    android.util.Log.w("MiaopuWidget", "Could not schedule widget refresh", error)
                    false
                }
            }
        }

        internal fun status(context: Context): WidgetRefreshStatus = requests.status {
            pending(context.getSystemService(JobScheduler::class.java))
        }

        private fun pending(scheduler: JobScheduler): WidgetRefreshRequest? =
            scheduler.getPendingJob(JOB_ID)?.extras?.let {
                WidgetRefreshRequest(it.getString(REQUEST_ID).orEmpty(), it.getBoolean(MANUAL))
            }
    }
}

/** The network fetch itself, shared by the scheduled job and a direct manual refresh tap. */
internal object WidgetDataRefresh {
    /**
     * [timeoutMillis] bounds work started from a broadcast receiver, whose goAsync window is
     * short. A timeout leaves whatever was already written in the store and lets the scheduled
     * job finish the rest.
     */
    suspend fun run(context: Context, timeoutMillis: Long? = null) {
        val app = context.applicationContext
        if (WidgetRenderer.ids(app).isEmpty()) return
        if (timeoutMillis == null) fetch(app) else withTimeoutOrNull(timeoutMillis) { fetch(app) }
        // Crests are a cosmetic second pass and must not share the receiver's data budget: a
        // cancelled download left every newly listed team on a letter placeholder with no log.
        WidgetUpdates.requestCrests(app)
    }

    private suspend fun fetch(app: Context) {
        val adapter = HupuAdapter(HupuCookieSession(app))
        // Repaint the cached card immediately, then replace it once the network responds.
        WidgetRenderer.renderAll(app)
        val subscriptions = EsportSubscriptionStore(app).subscriptions()
        val store = WidgetScheduleStore(app)
        val concurrency = Semaphore(3)
        coroutineScope {
            subscriptions.map { sport ->
                async(Dispatchers.IO) {
                    concurrency.withPermit {
                        val startedAt = System.currentTimeMillis()
                        val schedule = try {
                            val result = adapter.getSchedule(sport)
                            if (result.data == null) {
                                android.util.Log.w("MiaopuWidget", "Schedule unavailable for ${sport.businessId}: ${result.status}, HTTP ${result.error?.httpCode}")
                            }
                            result.data
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w("MiaopuWidget", "Schedule unavailable for ${sport.businessId}", error)
                            null
                        }
                        store.record(sport, schedule, startedAt)
                    }
                }
            }.awaitAll()
        }
        WidgetRenderer.renderAll(app)
    }
}
