package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.ShortcutEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.model.ShortcutItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource

/**
 * 長押しして動かさずに離したときのメニュー。
 * アプリの場合は、そのアプリが持つ Android のショートカットも並べる(#11)。
 * 触ったアイテムの近くに吹き出しで出す(#45)。ウィジェットの大きさは編集モードのつまみで変える(#47)。
 */
@Composable
fun ItemMenu(
    payload: DragPayload,
    visible: Boolean,
    source: Rect?,
    layout: Layout,
    shortcuts: List<ShortcutEntry>,
    hidden: Boolean,
    onAppInfo: (AppItem) -> Unit,
    onUninstall: (AppItem) -> Unit,
    onOpenFolder: (ItemRef) -> Unit,
    onRemove: (ItemRef) -> Unit,
    onLaunchShortcut: (ShortcutItem, Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val shape = theme.shapeOf(theme.moduleRadius + 4.dp)
    val ref = payload.source
    Popover(visible = visible, anchor = source, hidden = hidden, onDismiss = onDismiss, fill = theme.colors.surface.copy(alpha = 1f)) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .clip(shape)
                .background(theme.colors.surface.copy(alpha = 1f))
                // 枠を消すテーマでも、浮いている面には縁を残す。無いと下地と溶ける
                .border(1.dp, if (theme.decor.outlines) theme.outline else theme.colors.line, shape)
                .padding(vertical = 8.dp),
        ) {
            // アプリ名は日本語が入るので UI 書体(#32)
            Text(
                text = payload.label,
                color = theme.colors.accent,
                fontFamily = theme.uiFont,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (shortcuts.isNotEmpty()) {
                Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    shortcuts.forEach { entry ->
                        ShortcutRow(entry, onLaunchShortcut)
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(theme.colors.line),
                )
            }
            when (val item = payload.item) {
                is AppItem -> {
                    MenuRow("APP INFO") { onAppInfo(item); onDismiss() }
                    // 端末の確認ダイアログに任せる。消えたアプリはアプリ一覧の更新でホームからも外れる(#36)
                    MenuRow("UNINSTALL") { onUninstall(item); onDismiss() }
                }
                is FolderItem -> if (ref != null) MenuRow("OPEN / RENAME") { onOpenFolder(ref) }
                // 大きさは編集モードの四隅のつまみで変える(#47)
                is NativeWidgetItem, is AppWidgetItem -> if (ref != null) {
                    val p = LayoutEditor.placementOf(layout, ref)
                    Text(
                        text = "SIZE ${p?.w ?: payload.w} x ${p?.h ?: payload.h}  //  EDIT TO RESIZE",
                        color = theme.colors.textDim,
                        fontFamily = theme.monoFont,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                is ShortcutItem -> MenuRow("LAUNCH") { onLaunchShortcut(item, source ?: Rect.Zero); onDismiss() }
            }
            if (ref != null) MenuRow("REMOVE", accent = true) { onRemove(ref); onDismiss() }
        }
    }
}

/** ショートカット 1 件。タップで起動、長押しドラッグでホームに固定できる。 */
@Composable
private fun ShortcutRow(entry: ShortcutEntry, onLaunch: (ShortcutItem, Rect) -> Unit) {
    val theme = LocalLauncherTheme.current
    val item = entry.toItem()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dragSource(
                payload = DragPayload(item, null, entry.icon, entry.label),
                onTap = { bounds -> onLaunch(item, bounds) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val icon = entry.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = entry.label, modifier = Modifier.size(24.dp))
        } else {
            Box(modifier = Modifier.size(24.dp).border(1.dp, theme.colors.line, RoundedCornerShape(6.dp)))
        }
        Text(
            text = entry.label,
            color = theme.colors.text,
            fontFamily = theme.uiFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** ホームの空き領域を長押ししたときのメニュー。ウィジェットの追加と設定への入口(#25)。 */
@Composable
fun HomeMenu(
    visible: Boolean,
    source: Rect?,
    onOpenWallpaper: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val shape = theme.shapeOf(theme.moduleRadius + 4.dp)
    // ホームメニューも同じ吹き出しに揃える。空き領域を触った点から伸びる(#45)
    Popover(visible = visible, anchor = source, hidden = false, onDismiss = onDismiss, fill = theme.colors.surface.copy(alpha = 1f)) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .clip(shape)
                .background(theme.colors.surface.copy(alpha = 1f))
                // 枠を消すテーマでも、浮いている面には縁を残す。無いと下地と溶ける
                .border(1.dp, if (theme.decor.outlines) theme.outline else theme.colors.line, shape)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "HOME",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            // 壁紙は端末の画面に移るのでここで閉じる。他の二つは別の重ね描きに移るので閉じない(閉じると移った先まで消える)
            MenuRow("WALLPAPER") { onOpenWallpaper(); onDismiss() }
            MenuRow("WIDGETS") { onOpenWidgets() }
            MenuRow("SETTINGS") { onOpenSettings() }
        }
    }
}
