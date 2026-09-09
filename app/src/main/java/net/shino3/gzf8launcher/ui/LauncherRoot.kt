package net.shino3.gzf8launcher.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.data.ShortcutEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragController
import net.shino3.gzf8launcher.ui.drag.DragGhost
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.LocalDragController
import net.shino3.gzf8launcher.ui.drag.dropTarget
import net.shino3.gzf8launcher.ui.drawer.rememberDrawerSheetState
import net.shino3.gzf8launcher.widget.LocalAppWidgetHost
import net.shino3.gzf8launcher.widget.WidgetRegistry

/** ホームの上に重ねるもの。同時に一つだけ。ドロワーは進捗で動かすので、ここには含めない。 */
sealed interface Overlay {
    data object Settings : Overlay
    data object Home : Overlay
    data class Folder(val ref: ItemRef) : Overlay
    data class Menu(val payload: DragPayload) : Overlay
}

/** どの面を出すかを実寸 dp で決め(docs/01)、共通のドック、重ね描き、ドラッグを持つ。 */
@Composable
fun LauncherRoot(controller: LauncherController) {
    val theme by controller.theme.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalLauncherTheme provides theme) {
        LauncherContent(controller, theme)
    }
}

@Composable
private fun LauncherContent(controller: LauncherController, theme: LauncherTheme) {
    val apps by controller.apps.collectAsStateWithLifecycle()
    val layout by controller.layout.collectAsStateWithLifecycle()
    val usagePermitted by controller.usagePermitted.collectAsStateWithLifecycle()
    val themes by controller.themes.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val sheet = rememberDrawerSheetState()
    val density = LocalDensity.current

    ApplyWindowAppearance(theme)

    val drag = remember(controller, theme.columns, theme.dockSlots) {
        DragController(
            slopPx = with(density) { 12.dp.toPx() },
            onDrop = { session, target ->
                controller.drop(session, target, theme.columns, theme.dockSlots)
                overlay = null
                sheet.close()
            },
            onLongPress = { payload -> overlay = Overlay.Menu(payload) },
        )
    }
    val actions = remember(controller) {
        ItemActions(
            onLaunch = { controller.launch(it) },
            onOpenFolder = { overlay = Overlay.Folder(it) },
            resolveFolder = { controller.resolveFolder(it) },
            resolveShortcut = { controller.resolveShortcut(it) },
            onLaunchShortcut = { controller.launchShortcut(it) },
        )
    }

    // 長押しメニューを出したアプリのショートカットを読む
    var menuShortcuts by remember { mutableStateOf<List<ShortcutEntry>>(emptyList()) }
    LaunchedEffect(overlay) {
        val app = (overlay as? Overlay.Menu)?.payload?.item as? AppItem
        menuShortcuts = if (app == null) emptyList() else controller.shortcutsFor(app)
    }

    BackHandler(enabled = overlay != null || sheet.progress > 0f) {
        if (overlay != null) overlay = null else sheet.close()
    }
    LaunchedEffect(controller) {
        controller.homeSignal.collect {
            overlay = null
            sheet.close()
        }
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp() }
    val isCover = widthDp < 600.dp
    val gridWidthPx = (containerSize.width / (if (isCover) 1 else 2)) - with(density) { 16.dp.toPx() }
    val cellPx = gridWidthPx / theme.columns
    val session = drag.session

    CompositionLocalProvider(LocalDragController provides drag, LocalAppWidgetHost provides controller.appWidgets) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sheet.heightPx = it.height.toFloat() }
                .background(theme.colors.panel)
                .then(theme.gradient?.let { Modifier.background(it) } ?: Modifier),
        ) {
            // ホーム。ドロワーが上がるにつれて少し奥へ引く
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = sheet.progress
                        val s = 1f - 0.04f * p
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.7f * p
                    }
                    .systemBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .dragToOpenDrawer(sheet)
                        .longPressOnEmptySpace(drag) { overlay = Overlay.Home },
                ) {
                    if (isCover) {
                        CoverSurface(layout, apps, actions)
                    } else {
                        MainSurface(layout, apps, actions)
                    }
                }
                Dock(layout.dock, apps, actions, Modifier.dragToOpenDrawer(sheet))
            }

            if (theme.decor.scanlines) Scanlines(theme.colors.line.copy(alpha = 0.06f))

            // ドロワー。進捗に応じて下から上がる
            if (sheet.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = size.height * (1f - sheet.progress) },
                ) {
                    AppDrawer(
                        apps = apps.values.sortedBy { it.label.lowercase() },
                        columns = if (isCover) theme.columns else theme.columns * 2,
                        sheet = sheet,
                        hidden = session != null,
                        toItem = { controller.toAppItem(it) },
                        widgets = WidgetRegistry.all.toList(),
                        providers = controller.appWidgets.providers(),
                        cellPx = cellPx,
                        onLaunch = { controller.launch(it); sheet.close() },
                    )
                }
            }

            if (session != null) {
                Box(modifier = Modifier.fillMaxWidth().systemBarsPadding(), contentAlignment = Alignment.TopCenter) { RemoveBar() }
            }
            when (val current = overlay) {
                Overlay.Settings -> SettingsScreen(
                    themes = themes,
                    currentThemeId = theme.id,
                    onApplyTheme = { controller.applyTheme(it) },
                    onClose = { overlay = null },
                )
                Overlay.Home -> HomeMenu(
                    onOpenSettings = { overlay = Overlay.Settings },
                    onOpenDrawer = { overlay = null; sheet.open() },
                    onDismiss = { overlay = null },
                )
                is Overlay.Folder -> FolderPopup(
                    folderRef = current.ref,
                    layout = layout,
                    apps = apps,
                    actions = actions,
                    hidden = session != null,
                    usagePermitted = usagePermitted,
                    onRename = { controller.renameFolder(current.ref, it) },
                    onRuleChange = { controller.setFolderRule(current.ref, it) },
                    onRequestUsagePermission = { controller.requestUsagePermission() },
                    onDismiss = { overlay = null },
                )
                is Overlay.Menu -> ItemMenu(
                    payload = current.payload,
                    layout = layout,
                    shortcuts = menuShortcuts,
                    hidden = session != null,
                    onAppInfo = { controller.openAppDetails(it) },
                    onOpenFolder = { overlay = Overlay.Folder(it) },
                    onResize = { ref, dw, dh -> controller.resize(ref, dw, dh, theme.columns) },
                    onRemove = { controller.remove(it) },
                    onLaunchShortcut = { controller.launchShortcut(it) },
                    onDismiss = { overlay = null },
                )
                null -> Unit
            }
            if (session != null) DragGhost(session)
        }
    }
}

