package net.shino3.gzf8launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.ui.drawer.DrawerSheetState
import net.shino3.gzf8launcher.ui.drawer.closeOnOverscroll
import net.shino3.gzf8launcher.widget.NativeWidget

enum class DrawerTab { APPS, WIDGETS }

/**
 * アプリドロワー(docs/04、#11 で作り直し)。
 * 検索欄を下端に置き、一覧はその上をスクロールする。横長のメイン画面でも親指から届く。
 * ドックはホーム側に固定されたままで、ここには含まれない。
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    columns: Int,
    sheet: DrawerSheetState,
    hidden: Boolean,
    toItem: (AppEntry) -> AppItem,
    widgets: List<NativeWidget<*>>,
    providers: List<AppWidgetProviderInfo>,
    /** ドロップ先グリッドのセル幅 px。AppWidget の最小サイズをセル数に直すのに使う。 */
    cellPx: Float,
    onLaunch: (AppEntry) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DrawerTab.APPS) }
    val gridState = rememberLazyGridState()
    val filtered = remember(apps, query) { filterApps(apps, query) }

    // ドロワーを開いたまま検索語だけ変えたとき、先頭に戻す
    LaunchedScrollReset(query, gridState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        DragHandle(sheet)
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                DrawerTab.APPS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(sheet.closeOnOverscroll()),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    items(filtered, key = { it.key.toString() }) { entry ->
                        AppCell(
                            entry = entry,
                            fallback = entry.label,
                            showLabel = true,
                            modifier = Modifier
                                .aspectRatio(0.85f)
                                .dragSource(
                                    payload = DragPayload(toItem(entry), null, entry.icon, entry.label),
                                    enabled = !hidden,
                                    onTap = { onLaunch(entry) },
                                ),
                        )
                    }
                }
                DrawerTab.WIDGETS -> WidgetList(widgets, providers, cellPx, sheet)
            }
        }
        BottomBar(
            query = query,
            onQueryChange = { query = it },
            tab = tab,
            onTabChange = { tab = it },
            count = filtered.size,
            onSubmit = { filtered.firstOrNull()?.let(onLaunch) },
        )
    }
}

/** 検索語が変わったら一覧を先頭に戻す。 */
@Composable
private fun LaunchedScrollReset(query: String, state: androidx.compose.foundation.lazy.grid.LazyGridState) {
    androidx.compose.runtime.LaunchedEffect(query) { state.scrollToItem(0) }
}

/**
 * アプリ名とパッケージ名で絞り込む。
 * 先頭一致を前に出すので、確定キーで起動したときに狙ったものが出る。
 */
private fun filterApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    return apps
        .mapNotNull { entry ->
            val label = entry.label
            val rank = when {
                label.startsWith(q, ignoreCase = true) -> 0
                label.split(' ').any { it.startsWith(q, ignoreCase = true) } -> 1
                label.contains(q, ignoreCase = true) -> 2
                entry.componentName.packageName.contains(q, ignoreCase = true) -> 3
                else -> return@mapNotNull null
            }
            entry to rank
        }
        .sortedWith(compareBy({ it.second }, { it.first.label.lowercase() }))
        .map { it.first }
}

/** つまんで開閉できる取っ手。一覧が先頭でなくてもここからは閉じられる。 */
@Composable
private fun DragHandle(sheet: DrawerSheetState) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .draggable(
                state = rememberDraggableState { delta -> sheet.dragBy(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> sheet.settle(velocity) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.colors.textDim),
        )
    }
}

/** 下端に固定する検索欄とタブ。親指の届く位置に置く(#11)。 */
@Composable
private fun BottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    tab: DrawerTab,
    onTabChange: (DrawerTab) -> Unit,
    count: Int,
    onSubmit: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            DrawerTab.entries.forEach { t ->
                Text(
                    text = t.name,
                    color = if (tab == t) theme.colors.accent else theme.colors.textDim,
                    fontFamily = theme.monoFont,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .pointerInput(t) { detectTapGestures { onTabChange(t) } },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.module)
                    .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
                    // 枠の余白を触っても入力できるようにする。文字入力欄そのものは細いので当てにくい
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusRequester.requestFocus() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (query.isEmpty()) {
                    Text("SEARCH // $count APPS", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 12.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 13.sp),
                    cursorBrush = SolidColor(theme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    text = "CLEAR",
                    color = theme.colors.accent,
                    fontFamily = theme.monoFont,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .pointerInput(Unit) { detectTapGestures { onQueryChange("") } },
                )
            }
        }
    }
}

/** 自作ウィジェットと AppWidget の一覧。長押しドラッグでホームに置く。 */
@Composable
private fun WidgetList(
    widgets: List<NativeWidget<*>>,
    providers: List<AppWidgetProviderInfo>,
    cellPx: Float,
    sheet: DrawerSheetState,
) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val pm = context.packageManager
    val sorted = remember(providers) { providers.sortedBy { it.loadLabel(pm).lowercase() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().nestedScroll(sheet.closeOnOverscroll()),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        items(sorted, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
            val label = remember(info) { info.loadLabel(pm) }
            val appLabel = remember(info) {
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
            }
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
                Text(label, color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$appLabel  //  $w x $h", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
