package dev.kiritoxd.miaopu.ui

import android.widget.Toast
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
        }
    }
}
