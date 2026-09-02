package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** アイテムに対する操作。種類ごとの動作はここに閉じる(docs/04)。 */
class ItemActions(
    val onLaunch: (AppEntry) -> Unit,
    val onOpenFolder: (FolderItem) -> Unit,
)

/** 種類で描画を振り分ける。 */
@Composable
fun ItemView(
    item: Item,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    showLabel: Boolean = LocalLauncherTheme.current.showLabels,
) {
    when (item) {
        is AppItem -> AppCell(
            entry = apps[item.key],
            fallback = item.component.substringBefore('/').substringAfterLast('.'),
            showLabel = showLabel,
            modifier = modifier,
            onClick = { entry -> actions.onLaunch(entry) },
        )
        is FolderItem -> FolderCell(item, apps, modifier) { actions.onOpenFolder(item) }
        is NativeWidgetItem -> WidgetPlaceholder("WIDGET // ${item.widget}", modifier)
        is AppWidgetItem -> WidgetPlaceholder("APPWIDGET // ${item.provider.substringBefore('/')}", modifier)
    }
}

@Composable
fun AppCell(
    entry: AppEntry?,
    fallback: String,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: (AppEntry) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = entry != null) { entry?.let(onClick) },
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = minOf(maxWidth, maxHeight) * theme.iconScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (entry != null) {
                Image(bitmap = entry.icon, contentDescription = entry.label, modifier = Modifier.size(iconSize))
            } else {
                // 未インストールのアプリ。JSON の書き間違いを見つけられるように場所を空けたまま印を出す
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(22))
                        .border(1.dp, theme.colors.line, RoundedCornerShape(22)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", color = theme.colors.textDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
            }
            if (showLabel) {
                Text(
                    text = entry?.label ?: fallback,
                    color = theme.colors.text,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

/** フォルダの表面。中身のアイコンを小さく並べて見せる。 */
@Composable
fun FolderCell(
    folder: FolderItem,
    apps: Map<AppKey, AppEntry>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(theme.colors.folder)
            .border(1.dp, theme.colors.line, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        val cols = theme.folderColumns
        val miniSize = (maxWidth - 12.dp) / cols * 0.78f
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = folder.name,
                color = theme.colors.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            folder.apps.take(cols * cols).chunked(cols).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    row.forEach { app ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val entry = apps[app.key]
                            if (entry != null) {
                                Image(bitmap = entry.icon, contentDescription = entry.label, modifier = Modifier.size(miniSize))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(miniSize)
                                        .border(1.dp, theme.colors.line, RoundedCornerShape(22)),
                                )
                            }
                        }
                    }
                    repeat(cols - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** ウィジェット基盤(マイルストーン 3・5)ができるまでの仮表示。 */
@Composable
fun WidgetPlaceholder(caption: String, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .border(1.dp, theme.colors.line, shape)
            .padding(8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(caption, color = theme.colors.textDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}