/**
 * ウィンドウ側の見た目をテーマに合わせる。
 * 明るいテーマではステータスバーのアイコンを黒にし、壁紙を使わないテーマでは壁紙の描画を止める。
 */
@Composable
private fun ApplyWindowAppearance(theme: LauncherTheme) {
    val context = LocalContext.current
    LaunchedEffect(theme.light, theme.showWallpaper, context) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = theme.light
            isAppearanceLightNavigationBars = theme.light
        }
        if (theme.showWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
    }
}

/**
 * ホームの何も置いていない場所を長押ししたときだけ反応する。
 *
 * アイテムの長押しドラッグと同じ指の動きなので、単純な detectTapGestures では
 * アイテムの上でも親側が反応してしまう。アイテムの長押しが成立していればその時点で
 * ドラッグが始まっているので、長押しの判定時間を少し長めに取り、
 * ドラッグが始まっていないことを確かめてから出す。
 */
@Composable
private fun Modifier.longPressOnEmptySpace(drag: DragController, onLongPress: () -> Unit): Modifier =
    pointerInput(drag) {
        val wait = viewConfiguration.longPressTimeoutMillis + 150
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val lifted = withTimeoutOrNull(wait) { waitForUpOrCancellation() }
            if (lifted == null && drag.session == null) onLongPress()
        }
    }

/**
 * 上方向のドラッグでドロワーを引き上げる。閾値で切り替えず、指の移動に追従させる(#11)。
 * 子の長押しドラッグとは、長押しの有無で切り分けられる。
 */
@Composable
private fun Modifier.dragToOpenDrawer(sheet: net.shino3.gzf8launcher.ui.drawer.DrawerSheetState): Modifier {
    val state = rememberDraggableState { delta -> sheet.dragBy(delta) }
    return draggable(
        state = state,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> sheet.settle(velocity) },
    )
}

/** ドラッグ中だけ画面上端に出る削除先。 */
@Composable
private fun RemoveBar() {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.accent)
            .dropTarget("remove") { DropTarget.Remove(it) },
        contentAlignment = Alignment.Center,
    ) {
        Text("DROP HERE TO REMOVE", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 12.sp)
    }
}

/** カバー画面(閉)。カバー面のゾーンをそのまま描く。 */
@Composable
fun CoverSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    HomeGrid(layout.cover, ZoneId.COVER, theme.columns, modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) { index, placed ->
        ItemView(placed.item, ItemRef.Grid(ZoneId.COVER, index), apps, actions, w = placed.w, h = placed.h)
    }
}

/**
 * メイン画面(開)。右をアンカー(カバー面の参照)、左を拡張パネルにする(docs/03)。
 * アンカーの左右切り替えは #3 で扱う。
 */
@Composable
fun MainSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Row(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("EXT.PANEL // UNFOLD+")
            HomeGrid(layout.extension, ZoneId.EXTENSION, theme.columns, Modifier.weight(1f).padding(horizontal = 8.dp)) { index, placed ->
                ItemView(placed.item, ItemRef.Grid(ZoneId.EXTENSION, index), apps, actions, w = placed.w, h = placed.h)
            }
        }
        if (theme.decor.hingeMarker) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.colors.line))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("PRIMARY // SYNC:COVER")
            HomeGrid(layout.cover, ZoneId.COVER, theme.columns, Modifier.weight(1f).padding(horizontal = 8.dp)) { index, placed ->
                ItemView(placed.item, ItemRef.Grid(ZoneId.COVER, index), apps, actions, w = placed.w, h = placed.h)
            }
        }
    }
}

@Composable
private fun ZoneHeader(text: String) {
    val theme = LocalLauncherTheme.current
    if (!theme.decor.zoneHeaders) return
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = theme.monoFont,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
