package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.platform.LocalContext
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.widget.NativeWidget

/**
 * アプリドロワー(docs/04)。全アプリと検索。長押しドラッグでホームやドックへ運ぶ。
 * ドラッグ中は hidden で見えなくなるが、ジェスチャを続けるために合成には残す。
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    columns: Int,
    hidden: Boolean,
    toItem: (AppEntry) -> net.shino3.gzf8launcher.model.AppItem,
    widgets: List<NativeWidget<*>>,
    providers: List<AppWidgetProviderInfo>,
    /** ドロップ先グリッドのセル幅 px。AppWidget の最小サイズをセル数に直すのに使う。 */
    cellPx: Float,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DrawerTab.APPS) }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (hidden) 0f else 1f)
            .background(theme.colors.surface.copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, theme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text("SEARCH // ${apps.size} APPS", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 12.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 13.sp),
                    cursorBrush = SolidColor(theme.colors.accent),
                )
            }
            Text(
                text = "CLOSE",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            DrawerTab.entries.forEach { t ->
                Text(
                    text = t.name,
                    color = if (tab == t) theme.colors.accent else theme.colors.textDim,
                    fontFamily = theme.monoFont,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .pointerInput(t) { detectTapGestures { tab = t } },
                )
            }
        }
        if (tab == DrawerTab.WIDGETS) {
            WidgetList(widgets, providers, cellPx, columns)
            return@Column
        }
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.key.toString() }) { entry ->
                AppCell(
                    entry = entry,
                    fallback = entry.label,
                    showLabel = true,
                    modifier = Modifier
                        .aspectRatio(0.85f)
                        .dragSource(
                            payload = DragPayload(toItem(entry), null, entry.icon, entry.label),
                            onTap = { onLaunch(entry) },
                        ),
                )
            }
        }
    }
}

enum class DrawerTab { APPS, WIDGETS }

/** 自作ウィジェットと AppWidget の一覧。長押しドラッグでホームに置く。 */
@Composable
private fun WidgetList(widgets: List<NativeWidget<*>>, providers: List<AppWidgetProviderInfo>, cellPx: Float, columns: Int) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val pm = context.packageManager
    val sorted = remember(providers) { providers.sortedBy { it.loadLabel(pm).lowercase() } }
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
        items(sorted, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
            val label = remember(info) { info.loadLabel(pm) }
            val appLabel = remember(info) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName) }
            val w = spanFor(info.minWidth, cellPx).coerceIn(1, 6)
            val h = spanFor(info.minHeight, cellPx).coerceIn(1, 6)
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, theme.outline, RoundedCornerShape(10.dp))
                    .dragSource(DragPayload(AppWidgetItem(info.provider.flattenToString()), null, null, label, w, h))
                    .padding(12.dp),
            ) {
                Text(label, color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("$appLabel  //  $w x $h", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        items(widgets, key = { it.spec.id }) { widget ->
            val spec = widget.spec
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, theme.outline, RoundedCornerShape(10.dp))
                    .dragSource(DragPayload(NativeWidgetItem(spec.id), null, null, spec.name, spec.defaultW, spec.defaultH))
                    .padding(12.dp),
            ) {
                Text(spec.name, color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 12.sp)
                Text(
                    text = "${spec.defaultW} x ${spec.defaultH}  (${spec.minW}-${spec.maxW} x ${spec.minH}-${spec.maxH})",
                    color = theme.colors.textDim,
                    fontFamily = theme.monoFont,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** px の最小サイズをセル数に切り上げる。 */
private fun spanFor(minPx: Int, cellPx: Float): Int =
    if (cellPx <= 0f) 1 else kotlin.math.ceil(minPx / cellPx).toInt().coerceAtLeast(1)
