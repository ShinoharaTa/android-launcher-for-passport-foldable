package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import net.shino3.gzf8launcher.model.PlacedItem
import net.shino3.gzf8launcher.model.Zone
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.dropTarget

/**
 * ゾーンのアイテムを正方セルのグリッドに配置する。
 * セル幅は利用可能な幅を列数で割って決め、行数は高さから自然に決まる。
 * 自身の矩形をドロップ先として登録する。
 */
@Composable
fun HomeGrid(
    zone: Zone,
    zoneId: ZoneId,
    columns: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, placed: PlacedItem) -> Unit,
) {
    Layout(
        modifier = modifier.dropTarget("grid:$zoneId") { DropTarget.Grid(zoneId, it, columns) },
        content = { zone.items.forEachIndexed { index, placed -> Box { cell(index, placed) } } },
    ) { measurables, constraints ->
        val cellPx = constraints.maxWidth / columns
        val placeables = measurables.mapIndexed { index, measurable ->
            val p = zone.items[index]
            measurable.measure(Constraints.fixed(p.w * cellPx, p.h * cellPx))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val p = zone.items[index]
                placeable.placeRelative(p.col * cellPx, p.row * cellPx)
            }
        }
    }
}
