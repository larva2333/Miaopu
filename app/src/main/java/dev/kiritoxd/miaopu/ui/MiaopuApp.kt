package dev.kiritoxd.miaopu.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun MiaopuApp(viewModel: MiaopuViewModel) {
    // A launch from a widget or notification pre-seeds the match detail on top, so it renders
    // immediately instead of animating over a freshly composed schedule list.
    val backStack = rememberNavBackStack(*viewModel.initialBackStack().toTypedArray())
    val navigator = remember(backStack) { MiaopuNavigator(backStack) }
    DisposableEffect(viewModel, navigator) {
        viewModel.attachNavigator(navigator)
        onDispose { viewModel.detachNavigator(navigator) }
    }
    val themeController = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = themeController) {
        Box(Modifier.fillMaxSize()) {
            // Transient feedback only: a pinning bottom card had to be dismissed by hand and could
            // sit on screen forever, so every ViewModel message now surfaces as a system toast.
            val context = LocalContext.current
            LaunchedEffect(viewModel.message) {
                val text = viewModel.message ?: return@LaunchedEffect
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                viewModel.dismissMessage()
            }
            val cornerRadius = rememberNavSystemCornerRadius()
            val backdropColor = MiuixTheme.colorScheme.surface
            val navEffects = remember(cornerRadius, backdropColor) {
                NavDisplayEffects(
                    enableCornerClip = false,
                    cornerClipRadius = cornerRadius,
                    backdropColor = backdropColor,
                )
            }
            val navigationMotion = rememberMiaopuNavigationMotion(cornerRadius)
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = viewModel::goBack,
                transition = navigationMotion,
                effects = navEffects,
            ) {
                entry<AppScreen.Schedule>(contentKey = { it.navigationContentKey }) { ScheduleScreen(viewModel) }
                entry<AppScreen.Subscriptions>(contentKey = { it.navigationContentKey }) { SubscriptionScreen(viewModel) }
                entry<AppScreen.Ratings>(contentKey = { it.navigationContentKey }) { screen ->
                    RatingsScreen(viewModel, screen.match.toModel())
                }
                entry<AppScreen.Stage>(contentKey = { it.navigationContentKey }) { screen ->
                    MatchStageScreen(
                        viewModel = viewModel,
                        match = screen.match.toModel(),
                        stage = screen.stage.toModel(),
                        stageNumber = screen.stageNumber,
                        showStageNumber = screen.returnToStagePicker,
                    )
                }
                entry<AppScreen.Comments>(contentKey = { it.navigationContentKey }) { screen ->
                    CommentsScreen(viewModel, screen.target.toModel())
                }
                entry<AppScreen.Web>(contentKey = { it.navigationContentKey }) { screen ->
                    HupuWebScreen(
                        title = screen.title,
                        url = screen.url,
                        login = screen.login,
                        onBack = viewModel::goBack,
                        onLoginDetected = viewModel::finishLogin,
                    )
                }
            }
            GestureBackReleaseGuard()
        }
    }
}

/**
 * 边缘手势返回时，系统一旦判定「划够了」就立刻提交返回，快速一甩的那一刻手指其实还在屏幕上，
 * 页面就先退了。应用侧拿不到「手指抬起」这个事件（系统手势区的事件不会派发给窗口），
 * 所以只能把提前提交的那一次先压住，等一个足够长的窗口再执行。
 *
 * 窗口从 [handleOnBackStarted] 起算：慢慢拖到松手才提交的，通常已经超过 [GestureSettleMs]，
 * 直接执行，松手即回；只有 100ms 内就提交的快速一甩才需要补足，让它落在松手之后。
 * 返回键不走 onBackStarted，立刻放行。
 */
@Composable
private fun GestureBackReleaseGuard() {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity) {
        val callback = object : OnBackPressedCallback(true) {
            private var gestureStartedAt = 0L
            private var fromGesture = false
            private val handler = Handler(Looper.getMainLooper())
            private var pending: Runnable? = null

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                // 返回键也会走到这里，但 swipeEdge 不是左右边缘；只有真正从屏幕边缘滑入的才算手势。
                fromGesture = backEvent.swipeEdge == BackEventCompat.EDGE_LEFT ||
                    backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT
                gestureStartedAt = SystemClock.uptimeMillis()
                // 压着的这一下还没执行用户又划了一次，说明他就是想返回，交给新的这次处理。
                pending?.let { handler.removeCallbacks(it) }
                pending = null
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) = Unit

            override fun handleOnBackCancelled() {
                fromGesture = false
            }

            override fun handleOnBackPressed() {
                if (!fromGesture) {
                    forwardNow()
                    return
                }
                fromGesture = false
                val wait = GestureSettleMs - (SystemClock.uptimeMillis() - gestureStartedAt)
                if (wait <= 0L) {
                    forwardNow()
                    return
                }
                val runnable = Runnable {
                    pending = null
                    forwardNow()
                }
                pending = runnable
                handler.postDelayed(runnable, wait)
            }

            /** 让出这一层，把返回交回给导航栈和页面、浮层自己的 BackHandler。 */
            private fun forwardNow() {
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        activity.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/**
 * 手势从起手到系统提交不足这个时长，就认为手指还在划，把返回压到这时长之后再执行。
 * 真人快速甩手大约 80~150ms 完成，取 200ms 能保证落在松手之后；慢慢拖过 200ms 的不受影响。
 */
private const val GestureSettleMs = 200L
