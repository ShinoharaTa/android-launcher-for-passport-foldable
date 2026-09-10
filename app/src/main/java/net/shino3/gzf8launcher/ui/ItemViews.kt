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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.widget.AppWidgetView
import net.shino3.gzf8launcher.widget.NativeWidgetHost

/** アイテムに対する操作。種類ごとの動作はここに閉じる(docs/04)。 */
class ItemActions(
    val onLaunch: (AppEntry, Rect) -> Unit,
    val onOpenFolder: (ItemRef, Rect) -> Unit,
    /** 編集モードの ✕ で消す(#46)。 */
    val onRemove: (ItemRef) -> Unit = {},
    /** 固定したショートカットの表示名とアイコンを引き直す。 */
    val resolveShortcut: suspend (ShortcutItem) -> ShortcutEntry? = { null },
    val onLaunchShortcut: (ShortcutItem, Rect) -> Unit = { _, _ -> },
)

fun AppItem.fallbackLabel(): String = component.substringBefore('/').substringAfterLast('.')

/**
 * アイコンの形を Compose の Shape にしたもの。
 * アイコンの Bitmap は読み込みの段階で形に切ってあるので(IconRenderer、#32)、ここでは切らない。
 * 未インストールの印やフォルダの器など、自前で描く箱の輪郭に使う。
 */
@Composable
private fun iconShape(): Shape = LocalLauncherTheme.current.iconShape.asShape()

/**
 * 種類で描画を振り分け、長押しドラッグを付ける。
 * 編集モード中は揺らし、左上に ✕ を重ねる(#46)。
 */
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
    /** 揺れの位相をずらす種。並び順を渡す。 */
    seed: Int = 0,
) {
    if (LocalEditMode.current) {
        Box(modifier = Modifier.fillMaxSize()) {
            ItemBody(item, ref, apps, actions, Modifier.fillMaxSize().jiggle(seed), showLabel, w, h)
            // ✕ はセルの左上。アイテム本体より手前に置く
            Box(modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp)) {
                BoxScopeRemoveBadge { actions.onRemove(ref) }
            }
        }
        return
    }
    ItemBody(item, ref, apps, actions, modifier, showLabel, w, h)
}

@Composable
private fun ItemBody(
    item: Item,
    ref: ItemRef,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier,
    showLabel: Boolean,
    w: Int,
    h: Int,
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
                    onTap = entry?.let { e -> { bounds -> actions.onLaunch(e, bounds) } },
                ),
            )
        }
        is FolderItem -> FolderCell(
            folder = item,
            apps = apps,
            members = item.apps,
            compact = w == 1 && h == 1,
            showLabel = showLabel,
            modifier = modifier.dragSource(
                payload = DragPayload(item, ref, null, item.name, w, h),
                onTap = { bounds -> actions.onOpenFolder(ref, bounds) },
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
                onTap = { bounds -> actions.onLaunchShortcut(item, bounds) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = minOf(maxWidth, maxHeight) * theme.iconScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val icon = entry?.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = label, modifier = Modifier.size(iconSize))
            } else {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(shape)
                        .border(1.dp, theme.colors.line, shape),
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
                Image(bitmap = entry.icon, contentDescription = entry.label, modifier = Modifier.size(iconSize))
            } else {
                // 未インストールのアプリ。JSON の書き間違いを見つけられるように場所を空けたまま印を出す
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(shape)
                        .border(1.dp, theme.colors.line, shape),
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
    val iconShape = iconShape()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            // ウィジェットと同じ面にする。形も枠も影もテーマが決める(#40)
            .moduleSurface(fill = theme.colors.dock)
            .padding(6.dp),
    ) {
        val cols = theme.folderColumns
        val miniSize = (maxWidth - 12.dp) / cols * 0.78f
        Column(modifier = Modifier.fillMaxSize()) {
            // フォルダ名は利用者が付けるので日本語が入る。等幅ではなく UI 書体で描く(#32)
            Text(
                text = folder.name,
                color = theme.colors.accent,
                fontFamily = theme.uiFont,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            members.take(cols * cols).chunked(cols).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    row.forEach { app ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val entry = apps[app.key]
                            if (entry != null) {
                                Image(bitmap = entry.icon, contentDescription = entry.label, modifier = Modifier.size(miniSize))
                            } else {
                                Box(modifier = Modifier.size(miniSize).border(1.dp, theme.colors.line, iconShape))
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
                    .clip(iconShape)
                    .background(theme.colors.dock)
                    .border(1.dp, theme.outline, iconShape),
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
                                        modifier = Modifier.padding(1.dp).size(mini),
                                    )
                                } else {
                                    Box(modifier = Modifier.padding(1.dp).size(mini).border(1.dp, theme.colors.line, iconShape))
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
    val shape = theme.shapeOf(theme.moduleRadius)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .border(theme.outlineWidth, theme.colors.line, shape)
            .padding(8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(caption, color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp)
    }
}
