package net.shino3.gzf8launcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.LauncherRoot
import net.shino3.gzf8launcher.widget.BuiltInWidgets

class MainActivity : ComponentActivity(), LauncherController.AppWidgetBindHost {
    private lateinit var controller: LauncherController

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        controller.onBindResult(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BuiltInWidgets.register()
        controller = LauncherController(applicationContext, lifecycleScope)
        controller.bindHost = this
        controller.start()
        setContent {
            CompositionLocalProvider(LocalLauncherTheme provides LauncherTheme()) {
                LauncherRoot(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.appWidgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onStop() {
        controller.appWidgets.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        controller.bindHost = null
        super.onDestroy()
    }

    /** singleTask なので HOME キーはここに届く。開いているものを閉じてホームに戻す。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        controller.signalHome()
    }

    override fun requestBind(appWidgetId: Int, provider: ComponentName, profile: UserHandle) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
        bindLauncher.launch(intent)
    }

    override fun requestConfigure(appWidgetId: Int) {
        controller.appWidgets.host.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQUEST_CONFIGURE, null)
    }

    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult は旧 API の結果通知を使う")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) controller.onConfigureResult(resultCode == RESULT_OK)
    }

    private companion object {
        const val REQUEST_CONFIGURE = 0x6f81
    }
}
