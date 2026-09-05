package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.ui.drawer.DrawerSheetState
import net.shino3.gzf8launcher.ui.drawer.closeOnOverscroll

/**
 * アプリドロワー(docs/04、#11 で作り直し、#25 で全アプリだけに絞った)。
 * 検索欄を下端に置き、一覧はその上をスクロールする。横長のメイン画面でも親指から届く。
 * ドックはホーム側に固定されたままで、ここには含まれない。ウィジェットの追加は長押しメニューから。
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    columns: Int,
    sheet: DrawerSheetState,
    hidden: Boolean,
    toItem: (AppEntry) -> AppItem,
    /** 端末の全体検索が無いときの代わり。開いたら検索欄に焦点を当てる。 */
    focusSearch: Boolean,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    var query by remember { mutableStateOf("") }
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                            onTap = { bounds -> onLaunch(entry, bounds) },
                        ),
                )
            }
        }
        BottomBar(
            query = query,
            onQueryChange = { query = it },
            count = filtered.size,
            focusSearch = focusSearch,
            onSubmit = { filtered.firstOrNull()?.let { onLaunch(it, Rect.Zero) } },
        )
    }
}

/** 検索語が変わったら一覧を先頭に戻す。 */
@Composable
private fun LaunchedScrollReset(query: String, state: LazyGridState) {
    LaunchedEffect(query) { state.scrollToItem(0) }
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

/** 下端に固定する検索欄。親指の届く位置に置く(#11)。 */
@Composable
private fun BottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    count: Int,
    focusSearch: Boolean,
    onSubmit: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) runCatching { focusRequester.requestFocus() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
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
