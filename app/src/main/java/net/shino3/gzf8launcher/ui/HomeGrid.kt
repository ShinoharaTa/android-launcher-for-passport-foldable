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
 * セル幅は利用可能な幅を列数で割って決める。高さは置かれたアイテムの段数に余白を足した分だけ取り、
 * 縦スクロールの中で伸びる(#19)。minRows は画面の高さぶんを下限として渡す。
 * 自身の矩形をドロップ先として登録する。
 */
@Composable
fun HomeGrid(
    zone: Zone,
    zoneId: ZoneId,
    columns: Int,
    modifier: Modifier = Modifier,
    minRows: Int = 0,
    /** 段数を固定する(アプリのページ)。null なら中身に合わせて伸びる(ウィジェット面)。 */
    fixedRows: Int? = null,
    cell: @Composable (index: Int, placed: PlacedItem) -> Unit,
) {
    Layout(
        modifier = modifier.dropTarget("grid:$zoneId") { DropTarget.Grid(zoneId, it, columns, fixedRows) },
        content = { zone.items.forEachIndexed { index, placed -> Box { cell(index, placed) } } },
    ) { measurables, constraints ->
        val cellPx = constraints.maxWidth / columns
        val rows = fixedRows ?: maxOf(minRows, zone.occupiedRows + SLACK_ROWS)
        val height = (rows * cellPx).coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeables = measurables.mapIndexed { index, measurable ->
            val p = zone.items[index]
            measurable.measure(Constraints.fixed(p.w * cellPx, p.h * cellPx))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val p = zone.items[index]
                placeable.placeRelative(p.col * cellPx, p.row * cellPx)
            }
        }
    }
}

/** 末尾のアイテムの下に、いつも置ける空きを残す段数。 */
private const val SLACK_ROWS = 2
