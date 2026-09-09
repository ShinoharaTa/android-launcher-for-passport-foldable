package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.model.ShortcutItem
import net.shino3.gzf8launcher.data.ShortcutEntry
import net.shino3.gzf8launcher.theme.IconShape
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.widget.AppWidgetView
import net.shino3.gzf8launcher.widget.NativeWidgetHost

/** アイテムに対する操作。種類ごとの動作はここに閉じる(docs/04)。 */
class ItemActions(
    val onLaunch: (AppEntry) -> Unit,
    val onOpenFolder: (ItemRef) -> Unit,
    /** 規則つきフォルダの中身を解決する。 */
    val resolveFolder: (FolderItem) -> List<AppItem>,
    /** 固定したショートカットの表示名とアイコンを引き直す。 */
    val resolveShortcut: suspend (ShortcutItem) -> ShortcutEntry? = { null },
    val onLaunchShortcut: (ShortcutItem) -> Unit = {},
)

fun AppItem.fallbackLabel(): String = component.substringBefore('/').substringAfterLast('.')

/** テーマが指定するアイコンの形。SYSTEM はアプリが持つ形をそのまま出す。 */
@Composable
private fun iconShape(): Shape? = when (LocalLauncherTheme.current.iconShape) {
    IconShape.SYSTEM -> null
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> RoundedCornerShape(28)
}

/** 種類で描画を振り分け、長押しドラッグを付ける。 */
@Composable
fun ItemView(
    item: Item,
    ref: ItemRef,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    showLabel: Boolean = LocalLauncherTheme.current.showLabels,
    w: Int = 1,
    h: Int = 1,
) {
    when (item) {
        is AppItem -> {
            val entry = apps[item.key]
            AppCell(
                entry = entry,
                fallback = item.fallbackLabel(),
                showLabel = showLabel,
                modifier = modifier.dragSource(
                    payload = DragPayload(item, ref, entry?.icon, entry?.label ?: item.fallbackLabel(), w, h),
                    onTap = entry?.let { e -> { actions.onLaunch(e) } },
                ),
            )
        }
        is FolderItem -> FolderCell(
            folder = item,
            apps = apps,
            members = actions.resolveFolder(item),
            compact = w == 1 && h == 1,
            showLabel = showLabel,
            modifier = modifier.dragSource(
                payload = DragPayload(item, ref, null, item.name, w, h),
                onTap = { actions.onOpenFolder(ref) },
            ),
        )
        is NativeWidgetItem -> NativeWidgetHost(
            item = item,
            modifier = modifier.dragSource(DragPayload(item, ref, null, item.widget, w, h)),
        )
        is ShortcutItem -> ShortcutCell(
            item = item,
            showLabel = showLabel,
            actions = actions,
            modifier = modifier,
            ref = ref,
            w = w,
            h = h,
        )
        is AppWidgetItem -> AppWidgetView(
            item = item,
            payload = DragPayload(item, ref, null, item.provider.substringAfterLast('.').uppercase(), w, h),
            modifier = modifier,
        )
    }
}

