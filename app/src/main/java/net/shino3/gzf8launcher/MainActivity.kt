package net.shino3.gzf8launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.LauncherRoot
import net.shino3.gzf8launcher.widget.BuiltInWidgets

class MainActivity : ComponentActivity() {
    private lateinit var controller: LauncherController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BuiltInWidgets.register()
        controller = LauncherController(applicationContext, lifecycleScope)
        controller.start()
        setContent {
            CompositionLocalProvider(LocalLauncherTheme provides LauncherTheme()) {
                LauncherRoot(controller)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    /** singleTask なので HOME キーはここに届く。開いているものを閉じてホームに戻す。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        controller.signalHome()
    }
}
