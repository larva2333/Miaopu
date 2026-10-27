package dev.kiritoxd.miaopu.widget

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal enum class PinnableWidget {
    MATCH,
    SCHEDULE,
}

internal object WidgetPinner {
    /**
     * How many instances of this widget the launcher currently hosts.
     * Used to tell a real "added" from a silently swallowed request.
     */
    fun instanceCount(context: Context, widget: PinnableWidget): Int = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, providerClass(widget)))
            .size
    }.getOrDefault(0)

    fun request(context: Context, widget: PinnableWidget): Boolean {
        val provider = providerClass(widget)
        // MIUI / HyperOS Home adds the widget immediately on this private broadcast and shows no
        // confirmation dialog at all (verified on device) — so try it first on Xiaomi builds.
        if (requestMiuiInstall(context, provider)) return true
        // Everywhere else fall back to the official pin request, which does surface the
        // launcher's confirm dialog.
        return requestStandardPin(context, provider)
    }

    private fun providerClass(widget: PinnableWidget): Class<out android.appwidget.AppWidgetProvider> =
        when (widget) {
            PinnableWidget.MATCH -> MatchWidgetProvider::class.java
            PinnableWidget.SCHEDULE -> ScheduleWidgetProvider::class.java
        }

    private fun requestStandardPin(
        context: Context,
        provider: Class<out android.appwidget.AppWidgetProvider>,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val manager = AppWidgetManager.getInstance(context)
            manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(ComponentName(context, provider), null, null)
        }.getOrDefault(false)
    }

    /**
     * HyperOS accepts the Android pin request but does not show its confirmation UI.
     * MIUI Home exposes a normal-permission receiver for the same operation.
     */
    private fun requestMiuiInstall(
        context: Context,
        provider: Class<out android.appwidget.AppWidgetProvider>,
    ): Boolean {
        if (!isMiuiHome()) return false
        val intent = Intent(MIUI_INSTALL_WIDGET_ACTION).apply {
            component = ComponentName(MIUI_HOME_PACKAGE, MIUI_INSTALL_WIDGET_RECEIVER)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(MIUI_PROVIDER_EXTRA, ComponentName(context, provider))
            putExtra("screenId", -1L)
            putExtra("cellX", 0)
            putExtra("cellY", 0)
        }
        return try {
            if (context.packageManager.queryBroadcastReceivers(intent, 0).isEmpty()) return false
            context.sendBroadcast(intent)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * HyperOS gates widget pinning behind its own `OP_INSTALL_SHORTCUT` app-op ("桌面快捷方式").
     * When it is not allowed the launcher logs `add widget failed, <pkg> has no permission` and
     * silently drops both the legacy broadcast and the standard pin request — there is no API to
     * flip it from the app, so we can only deep-link the user to the permission editor.
     */
    fun openShortcutPermissionPage(context: Context): Boolean {
        val intent = Intent(MIUI_PERM_EDITOR_ACTION).apply {
            setClassName(MIUI_SECURITY_CENTER_PACKAGE, MIUI_PERM_EDITOR_ACTIVITY)
            putExtra("extra_pkgname", context.packageName)
            putExtra("extra_package_name", context.packageName)
        }
        when {
            context.packageManager.resolveActivity(intent, 0) != null -> intent
            else -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        }.let { target ->
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(target)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private fun isMiuiHome(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
            brand.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("Redmi", ignoreCase = true) ||
            brand.equals("Redmi", ignoreCase = true)
    }

    private const val MIUI_HOME_PACKAGE = "com.miui.home"
    private const val MIUI_SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val MIUI_PERM_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"
    private const val MIUI_PERM_EDITOR_ACTIVITY = "com.miui.permcenter.permissions.PermissionsEditorActivity"
    private const val MIUI_INSTALL_WIDGET_ACTION = "com.miui.home.launcher.action.INSTALL_WIDGET"
    private const val MIUI_INSTALL_WIDGET_RECEIVER = "com.miui.home.launcher.InstallWidgetReceiver"
    private const val MIUI_PROVIDER_EXTRA = "miui.intent.extra.provider_component_name"
}
