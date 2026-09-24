package net.shino3.gzf8launcher.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import net.shino3.gzf8launcher.data.UsageRepository
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.ui.drawer.DrawerSheetState
import net.shino3.gzf8launcher.ui.drawer.closeOnOverscroll

/**
 * アプリドロワー(docs/04、#11 で作り直し、#25 で全アプリだけに、#29 で検索と絞り込みを上部に集めた)。
 * 上から順に、取っ手、検索欄、絞り込みチップ、一覧。
 * 検索欄を上に置くのは、上からスワイプで入る面だから。指はもう上にある。
 * ドックはホーム側に固定されたままで、ここには含まれない。ウィジェットの追加は長押しメニューから。
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    columns: Int,
    sheet: DrawerSheetState,
    hidden: Boolean,
    toItem: (AppEntry) -> AppItem,
    /** 直近 7 日の起動回数と最終起動。最近 / よく使う の並びに使う。 */
    usage: Map<String, UsageRepository.PackageUsage>,
    usagePermitted: Boolean,
    onRequestUsagePermission: () -> Unit,
    /** 端末の検索が無いときの代わり。開いたら検索欄に焦点を当てる。 */
    focusSearch: Boolean,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<DrawerFilter?>(null) }
    // 複数選択(#48)。長押しで入り、以降はタップで出し入れする
    var picked by remember { mutableStateOf<Set<AppKey>>(emptySet()) }
    val gridState = rememberLazyGridState()
    val chips = remember(apps) { availableFilters(apps) }
    val shown = remember(apps, usage, query, filter) { applyFilter(filterApps(apps, query), filter, usage) }
    val needsUsage = filter is DrawerFilter.Recent || filter is DrawerFilter.Frequent

    // 検索語や絞り込みが変わったら先頭に戻す
    LaunchedScrollReset(query, filter, gridState)
    // ドロワーを閉じたら選択も解く
    LaunchedEffect(sheet.progress == 0f) { if (sheet.progress == 0f) picked = emptySet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        DragHandle(sheet)
        SearchField(
            query = query,
            onQueryChange = { query = it },
            count = shown.size,
            focusSearch = focusSearch,
            onSubmit = { shown.firstOrNull()?.let { onLaunch(it, Rect.Zero) } },
        )
        FilterChips(chips = chips, selected = filter, onSelect = { filter = if (filter == it) null else it })
        if (picked.isNotEmpty()) {
            SelectionBar(count = picked.size) { picked = emptySet() }
        }
        if (needsUsage && !usagePermitted) {
            UsagePermissionRow(onRequestUsagePermission)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(sheet.closeOnOverscroll(DEAD_ZONE)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            items(shown, key = { it.key.toString() }) { entry ->
                val selected = entry.key in picked
                // 選択中は、つまんだ 1 つに残りを載せて束として運ぶ
                val bundle = if (selected) shown.filter { it.key in picked && it.key != entry.key }.map(toItem) else emptyList()
                Box(modifier = Modifier.aspectRatio(0.85f)) {
                    AppCell(
                        entry = entry,
                        fallback = entry.label,
                        showLabel = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .dragSource(
                                payload = DragPayload(toItem(entry), null, entry.icon, entry.label, rest = bundle, menu = false),
                                enabled = !hidden,
                                onTap = { bounds ->
                                    // 選択中はタップで出し入れ。そうでなければ起動
                                    if (picked.isEmpty()) {
                                        onLaunch(entry, bounds)
                                    } else {
                                        picked = if (selected) picked - entry.key else picked + entry.key
                                    }
                                },
                                onLongPress = { picked = picked + entry.key },
                            ),
                    )
                    if (selected) SelectedMark(modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

/** ドロワーの絞り込み(#29)。一つだけ選べ、もう一度押すと外れる。 */
sealed interface DrawerFilter {
    val label: String

    data object Recent : DrawerFilter { override val label = "RECENT" }
    data object Frequent : DrawerFilter { override val label = "FREQUENT" }
    data object New : DrawerFilter { override val label = "NEW" }

    /** ApplicationInfo.CATEGORY_* の値。付けているアプリがある分だけチップに出す。 */
    data class Category(val category: Int) : DrawerFilter {
        override val label: String get() = CATEGORY_LABELS[category] ?: "OTHER"
    }
}

private val CATEGORY_LABELS = mapOf(
    ApplicationInfo.CATEGORY_GAME to "GAME",
    ApplicationInfo.CATEGORY_AUDIO to "AUDIO",
    ApplicationInfo.CATEGORY_VIDEO to "VIDEO",
    ApplicationInfo.CATEGORY_IMAGE to "IMAGE",
    ApplicationInfo.CATEGORY_SOCIAL to "SOCIAL",
    ApplicationInfo.CATEGORY_NEWS to "NEWS",
    ApplicationInfo.CATEGORY_MAPS to "MAPS",
    ApplicationInfo.CATEGORY_PRODUCTIVITY to "PRODUCTIVITY",
)

/** 出すチップ。固定の 3 つと、アプリが 1 つでも付けているカテゴリ。 */
private fun availableFilters(apps: List<AppEntry>): List<DrawerFilter> {
    val categories = apps.map { it.category }.filter { it in CATEGORY_LABELS }.distinct()
    return listOf(DrawerFilter.Recent, DrawerFilter.Frequent, DrawerFilter.New) +
        CATEGORY_LABELS.keys.filter { it in categories }.map { DrawerFilter.Category(it) }
}

/** 絞り込みを当て、チップの意味に沿った順に並べる。絞り込みが無ければ名前順のまま。 */
private fun applyFilter(
    apps: List<AppEntry>,
    filter: DrawerFilter?,
    usage: Map<String, UsageRepository.PackageUsage>,
): List<AppEntry> = when (filter) {
    null -> apps
    DrawerFilter.Recent -> apps
        .mapNotNull { app -> usage[app.componentName.packageName]?.let { app to it.lastUsed } }
        .sortedByDescending { it.second }
        .map { it.first }
    DrawerFilter.Frequent -> apps
        .mapNotNull { app -> usage[app.componentName.packageName]?.let { app to it.launches } }
        .sortedByDescending { it.second }
        .map { it.first }
    DrawerFilter.New -> {
        val since = System.currentTimeMillis() - NEW_DAYS * 24L * 60 * 60 * 1000
        apps.filter { it.installedAt >= since }.sortedByDescending { it.installedAt }
    }
    is DrawerFilter.Category -> apps.filter { it.category == filter.category }
}

/** 「新着」に入れる日数。 */
private const val NEW_DAYS = 14

/** 検索語か絞り込みが変わったら一覧を先頭に戻す。 */
@Composable
private fun LaunchedScrollReset(query: String, filter: DrawerFilter?, state: LazyGridState) {
    LaunchedEffect(query, filter) { state.scrollToItem(0) }
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

/** 上部の検索欄(#29)。 */
@Composable
private fun SearchField(
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
            // 入力は日本語も入るので UI 書体(#32)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = theme.colors.text, fontFamily = theme.uiFont, fontSize = 14.sp),
                cursorBrush = SolidColor(theme.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            TextAction("CLEAR", modifier = Modifier.padding(start = 4.dp)) { onQueryChange("") }
        }
    }
}

/** 絞り込みチップの列。横に流れる。押す場所なので 44dp 以上に取る(#32)。 */
@Composable
private fun FilterChips(chips: List<DrawerFilter>, selected: DrawerFilter?, onSelect: (DrawerFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        chips.forEach { chip ->
            Chip(label = chip.label, selected = chip == selected, modifier = Modifier.padding(end = 8.dp)) { onSelect(chip) }
        }
    }
}

/** 最近 / よく使う に必要な使用状況の権限が無いときの導線。 */
@Composable
private fun UsagePermissionRow(onRequest: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TAP_MIN)
            .pointerInput(Unit) { detectTapGestures { onRequest() } }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "USAGE ACCESS REQUIRED // TAP TO ALLOW",
            color = theme.colors.accent,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
        )
    }
}

/** 選択中に上部へ出す帯。数と、まとめて解く入口を持つ(#48)。 */
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$count SELECTED  //  DRAG TO HOME",
            color = theme.colors.accent,
            fontFamily = theme.monoFont,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        TextAction("CLEAR") { onClear() }
    }
}

/** 選ばれているアプリに付ける印。 */
@Composable
private fun SelectedMark(modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = modifier
            .padding(2.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(theme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = theme.colors.surface.copy(alpha = 1f), fontFamily = theme.monoFont, fontSize = 11.sp)
    }
}
