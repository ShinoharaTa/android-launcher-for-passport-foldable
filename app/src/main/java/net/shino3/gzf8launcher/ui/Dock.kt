package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.dropTarget

/**
 * カバーとメインで共有する 1 本のレール(docs/04)。
 * 右端にドロワーを開くボタンを持つ。ホームが縦スクロールになったので、上ドラッグの代わりの入口(#19)。
 */
@Composable
fun Dock(
    items: List<Item>,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(theme.moduleRadius + 8.dp))
            .background(theme.colors.dock)
            .border(1.dp, theme.outline, RoundedCornerShape(theme.moduleRadius + 8.dp))
            .dropTarget("dock") { DropTarget.Dock(it, theme.dockSlots) },
    ) {
        repeat(theme.dockSlots) { slot ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                items.getOrNull(slot)?.let { item ->
                    ItemView(item, ItemRef.Dock(slot), apps, actions, showLabel = false)
                }
            }
        }
        DrawerButton(onOpenDrawer)
    }
}

/** 2×2 の点。全アプリ一覧の入口。 */
@Composable
private fun DrawerButton(onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(2) {
                Row {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(theme.colors.accent),
                        )
                    }
                }
            }
        }
    }
}
