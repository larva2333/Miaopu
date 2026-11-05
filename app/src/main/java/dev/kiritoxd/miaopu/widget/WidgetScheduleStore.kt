package dev.kiritoxd.miaopu.widget

import android.content.Context
import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.EsportSubscriptionStore
import dev.kiritoxd.miaopu.data.Schedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal data class WidgetScheduleSnapshot(
    val matches: List<WidgetMatch> = emptyList(),
    val updatedAt: Long = 0,
    val attemptedAt: Long = 0,
    val failed: Boolean = false,
)

internal data class WidgetScheduleState(
    val matches: List<WidgetMatch>,
    val updatedAt: Long,
    val failed: Boolean,
    val hasFetched: Boolean,
)

internal class WidgetScheduleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("miaopu_widget_schedules", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun snapshot(esport: Esport): WidgetScheduleSnapshot = try {
        preferences.getString(esport.businessId, null)?.let {
            json.decodeFromString<WidgetScheduleSnapshot>(it)
        } ?: WidgetScheduleSnapshot()
    } catch (_: SerializationException) {
        WidgetScheduleSnapshot()
    } catch (_: IllegalArgumentException) {
        WidgetScheduleSnapshot()
    }

    fun record(esport: Esport, schedule: Schedule?, startedAt: Long) = synchronized(lock) {
        val old = snapshot(esport)
        // A slower widget request must not replace a newer in-app refresh.
        if (startedAt < old.attemptedAt) return@synchronized
        val next = if (schedule == null) {
            old.copy(attemptedAt = startedAt, failed = true)
        } else {
            WidgetScheduleSnapshot(
                // Results are cached with the same lookback the card renders with, otherwise a
                // finished match would be dropped on write and never reach the widget. The
                // section budget is generous because the fixture window is a whole month.
                matches = selectWidgetScheduleMatches(
                    schedule.days.flatMap { it.matches }.map { it.toWidgetMatch() },
                    startedAt,
                    60,
                    WIDGET_RESULT_WINDOW_HOURS,
                ),
                updatedAt = System.currentTimeMillis(),
                attemptedAt = startedAt,
            )
        }
        preferences.edit().putString(esport.businessId, json.encodeToString(next)).apply()
    }

    fun state(now: Long): WidgetScheduleState {
        val snapshots = EsportSubscriptionStore(context).subscriptions().map(::snapshot)
        return WidgetScheduleState(
            // Both sections, each wide enough for the tallest card: every widget size picks its own
            // rows out of this pool ([widgetVisibleRows]), so a short card and a tall one must not
            // be served a list that was already trimmed for the other. The picker still offers
            // every candidate through [candidates].
            matches = widgetCardPool(snapshots.flatMap { it.matches }, now),
            updatedAt = snapshots.map { it.updatedAt }.filter { it > 0 }.minOrNull() ?: 0,
            failed = snapshots.any { it.failed },
            hasFetched = snapshots.any { it.attemptedAt > 0 },
        )
    }

    /** Every selectable candidate before the widget size trims the list, used by the picker. */
    fun candidates(now: Long): List<WidgetMatch> {
        val snapshots = EsportSubscriptionStore(context).subscriptions().map(::snapshot)
        return selectWidgetScheduleMatches(
            snapshots.flatMap { it.matches },
            now,
            Int.MAX_VALUE,
            WIDGET_RESULT_WINDOW_HOURS,
        )
    }

    fun find(businessId: String?, matchId: String?): WidgetMatch? =
        EsportSubscriptionStore(context).subscriptions().firstOrNull { it.businessId == businessId }
            ?.let(::snapshot)?.matches?.firstOrNull { it.id == matchId }

    private companion object { val lock = Any() }
}
