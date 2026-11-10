package dev.kiritoxd.miaopu.widget

import java.util.UUID

internal enum class WidgetRefreshStatus { IDLE, WAITING, RUNNING, FAILED }

internal data class WidgetRefreshRequest(val id: String, val manual: Boolean)

/** Distinguish queued work from a running fetch, and promote only queued background work. */
internal class WidgetRefreshRequests {
    private var active: Any? = null
    private var activeId: String? = null
    private var activeSince = 0L
    private var completedId: String? = null
    private var queuedSince = 0L
    private var failed = false
    private var resultStatus: WidgetRefreshStatus? = null

    @Synchronized
    fun enqueue(
        manual: Boolean,
        supportsExpedited: Boolean,
        pending: () -> WidgetRefreshRequest?,
        schedule: (WidgetRefreshRequest, Boolean) -> Boolean,
    ) {
        val now = System.currentTimeMillis()
        // A fetch that never reported back (process killed mid-run, OEM job throttling) would
        // otherwise block every later refresh and every 30-minute system update forever.
        if (active != null && now - activeSince >= ACTIVE_TIMEOUT_MILLIS) {
            active = null
            activeId = null
            resultStatus = null
        }
        if (active != null) return
        val queued = pending()?.takeUnless { it.id == completedId }
        // A job that stays queued for minutes on a restricted launcher will never start by
        // itself. Let a later tap or periodic broadcast replace it instead of being swallowed.
        val stale = queued != null && queuedSince > 0L && now - queuedSince >= QUEUE_TIMEOUT_MILLIS
        if (queued != null && !stale && (!manual || queued.manual)) {
            failed = false
            return
        }
        val request = WidgetRefreshRequest(UUID.randomUUID().toString(), manual)
        val accepted = (manual && supportsExpedited && schedule(request, true)) || schedule(request, false)
        failed = !accepted
        queuedSince = if (accepted) now else 0L
    }

    @Synchronized
    fun started(token: Any, requestId: String) {
        active = token
        activeId = requestId
        activeSince = System.currentTimeMillis()
        queuedSince = 0L
        failed = false
        resultStatus = null
    }

    @Synchronized
    fun finishing(token: Any, failed: Boolean) {
        if (active === token) resultStatus = if (failed) WidgetRefreshStatus.FAILED else WidgetRefreshStatus.IDLE
    }

    @Synchronized
    fun stopped(requestId: String, reschedule: Boolean): Boolean {
        val token = active ?: return false
        if (activeId != requestId) return false
        return finished(token, reschedule = reschedule)
    }

    @Synchronized
    fun finished(token: Any, failed: Boolean = false, reschedule: Boolean = false): Boolean {
        if (active !== token) return false
        // JobScheduler may briefly keep a finished job visible after jobFinished().
        completedId = if (reschedule) null else activeId
        active = null
        activeId = null
        queuedSince = 0L
        resultStatus = null
        this.failed = failed
        return true
    }

    @Synchronized
    fun status(pending: () -> WidgetRefreshRequest?): WidgetRefreshStatus = when {
        active != null -> resultStatus ?: WidgetRefreshStatus.RUNNING
        failed -> WidgetRefreshStatus.FAILED
        pending()?.let { it.id != completedId } == true -> WidgetRefreshStatus.WAITING
        else -> WidgetRefreshStatus.IDLE
    }

    private companion object {
        const val ACTIVE_TIMEOUT_MILLIS = 10 * 60_000L
        const val QUEUE_TIMEOUT_MILLIS = 3 * 60_000L
    }
}
