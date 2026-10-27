package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.foundation.gestures.ScrollScope

/**
 * A short drag (15% of the page) or a gentle flick advances one page. The delegated fling receives a
 * heavily boosted velocity, lowering its effective threshold from 400dp/s to about 133dp/s so the
 * pager feels as easy to swipe as a short-video feed.
 */
@Composable
internal fun rememberMiaopuPagerFlingBehavior(state: PagerState): TargetedFlingBehavior {
    val base = PagerDefaults.flingBehavior(
        state = state,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.15f,
    )
    return rememberVelocityBoost(base, VELOCITY_BOOST)
}

@Stable
private class VelocityBoostFlingBehavior(
    private val delegate: TargetedFlingBehavior,
    private val boost: Float,
) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val scrollScope = this
        val remaining = with(delegate) {
            scrollScope.performFling(initialVelocity * boost, onRemainingDistanceUpdated)
        }
        return remaining / boost
    }
}

@Composable
private fun rememberVelocityBoost(
    delegate: TargetedFlingBehavior,
    boost: Float,
): TargetedFlingBehavior = remember(delegate, boost) {
    VelocityBoostFlingBehavior(delegate, boost)
}

private const val VELOCITY_BOOST = 3.0f
