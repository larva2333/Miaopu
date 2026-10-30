package dev.kiritoxd.miaopu.notify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Periodic safety net behind the one-shot kickoff alarms: aggressive battery savers (MIUI and
 * friends) can swallow the kickoff broadcast or drop the alarm entirely, so every 15 minutes
 * this sweep re-posts reminders for subscribed matches that kicked off but were never announced.
 */
class MatchAlertFallbackService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        MatchAlerts.postMissedStarts(this)
        MatchAlerts.reschedule(this, force = true)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        internal const val JOB_ID = 27103

        private const val PERIOD_MS = 15L * 60 * 1000

        fun enqueue(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, MatchAlertFallbackService::class.java))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build()
            scheduler.schedule(info)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
