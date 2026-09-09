package net.shino3.gzf8launcher.ui

import android.app.Activity
import android.app.ActivityOptions
import android.os.Bundle
import android.view.View
import android.graphics.Rect as AndroidRect
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
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.data.ShortcutEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.ItemRef
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
    /** 触った場所。ここを原点にして広がる(#23)。 */
    val source: Rect

    data class Settings(override val source: Rect) : Overlay
    data class Home(override val source: Rect) : Overlay
    data class Folder(val ref: ItemRef, override val source: Rect) : Overlay
    data class Menu(val payload: DragPayload, override val source: Rect) : Overlay
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
    // 閉じる動きを見せるため、消えたあとも終わるまで描き続ける
    var rendered by remember { mutableStateOf<Overlay?>(null) }
    LaunchedEffect(overlay) {
        val current = overlay
        if (current != null) {
            rendered = current
        } else {
            delay(OverlayAnim.EXIT_MILLIS.toLong())
            rendered = null
        }
    }
    val sheet = rememberDrawerSheetState()
    // カバー(と縦長メイン)は 0 がウィジェット面、1 以降がアプリのページ。横長メインの右側はアプリのページだけ
    val coverPager = rememberPagerState(initialPage = 1) { 1 + layout.pages.size }
    val appsPager = rememberPagerState(initialPage = 0) { layout.pages.size }
    val density = LocalDensity.current

    ApplyWindowAppearance(theme)

    val drag = remember(controller, theme.columns, theme.dockSlots) {
        DragController(
            slopPx = with(density) { 12.dp.toPx() },
            onDrop = { session, target ->
                controller.drop(session, target, theme.columns, theme.dockSlots)
                controller.pruneEmptyPages()
                overlay = null
                sheet.close()
            },
            onLongPress = { payload, bounds -> overlay = Overlay.Menu(payload, bounds) },
            onCancel = { controller.pruneEmptyPages() },
        )
    }
    val view = LocalView.current
    val actions = remember(controller, view) {
        ItemActions(
            onLaunch = { entry, bounds -> controller.launch(entry, bounds.toAndroidRect(), view.scaleUpOptions(bounds)) },
            onOpenFolder = { ref, bounds -> overlay = Overlay.Folder(ref, bounds) },
            resolveFolder = { controller.resolveFolder(it) },
            resolveShortcut = { controller.resolveShortcut(it) },
            onLaunchShortcut = { item, bounds -> controller.launchShortcut(item, bounds.toAndroidRect(), view.scaleUpOptions(bounds)) },
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
            coverPager.animateScrollToPage(1)
            appsPager.animateScrollToPage(0)
        }
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp() }
    val isCover = widthDp < 600.dp
    // 開いて縦長にしたときは 1 ページずつ。余白を広げて置く
    val isTallMain = !isCover && containerSize.height > containerSize.width
    val sideBySide = !isCover && !isTallMain
    val pageSidePadding = if (isTallMain) 40.dp else 8.dp
    val gridWidthPx = (containerSize.width / (if (sideBySide) 2 else 1)) - with(density) { (pageSidePadding * 2).toPx() }
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
                // ホームは縦スクロールなので、上ドラッグでドロワーを開く入口は置かない(#19)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .longPressOnEmptySpace(drag) { at -> overlay = Overlay.Home(Rect(at, at)) },
                ) {
                    if (sideBySide) {
                        SideBySideSurface(layout, apps, actions, appsPager, onCreatePage = { controller.addPage() })
                    } else {
                        PagedSurface(layout, apps, actions, coverPager, onCreatePage = { controller.addPage() }, sidePadding = pageSidePadding)
                    }
                }
                Dock(layout.dock, apps, actions, onOpenDrawer = { sheet.open() }, modifier = Modifier.dragToOpenDrawer(sheet))
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
                        columns = if (sideBySide) theme.columns * 2 else theme.columns,
                        sheet = sheet,
                        hidden = session != null,
                        toItem = { controller.toAppItem(it) },
                        widgets = WidgetRegistry.all.toList(),
                        providers = controller.appWidgets.providers(),
                        cellPx = cellPx,
                        onLaunch = { entry, bounds ->
                        controller.launch(entry, bounds.toAndroidRect(), view.scaleUpOptions(bounds))
                        sheet.close()
                    },
                    )
                }
            }

            if (session != null) {
                Box(modifier = Modifier.fillMaxWidth().systemBarsPadding(), contentAlignment = Alignment.TopCenter) { RemoveBar() }
            }
            val visible = overlay != null
            when (val current = rendered) {
                is Overlay.Settings -> SettingsScreen(
                    themes = themes,
                    currentThemeId = theme.id,
                    onApplyTheme = { controller.applyTheme(it) },
                    onClose = { overlay = null },
                )
                is Overlay.Home -> HomeMenu(
                    visible = visible,
                    source = current.source,
                    onOpenSettings = { overlay = Overlay.Settings(current.source) },
                    onOpenDrawer = { overlay = null; sheet.open() },
                    onDismiss = { overlay = null },
                )
                is Overlay.Folder -> FolderPopup(
                    folderRef = current.ref,
                    visible = visible,
                    source = current.source,
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
                    visible = visible,
                    source = current.source,
                    layout = layout,
                    shortcuts = menuShortcuts,
                    hidden = session != null,
                    onAppInfo = { controller.openAppDetails(it) },
                    onOpenFolder = { overlay = Overlay.Folder(it, current.source) },
                    onResize = { ref, dw, dh -> controller.resize(ref, dw, dh, theme.columns, theme.rows) },
                    onRemove = { controller.remove(it) },
                    onLaunchShortcut = { item, bounds -> controller.launchShortcut(item, bounds.toAndroidRect(), view.scaleUpOptions(bounds)) },
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
private fun Modifier.longPressOnEmptySpace(drag: DragController, onLongPress: (Offset) -> Unit): Modifier =
    pointerInput(drag) {
        val wait = viewConfiguration.longPressTimeoutMillis + 150
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // スクロールなど他が消費したら cancelled。時間切れまで押されたままなら lifted も cancelled も偽
            var cancelled = false
            val lifted = withTimeoutOrNull(wait) {
                waitForUpOrCancellation().also { if (it == null) cancelled = true }
            }
            if (lifted == null && !cancelled && drag.session == null) onLongPress(down.position)
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

/** アイコンの矩形から画面が広がる起動オプション。矩形が分からないときは既定の遷移に任せる。 */
private fun View.scaleUpOptions(bounds: Rect): Bundle? {
    if (bounds.isEmpty) return null
    return runCatching {
        ActivityOptions.makeScaleUpAnimation(
            this,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        ).toBundle()
    }.getOrNull()
}

/** アプリ側がメニューの位置決めに使う矩形。 */
private fun Rect.toAndroidRect(): AndroidRect? =
    if (isEmpty) null else AndroidRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

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
