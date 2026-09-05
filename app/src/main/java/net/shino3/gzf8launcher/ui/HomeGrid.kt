package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.model.PlacedItem
import net.shino3.gzf8launcher.model.Zone
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DropTarget
import net.shino3.gzf8launcher.ui.drag.LocalDropPreview
import net.shino3.gzf8launcher.ui.drag.dropTarget

/**
 * ゾーンのアイテムを正方セルのグリッドに配置する。
 * セル幅は利用可能な幅を列数で割って決める。高さは置かれたアイテムの段数に余白を足した分だけ取り、
 * 縦スクロールの中で伸びる(#19)。minRows は画面の高さぶんを下限として渡す。
 * 自身の矩形をドロップ先として登録し、ドラッグ中は落ちる位置に枠を描く(#25)。
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
    val preview = LocalDropPreview.current?.takeIf { it.zone == zoneId }
    Layout(
        modifier = modifier.dropTarget("grid:$zoneId") { DropTarget.Grid(zoneId, it, columns, fixedRows) },
        content = {
            zone.items.forEachIndexed { index, placed -> Box { cell(index, placed) } }
            // 最後の子として、落ちる位置の枠
            if (preview != null) DropPreviewFrame(preview.kind)
        },
    ) { measurables, constraints ->
        val cellPx = constraints.maxWidth / columns
        val rows = fixedRows ?: maxOf(minRows, zone.occupiedRows + SLACK_ROWS)
        val height = (rows * cellPx).coerceIn(constraints.minHeight, constraints.maxHeight)
        val itemCount = zone.items.size
        fun placementAt(index: Int) = if (index < itemCount) zone.items[index].placement else preview!!.placement
        val placeables = measurables.mapIndexed { index, measurable ->
            val p = placementAt(index)
            measurable.measure(Constraints.fixed(p.w * cellPx, p.h * cellPx))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val p = placementAt(index)
                placeable.placeRelative(p.col * cellPx, p.row * cellPx)
            }
        }
    }
}

/**
 * 落ちる位置の枠。置く、押しのける、まとめる、拒否を描き分ける。
 * まとめるときは輪にして、フォルダになることを伝える。
 */
@Composable
private fun DropPreviewFrame(kind: LayoutEditor.DropKind) {
    val theme = LocalLauncherTheme.current
    val reject = Color(0xFFE5484D)
    val color = if (kind == LayoutEditor.DropKind.REJECT) reject else theme.colors.accent
    val shape = if (kind == LayoutEditor.DropKind.MERGE) CircleShape else RoundedCornerShape(theme.moduleRadius)
    val inset = if (kind == LayoutEditor.DropKind.MERGE) 10.dp else 3.dp
    val stroke = if (kind == LayoutEditor.DropKind.MERGE) 2.dp else 1.5.dp
    val fill = if (kind == LayoutEditor.DropKind.PUSH) 0.10f else 0.04f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(inset)
            .background(color.copy(alpha = fill), shape)
            .border(stroke, color, shape),
    )
}

/** 末尾のアイテムの下に、いつも置ける空きを残す段数。 */
private const val SLACK_ROWS = 2
