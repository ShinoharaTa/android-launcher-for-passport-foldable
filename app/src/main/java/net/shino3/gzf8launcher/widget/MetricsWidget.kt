package net.shino3.gzf8launcher.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * docs/01 の「要実測」項目を画面に出す。
 * 実機 Fold8 で折・開それぞれの値を読むためのもので、製品 UI ではない。
 */
object MetricsWidget {
    /** FoldingFeature が無いときは null を包んで流す(Flow は null を流せるので Optional 代わりの箱)。 */
    data class State(val fold: FoldingFeature?)

    val spec = WidgetSpec(id = "metrics", name = "GZF8 // METRICS", defaultW = 6, defaultH = 1, minW = 3, minH = 1)

    private val source = WidgetDataSource { context ->
        val activity = context.findActivity()
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { info -> State(info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()) }
    }

    @Suppress("DEPRECATION")
    private val lines = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val containerSize = LocalWindowInfo.current.containerSize
        val display = context.display
        val maxBounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val fold = state.fold
        val texts = listOf(
            "WIN ${containerSize.width}x${containerSize.height}px ${configuration.screenWidthDp}x${configuration.screenHeightDp}dp dpi=${configuration.densityDpi} sw=${configuration.smallestScreenWidthDp}dp",
            "DISP id=${display.displayId} max=${maxBounds.width()}x${maxBounds.height()}px rot=${display.rotation} orient=${configuration.orientation}",
            if (fold == null) "FOLD none" else "FOLD ${fold.state} ${fold.orientation} ${fold.occlusionType} ${fold.bounds.toShortString()}",
        )
        Column(modifier = modifier.fillMaxSize()) {
            texts.forEach {
                Text(it, color = theme.colors.textDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val widget = NativeWidget(spec, source, mapOf(NativeWidget.DEFAULT_VARIANT to lines))

    private fun Context.findActivity(): Activity {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        error("Composable is not hosted in an Activity")
    }
}
