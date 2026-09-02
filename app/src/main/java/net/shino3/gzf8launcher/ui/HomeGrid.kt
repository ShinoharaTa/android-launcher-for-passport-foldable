package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import net.shino3.gzf8launcher.model.PlacedItem
import net.shino3.gzf8launcher.model.Zone

/**
 * ゾーンのアイテムを正方セルのグリッドに配置する。
 * セル幅は利用可能な幅を列数で割って決め、行数は高さから自然に決まる。
 */
@Composable
fun HomeGrid(
    zone: Zone,
    columns: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (PlacedItem) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { zone.items.forEach { placed -> Box { cell(placed) } } },
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
