package net.shino3.gzf8launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.widget.NativeWidget
import kotlin.math.ceil

/**
 * ウィジェットの一覧。ホームの空き領域の長押しメニューから開く(#25)。
 * 他のランチャーと同じく、アプリ一覧には混ぜない。長押しドラッグでホームに置く。
 */
@Composable
fun WidgetPicker(
    visible: Boolean,
    source: Rect?,
    hidden: Boolean,
    widgets: List<NativeWidget<*>>,
    providers: List<AppWidgetProviderInfo>,
    /** ドロップ先グリッドのセル幅 px。AppWidget の最小サイズをセル数に直すのに使う。 */
    cellPx: Float,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val pm = context.packageManager
    val sorted = remember(providers) { providers.sortedBy { it.loadLabel(pm).lowercase() } }
    val shape = RoundedCornerShape(theme.moduleRadius + 6.dp)

    OverlayScaffold(visible = visible, source = source, hidden = hidden, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.7f)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(12.dp),
        ) {
            Text(
                text = "WIDGETS // DRAG TO HOME",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(widgets, key = { it.spec.id }) { widget ->
                    val spec = widget.spec
                    PickerCard(
                        title = spec.name,
                        subtitle = "${spec.defaultW} x ${spec.defaultH}  (${spec.minW}-${spec.maxW} x ${spec.minH}-${spec.maxH})",
                        accent = true,
                        modifier = Modifier.dragSource(
                            DragPayload(NativeWidgetItem(spec.id), null, null, spec.name, spec.defaultW, spec.defaultH),
                            enabled = !hidden,
                        ),
                    )
                }
                items(sorted, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                    val label = remember(info) { info.loadLabel(pm) }
                    val appLabel = remember(info) {
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                            .getOrDefault(info.provider.packageName)
                    }
                    val w = spanFor(info.minWidth, cellPx).coerceIn(1, 6)
                    val h = spanFor(info.minHeight, cellPx).coerceIn(1, 6)
                    PickerCard(
                        title = label,
                        subtitle = "$appLabel  //  $w x $h",
                        accent = false,
                        modifier = Modifier.dragSource(
                            DragPayload(AppWidgetItem(info.provider.flattenToString()), null, null, label, w, h),
                            enabled = !hidden,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCard(title: String, subtitle: String, accent: Boolean, modifier: Modifier) {
    val theme = LocalLauncherTheme.current
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, theme.outline, RoundedCornerShape(10.dp))
            .then(modifier)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = if (accent) theme.colors.accent else theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(subtitle, color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** px の最小サイズをセル数に切り上げる。 */
private fun spanFor(minPx: Int, cellPx: Float): Int =
    if (cellPx <= 0f) 1 else ceil(minPx / cellPx).toInt().coerceAtLeast(1)
