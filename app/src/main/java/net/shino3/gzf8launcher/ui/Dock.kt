package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * 左右の余白はページのグリッドと同じ値を受け取り、そこから dockInset だけ引っ込めて中央に置く(#34)。
 * グリッドいっぱいに広げると端が詰まって見えるので、レールはグリッドより少し狭くする。
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
    // 開いた横長の画面では、幅いっぱいに広げると 6 個が散って薄く見える。1 スロットあたりの幅に上限を置き、中央に寄せる
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = sidePadding + theme.dockInset, end = sidePadding + theme.dockInset, top = DOCK_GAP),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                // 上限を先に掛けてから広げる。逆にすると fillMaxWidth が幅を固定してしまい上限が効かない
                .widthIn(max = SLOT_MAX_WIDTH * theme.dockSlots)
                .fillMaxWidth()
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
}

/** ページとレールのあいだの隙間。レールの下の余白は画面の下の余白(insets.bottom)が担う。 */
private val DOCK_GAP = 8.dp

/** 1 スロットの幅の上限。カバー(スロット約 76dp)では効かず、開いた横長でだけレールを絞る。 */
private val SLOT_MAX_WIDTH = 104.dp
