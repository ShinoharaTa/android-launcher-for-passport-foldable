package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 画面全体に薄い横線を重ねる。触れないように装飾専用の層に置く。
 *
 * 四隅の L 字の飾り(cornerBrackets)はここにあったが、#32 で外した。
 * 角丸の箱に重ねると、角だけ明るさが段になって切り替わり、枠線の一部が浮いて見えるためである。
 * 枠は一様な細い線だけにする。
 */
@Composable
fun Scanlines(color: Color, spacing: Dp = 3.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val step = spacing.toPx().coerceAtLeast(1f)
                onDrawWithContent {
                    drawContent()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
            },
    )
}
