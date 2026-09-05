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
    onOpenFolder: (ItemRef) -> Unit,
    onResize: (ItemRef, dw: Int, dh: Int) -> Unit,
    onRemove: (ItemRef) -> Unit,
    onLaunchShortcut: (ShortcutItem, Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius + 4.dp)
    val ref = payload.source
    OverlayScaffold(visible = visible, source = source, hidden = hidden, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = payload.label.uppercase(),
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 11.sp,
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
                is AppItem -> MenuRow("APP INFO") { onAppInfo(item); onDismiss() }
                is FolderItem -> if (ref != null) MenuRow("OPEN / RENAME") { onOpenFolder(ref) }
                is NativeWidgetItem, is AppWidgetItem -> if (ref != null) {
                    val p = LayoutEditor.placementOf(layout, ref)
                    Text(
                        text = "SIZE ${p?.w ?: payload.w} x ${p?.h ?: payload.h}",
                        color = theme.colors.textDim,
                        fontFamily = theme.monoFont,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        listOf("W-" to (-1 to 0), "W+" to (1 to 0), "H-" to (0 to -1), "H+" to (0 to 1)).forEach { (label, d) ->
                            Text(
                                text = label,
                                color = theme.colors.text,
                                fontFamily = theme.monoFont,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, theme.colors.line, RoundedCornerShape(6.dp))
                                    .pointerInput(label) { detectTapGestures { onResize(ref, d.first, d.second) } }
                                    .padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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

@Composable
private fun MenuRow(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Text(
        text = label,
        color = if (accent) theme.colors.accent else theme.colors.text,
        fontFamily = theme.monoFont,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** ホームの空き領域を長押ししたときのメニュー。ウィジェットの追加と設定への入口(#25)。 */
@Composable
fun HomeMenu(visible: Boolean, source: Rect?, onOpenWidgets: () -> Unit, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius + 4.dp)
    OverlayScaffold(visible = visible, source = source, hidden = false, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "HOME",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            // どちらも別の重ね描きに移る。ここで閉じると移った先まで消えてしまう
            MenuRow("WIDGETS") { onOpenWidgets() }
            MenuRow("SETTINGS") { onOpenSettings() }
        }
    }
}
