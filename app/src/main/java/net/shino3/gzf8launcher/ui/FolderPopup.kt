package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * フォルダを開いたときのポップアップ(docs/04 で両画面ともポップアップに決めた)。
 * ドラッグでホームへ運び出せるように、別ウィンドウの Dialog ではなく同じウィンドウの重ね描きにする。
 * 中身は手動の並びだけ。規則(最近、よく使う)は #29 でドロワーの絞り込みに移した。
 */
@Composable
fun FolderPopup(
    folderRef: ItemRef,
    visible: Boolean,
    source: Rect?,
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    hidden: Boolean,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val folder = LayoutEditor.itemAt(layout, folderRef) as? FolderItem
    LaunchedEffect(folder == null) { if (folder == null) onDismiss() }
    if (folder == null) return
    val shape = RoundedCornerShape(theme.moduleRadius + 6.dp)

    OverlayScaffold(visible = visible, source = source, hidden = hidden, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 440.dp)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("FOLDER // ", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 12.sp)
                // 名前は利用者が付けるので日本語が入る。UI 書体で描く(#32)
                BasicTextField(
                    value = folder.name,
                    onValueChange = onRename,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.colors.accent, fontFamily = theme.uiFont, fontSize = 14.sp),
                    cursorBrush = SolidColor(theme.colors.accent),
                    modifier = Modifier.weight(1f),
                )
            }
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(folder.apps, key = { i, app -> "${app.component}@${app.user}#$i" }) { index, app ->
                    ItemView(
                        item = app,
                        ref = ItemRef.InFolder(folderRef, index),
                        apps = apps,
                        actions = ItemActions(
                            onLaunch = { entry, bounds -> actions.onLaunch(entry, bounds); onDismiss() },
                            onOpenFolder = { _, _ -> },
                            resolveShortcut = actions.resolveShortcut,
                            onLaunchShortcut = { item, bounds -> actions.onLaunchShortcut(item, bounds); onDismiss() },
                        ),
                        modifier = Modifier.aspectRatio(0.9f),
                        showLabel = true,
                    )
                }
            }
        }
    }
}
