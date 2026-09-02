package net.shino3.gzf8launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 通常の AppWidget を載せるためのホスト(docs/04「Android ウィジェットは同じアイテムとして載せる」)。
 * startListening / stopListening はアクティビティの onStart / onStop から呼ぶ。
 */
class AppWidgetHostManager(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host: AppWidgetHost = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            LauncherAppWidgetHostView(context)
    }

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun providerInfo(provider: ComponentName): AppWidgetProviderInfo? =
        manager.installedProviders.firstOrNull { it.provider == provider }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    /** 許可済みならその場でバインドする。false なら ACTION_APPWIDGET_BIND の許可画面が要る。 */
    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName, profile: UserHandle): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, profile, provider, null)

    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): LauncherAppWidgetHostView =
        host.createView(context, appWidgetId, info) as LauncherAppWidgetHostView

    companion object {
        private const val HOST_ID = 0x6f8
    }
}

val LocalAppWidgetHost = staticCompositionLocalOf<AppWidgetHostManager> { error("AppWidgetHostManager is not provided") }
