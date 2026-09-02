package net.shino3.gzf8launcher.widget

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.shino3.gzf8launcher.data.UsageRepository
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** アプリ起動頻度のグラフ。UsageStats の権限が無ければ許可画面への導線を出す。 */
object UsageWidget {
    data class Entry(val label: String, val launches: Int)
    data class State(val permitted: Boolean, val entries: List<Entry>)

    val spec = WidgetSpec(id = "usage", name = "USAGE // 7D", defaultW = 3, defaultH = 2, minW = 2, minH = 1)

    private fun load(context: Context, limit: Int): State {
        val repo = UsageRepository(context)
        if (!repo.hasPermission()) return State(false, emptyList())
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val entries = repo.query(days = 7).values
            .filter { it.packageName != context.packageName }
            .sortedByDescending { it.launches }
            .mapNotNull { usage ->
                val label = launcherApps.getActivityList(usage.packageName, Process.myUserHandle())
                    .firstOrNull()?.label?.toString() ?: return@mapNotNull null
                Entry(label, usage.launches)
            }
            .take(limit)
        return State(true, entries)
    }

    private val source = WidgetDataSource { context ->
        flow {
            while (true) {
                emit(load(context, limit = 6))
                delay(60_000)
            }
        }.flowOn(Dispatchers.IO)
    }

    private val bars = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        val context = LocalContext.current
        if (!state.permitted) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "GRANT USAGE ACCESS →",
                    color = theme.colors.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { context.startActivity(UsageRepository(context).settingsIntent()) }
                    },
                )
            }
            return@WidgetRenderer
        }
        val max = state.entries.maxOfOrNull { it.launches }?.coerceAtLeast(1) ?: 1
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            state.entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label.uppercase(),
                        color = theme.colors.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(6.dp).background(theme.colors.line)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(entry.launches / max.toFloat())
                                .fillMaxHeight()
                                .background(theme.colors.accent),
                        )
                    }
                    Text(
                        text = "${entry.launches}",
                        color = theme.colors.textDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 6.dp).width(28.dp),
                    )
                }
            }
        }
    }

    val widget = NativeWidget(spec, source, mapOf(NativeWidget.DEFAULT_VARIANT to bars))
}
