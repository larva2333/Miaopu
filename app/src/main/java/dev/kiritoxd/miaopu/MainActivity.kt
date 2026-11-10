package dev.kiritoxd.miaopu

import android.os.Bundle
import android.os.Build
import android.graphics.Color
import androidx.activity.SystemBarStyle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.kiritoxd.miaopu.notify.MatchAlertReceiver
import dev.kiritoxd.miaopu.notify.MatchAlerts
import androidx.activity.viewModels
import dev.kiritoxd.miaopu.ui.MiaopuApp
import dev.kiritoxd.miaopu.ui.MiaopuViewModel
import dev.kiritoxd.miaopu.data.EsportCatalog
import dev.kiritoxd.miaopu.widget.WidgetIntents
import dev.kiritoxd.miaopu.widget.WidgetScheduleStore

class MainActivity : ComponentActivity() {
    private val viewModel: MiaopuViewModel by viewModels()
    /** One automatic network refresh per foreground session, reset when the app is backgrounded. */
    private var refreshedThisForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A newly created ViewModel already hits the network in init and publishes to the widget
        // store; count that as this session's refresh instead of firing a second pass. A surviving
        // ViewModel (Activity recreated after a process kill) has no pending load and must refresh.
        refreshedThisForeground = viewModel.consumeInitialLoad()
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT
        if (savedInstanceState == null) handleWidgetIntent(intent)
        handlePreviewIntent(intent)
        setContent { MiaopuApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        // Returning from the background (including a tap on a widget that opens a detail page)
        // refreshes exactly once; switching pages inside this session must not fetch again.
        if (!refreshedThisForeground) {
            refreshedThisForeground = true
            viewModel.onAppOpened()
        }
        lifecycleScope.launch {
            // Alarms are rebuilt on every foreground visit so a cached schedule is enough to arm them.
            withContext(Dispatchers.IO) { MatchAlerts.reschedule(applicationContext) }
        }
    }

    override fun onStop() {
        refreshedThisForeground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
        handlePreviewIntent(intent)
    }

    /** Lets adb post a sample reminder without waiting for a kickoff. */
    private fun handlePreviewIntent(intent: Intent?) {
        if (intent?.action != MatchAlertReceiver.ACTION_PREVIEW) return
        intent.action = null
        MatchAlertReceiver.preview(applicationContext)
    }

    private fun handleWidgetIntent(intent: Intent) {
        if (intent.action != WidgetIntents.OPEN) return
        val businessId = intent.getStringExtra(WidgetIntents.BUSINESS_ID)
        val match = WidgetScheduleStore(this).find(businessId, intent.getStringExtra(WidgetIntents.MATCH_ID))
        viewModel.openWidgetDestination(match?.toModel(), EsportCatalog.byBusinessId(businessId))
    }
}
