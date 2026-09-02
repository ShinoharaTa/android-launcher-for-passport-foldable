package net.shino3.gzf8launcher

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

class MainActivity : ComponentActivity() {
    private lateinit var controller: LauncherController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = LauncherController(applicationContext, lifecycleScope)
        controller.start()
        setContent {
            CompositionLocalProvider(LocalLauncherTheme provides LauncherTheme()) {
                LauncherRoot(controller)
            }
        }
    }
}
