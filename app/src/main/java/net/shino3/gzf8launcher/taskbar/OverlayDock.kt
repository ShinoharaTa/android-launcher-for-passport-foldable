package net.shino3.gzf8launcher.taskbar

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.theme.DockStyle
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.AppCell
import net.shino3.gzf8launcher.ui.FolderCell
import net.shino3.gzf8launcher.ui.fallbackLabel
import net.shino3.gzf8launcher.ui.moduleSurface

/**
 * 他のアプリの上に重ねるドック(#56)。
 *
 * ホームのドック(ui/Dock)と同じスロット、同じテーマで描くが、ドラッグや編集は持たない。
 * ホーム側の Dock はドロップ先の登録に DragController を要るので、ここでは別に組む。
 * タップで起動するだけ。フォルダは見せるが開かない(開くには ホームに戻る)。
 */
@Composable
fun OverlayDock(
    items: List<Item>,
    apps: Map<AppKey, AppEntry>,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val corner = if (theme.dockStyle == DockStyle.PILL) theme.dockHeight / 2 else theme.moduleRadius + 8.dp
    Row(
        modifier = Modifier
            .width(SLOT_WIDTH * theme.dockSlots)
            .height(theme.dockHeight)
            // 重ねる面は、レールを描かないテーマでも下地が要る。無いと他のアプリの上で読めない
            .moduleSurface(corner = corner, fill = theme.colors.dock.copy(alpha = maxOf(theme.colors.dock.alpha, MIN_ALPHA)))
            .padding(theme.dockPadding),
    ) {
        repeat(theme.dockSlots) { slot ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (val item = items.getOrNull(slot)) {
                    is AppItem -> {
                        val entry = apps[item.key]
                        AppCell(
                            entry = entry,
                            fallback = item.fallbackLabel(),
                            showLabel = false,
                            modifier = Modifier.launchOnTap(entry, onLaunch),
                        )
                    }
                    is FolderItem -> FolderCell(folder = item, apps = apps, members = item.apps, compact = true)
                    else -> Unit
                }
            }
        }
    }
}

/** タップで起動する。起動の広がりの起点に自分の矩形を渡す。 */
@Composable
private fun Modifier.launchOnTap(entry: AppEntry?, onLaunch: (AppEntry, Rect) -> Unit): Modifier {
    if (entry == null) return this
    var bounds = Rect.Zero
    val onLaunchNow = rememberUpdatedState(onLaunch)
    return this
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .pointerInput(entry.key) { detectTapGestures { onLaunchNow.value(entry, bounds) } }
}

/** 1 スロットの幅。ホームのドックの上限(SLOT_MAX_WIDTH)と同じ。 */
private val SLOT_WIDTH = 72.dp

/** レールの塗りの下限。すりガラスや透明のテーマでも、他のアプリの上では読める濃さにする。 */
private const val MIN_ALPHA = 0.85f