/** ホームに固定したショートカット。表示名とアイコンはアプリ側から引き直す。 */
@Composable
fun ShortcutCell(
    item: ShortcutItem,
    showLabel: Boolean,
    actions: ItemActions,
    ref: ItemRef,
    modifier: Modifier = Modifier,
    w: Int = 1,
    h: Int = 1,
) {
    val entry by produceState<ShortcutEntry?>(initialValue = null, item) {
        value = actions.resolveShortcut(item)
    }
    val theme = LocalLauncherTheme.current
    val shape = iconShape()
    val label = entry?.label ?: item.label.ifEmpty { item.shortcutId }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .dragSource(
                payload = DragPayload(item, ref, entry?.icon, label, w, h),
                onTap = { actions.onLaunchShortcut(item) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = minOf(maxWidth, maxHeight) * theme.iconScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val icon = entry?.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize).then(if (shape != null) Modifier.clip(shape) else Modifier),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(shape ?: RoundedCornerShape(22))
                        .border(1.dp, theme.colors.line, shape ?: RoundedCornerShape(22)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("→", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 14.sp)
                }
            }
            if (showLabel) {
                Text(
                    text = label,
                    color = theme.colors.text,
                    fontFamily = theme.uiFont,
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

@Composable
fun AppCell(
    entry: AppEntry?,
    fallback: String,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val shape = iconShape()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = minOf(maxWidth, maxHeight) * theme.iconScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (entry != null) {
                Image(
                    bitmap = entry.icon,
                    contentDescription = entry.label,
                    modifier = Modifier
                        .size(iconSize)
                        .then(if (shape != null) Modifier.clip(shape) else Modifier),
                )
            } else {
                // 未インストールのアプリ。JSON の書き間違いを見つけられるように場所を空けたまま印を出す
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(shape ?: RoundedCornerShape(22))
                        .border(1.dp, theme.colors.line, shape ?: RoundedCornerShape(22)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 14.sp)
                }
            }
            if (showLabel) {
                Text(
                    text = entry?.label ?: fallback,
                    color = theme.colors.text,
                    fontFamily = theme.uiFont,
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

/**
 * フォルダの表面。中身のアイコンを小さく並べて見せる。
 * 1×1(アプリ棚やドック)のときは名前を出さず、アイコン大の角丸の中に 4 つまで並べる(#16)。
 */
@Composable
fun FolderCell(
    folder: FolderItem,
    apps: Map<AppKey, AppEntry>,
    members: List<AppItem>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showLabel: Boolean = false,
) {
    if (compact) {
        CompactFolderCell(folder, apps, members, showLabel, modifier)
        return
    }
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius)
    val iconShape = iconShape()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(theme.colors.dock)
            .border(1.dp, theme.outline, shape)
            .then(if (theme.decor.cornerBrackets) Modifier.cornerBrackets(theme.colors.accent) else Modifier)
            .padding(6.dp),
    ) {
        val cols = theme.folderColumns
        val miniSize = (maxWidth - 12.dp) / cols * 0.78f
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = folder.name,
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            members.take(cols * cols).chunked(cols).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    row.forEach { app ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val entry = apps[app.key]
                            if (entry != null) {
                                Image(
                                    bitmap = entry.icon,
                                    contentDescription = entry.label,
                                    modifier = Modifier
                                        .size(miniSize)
                                        .then(if (iconShape != null) Modifier.clip(iconShape) else Modifier),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(miniSize)
                                        .border(1.dp, theme.colors.line, iconShape ?: RoundedCornerShape(22)),
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

/** 1 セルのフォルダ。アプリのアイコンと同じ大きさの角丸に、中身を 2×2 で見せる。 */
@Composable
private fun CompactFolderCell(
    folder: FolderItem,
    apps: Map<AppKey, AppEntry>,
    members: List<AppItem>,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val iconShape = iconShape()
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val iconSize = minOf(maxWidth, maxHeight) * theme.iconScale
        val mini = iconSize * 0.36f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(iconShape ?: RoundedCornerShape(24))
                    .background(theme.colors.dock)
                    .border(1.dp, theme.outline, iconShape ?: RoundedCornerShape(24)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    members.take(4).chunked(2).forEach { row ->
                        Row {
                            row.forEach { app ->
                                val entry = apps[app.key]
                                if (entry != null) {
                                    Image(
                                        bitmap = entry.icon,
                                        contentDescription = entry.label,
                                        modifier = Modifier
                                            .padding(1.dp)
                                            .size(mini)
                                            .then(if (iconShape != null) Modifier.clip(iconShape) else Modifier),
                                    )
                                } else {
                                    Box(modifier = Modifier.padding(1.dp).size(mini).border(1.dp, theme.colors.line, RoundedCornerShape(22)))
                                }
                            }
                        }
                    }
                }
            }
            if (showLabel) {
                Text(
                    text = folder.name,
                    color = theme.colors.text,
                    fontFamily = theme.uiFont,
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

/** ウィジェットが解決できないときの仮表示。 */
@Composable
fun WidgetPlaceholder(caption: String, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .border(1.dp, theme.colors.line, shape)
            .padding(8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(caption, color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp)
    }
}
