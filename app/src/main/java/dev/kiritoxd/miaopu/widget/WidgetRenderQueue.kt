package dev.kiritoxd.miaopu.widget

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Collapse bursts, with at most one additional pass queued while a render is running. */
internal class WidgetRenderQueue(
    scope: CoroutineScope,
    windowMillis: Long = 150,
    render: suspend () -> Unit,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val consumer: Job = scope.launch {
        for (request in requests) {
            delay(windowMillis)
            while (requests.tryReceive().isSuccess) { /* use the latest persisted schedule */ }
            try {
                render()
            } catch (cancelled: CancellationException) {
                // A single cancelled pass must not kill this long-lived consumer. If the whole
                // scope is going away (process teardown), let the cancellation through.
                if (!currentCoroutineContext().isActive) throw cancelled
                Log.w("MiaopuWidget", "Widget render pass was cancelled")
            } catch (error: Exception) {
                Log.w("MiaopuWidget", "Widget render pass failed", error)
            }
        }
    }

    /** False once the consumer has died; the owner must then build a fresh queue. */
    val isActive: Boolean get() = consumer.isActive

    fun request() { requests.trySend(Unit) }
}
