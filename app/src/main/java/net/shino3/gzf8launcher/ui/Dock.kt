package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.dropTarget

/** カバーとメインで共有する 1 本のレール(docs/04)。 */
@Composable
fun Dock(
    items: List<Item>,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.folder)
            .dropTarget("dock") { DropTarget.Dock(it, theme.dockSlots) },
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
