package net.shino3.gzf8launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragController
import net.shino3.gzf8launcher.ui.drag.DragGhost
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.LocalDragController
import net.shino3.gzf8launcher.ui.drag.dropTarget
import net.shino3.gzf8launcher.widget.WidgetRegistry

/** ホームの上に重ねるもの。同時に一つだけ。 */
sealed interface Overlay {
    data object Drawer : Overlay
    data class Folder(val ref: ItemRef) : Overlay
    data class Menu(val payload: DragPayload) : Overlay
}

/** どの面を出すかを実寸 dp で決め(docs/01)、共通のドック、重ね描き、ドラッグを持つ。 */
@Composable
fun LauncherRoot(controller: LauncherController) {
    val theme = LocalLauncherTheme.current
    val apps by controller.apps.collectAsStateWithLifecycle()
    val layout by controller.layout.collectAsStateWithLifecycle()
    val usagePermitted by controller.usagePermitted.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val density = LocalDensity.current

    val drag = remember(controller) {
        DragController(
            slopPx = with(density) { 12.dp.toPx() },
            onDrop = { session, target ->
                controller.drop(session, target, theme.columns, theme.dockSlots)
                overlay = null
            },
            onLongPress = { payload -> overlay = Overlay.Menu(payload) },
        )
    }
    val actions = remember(controller) {
        ItemActions(
            onLaunch = { controller.launch(it) },
            onOpenFolder = { overlay = Overlay.Folder(it) },
            resolveFolder = { controller.resolveFolder(it) },
        )
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    LaunchedEffect(controller) { controller.homeSignal.collect { overlay = null } }

    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp() }
    val isCover = widthDp < 600.dp
    val swipeThreshold = with(density) { 80.dp.toPx() }

    CompositionLocalProvider(LocalDragController provides drag) {
        Box(modifier = Modifier.fillMaxSize().background(theme.colors.panel)) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .swipeUp(swipeThreshold) { overlay = Overlay.Drawer },
                ) {
                    if (isCover) {
                        CoverSurface(layout, apps, actions)
                    } else {
                        MainSurface(layout, apps, actions)
                    }
                }
                Dock(layout.dock, apps, actions, Modifier.swipeUp(swipeThreshold) { overlay = Overlay.Drawer })
            }

            val session = drag.session
            if (session != null) {
                Box(modifier = Modifier.fillMaxWidth().systemBarsPadding(), contentAlignment = Alignment.TopCenter) { RemoveBar() }
            }
            when (val current = overlay) {
                Overlay.Drawer -> AppDrawer(
                    apps = apps.values.sortedBy { it.label.lowercase() },
                    columns = if (isCover) theme.columns else theme.columns * 2,
                    hidden = session != null,
                    toItem = { controller.toAppItem(it) },
                    widgets = WidgetRegistry.all.toList(),
                    onLaunch = { controller.launch(it); overlay = null },
                    onClose = { overlay = null },
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
                    onAppInfo = { controller.openAppDetails(it) },
                    onOpenFolder = { overlay = Overlay.Folder(it) },
                    onResize = { ref, dw, dh -> controller.resize(ref, dw, dh, theme.columns) },
                    onRemove = { controller.remove(it) },
                    onDismiss = { overlay = null },
                )
                null -> Unit
            }
            if (session != null) DragGhost(session)
        }
    }
}

/** 上方向のスワイプでドロワーを開く。子の長押しドラッグとは、長押しの有無で自然に切り分けられる。 */
private fun Modifier.swipeUp(thresholdPx: Float, onSwipe: () -> Unit): Modifier =
    pointerInput(thresholdPx) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onVerticalDrag = { change, dy ->
                total += dy
                change.consume()
            },
            onDragEnd = { if (total < -thresholdPx) onSwipe() },
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
        Text("DROP HERE TO REMOVE", color = theme.colors.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
 * アンカーの左右切り替えはテーマに載せるまで右固定。
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
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.colors.line))
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
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
