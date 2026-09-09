package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
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
 * ドロワーの入口は上スワイプに揃えたので、ボタンは持たない(#25)。
 * 左右の余白はページのグリッドと同じ値を受け取り、レールの幅をグリッドに揃える(#34)。
 * 高さとレール内の余白はテーマと設定で決まる。
 */
@Composable
fun Dock(
    items: List<Item>,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    sidePadding: Dp,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius + 8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = sidePadding, end = sidePadding, top = DOCK_GAP)
            .height(theme.dockHeight)
            .clip(shape)
            .background(theme.colors.dock)
            .border(1.dp, theme.outline, shape)
            .dropTarget("dock") { DropTarget.Dock(it, theme.dockSlots) }
            .padding(theme.dockPadding),
    ) {
        repeat(theme.dockSlots) { slot ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                items.getOrNull(slot)?.let { item ->
                    ItemView(item, ItemRef.Dock(slot), apps, actions, showLabel = false)
                }
            }
        }
    }
}

/** ページとレールのあいだの隙間。レールの下の余白は画面の下の余白(insets.bottom)が担う。 */
private val DOCK_GAP = 8.dp
