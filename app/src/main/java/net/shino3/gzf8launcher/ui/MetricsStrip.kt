package net.shino3.gzf8launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * docs/01 の「要実測」項目を画面に出す。
 * 実機 Fold8 で折・開それぞれの値を読むためのもので、マイルストーン 3 でウィジェットに移す。
 */
@Suppress("DEPRECATION")
@Composable
fun MetricsStrip() {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val containerSize = LocalWindowInfo.current.containerSize
    val layoutInfo = rememberWindowLayoutInfo()
    val fold = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()

    val display = context.display
    val maxBounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds

    val lines = listOf(
        "WIN  ${containerSize.width}x${containerSize.height}px  " +
            "${configuration.screenWidthDp}x${configuration.screenHeightDp}dp  " +
            "dpi=${configuration.densityDpi}  sw=${configuration.smallestScreenWidthDp}dp",
        "DISP id=${display.displayId}  max=${maxBounds.width()}x${maxBounds.height()}px  " +
            "rotation=${display.rotation}  orientation=${configuration.orientation}",
        if (fold == null) {
            "FOLD none"
        } else {
            "FOLD ${fold.state}  ${fold.orientation}  ${fold.occlusionType}  bounds=${fold.bounds.toShortString()}"
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("GZF8 LAUNCHER // METRICS", color = theme.colors.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        lines.forEach { line ->
            Text(line, color = theme.colors.textDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun rememberWindowLayoutInfo(): WindowLayoutInfo? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsStateWithLifecycle(initialValue = null)
    return info
}

private fun Context.findActivity(): Activity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("Composable is not hosted in an Activity")
}
