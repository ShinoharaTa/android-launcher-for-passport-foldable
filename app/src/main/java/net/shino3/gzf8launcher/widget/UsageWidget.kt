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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

    /** 権限が無いときは許可画面への導線を出す。両レンダラで共通。 */
    @Composable
    private fun PermissionPrompt(modifier: Modifier, mono: Boolean) {
        val theme = LocalLauncherTheme.current
        val context = LocalContext.current
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (mono) "GRANT USAGE ACCESS →" else "使用状況へのアクセスを許可",
                color = theme.colors.accent,
                fontFamily = if (mono) theme.monoFont else theme.uiFont,
                fontSize = 12.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { context.startActivity(UsageRepository(context).settingsIntent()) }
                },
            )
        }
    }

    private val bars = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        if (!state.permitted) {
            PermissionPrompt(modifier, mono = true)
            return@WidgetRenderer
        }
        val max = state.entries.maxOfOrNull { it.launches }?.coerceAtLeast(1) ?: 1
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            state.entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label.uppercase(),
                        color = theme.colors.text,
                        fontFamily = theme.monoFont,
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
                        fontFamily = theme.monoFont,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 6.dp).width(28.dp),
                    )
                }
            }
        }
    }

    /** ミニマル。バーを引かず、順位と回数だけを並べる。 */
    private val list = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        if (!state.permitted) {
            PermissionPrompt(modifier, mono = false)
            return@WidgetRenderer
        }
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            state.entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = theme.colors.textDim, fontFamily = theme.uiFont, fontSize = 11.sp, modifier = Modifier.width(20.dp))
                    Text(
                        text = entry.label,
                        color = theme.colors.text,
                        fontFamily = theme.uiFont,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${entry.launches}", color = theme.colors.textDim, fontFamily = theme.uiFont, fontSize = 12.sp)
                }
            }
        }
    }

    val widget = NativeWidget(spec, source, mapOf(NativeWidget.DEFAULT_VARIANT to bars, "plain" to list))
}
