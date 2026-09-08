package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 四隅に L 字の線を描く。amber-terminal の計器らしさを出すための装飾。
 *
 * 角丸の箱では、角そのものに置くと丸めた枠線と交わって角だけ濃い塊になる(#32)。
 * inset に角丸の半径を渡すと、弧を避けて直線部分に乗るので、枠線の上にアクセント色の目盛りとして重なる。
 */
fun Modifier.cornerBrackets(color: Color, inset: Dp = 0.dp, length: Dp = 10.dp, width: Dp = 1.dp): Modifier =
    drawBehind {
        val len = length.toPx()
        val off = inset.toPx()
        val stroke = Stroke(width = width.toPx())
        val w = size.width
        val h = size.height
        // 各角で、横線は角から inset だけ内側から始めて len 伸ばし、縦線も同じにする
        listOf(
            Triple(Offset(off, 0f), Offset(off + len, 0f), Offset(0f, off) to Offset(0f, off + len)),
            Triple(Offset(w - off, 0f), Offset(w - off - len, 0f), Offset(w, off) to Offset(w, off + len)),
            Triple(Offset(off, h), Offset(off + len, h), Offset(0f, h - off) to Offset(0f, h - off - len)),
            Triple(Offset(w - off, h), Offset(w - off - len, h), Offset(w, h - off) to Offset(w, h - off - len)),
        ).forEach { (hFrom, hTo, v) ->
            drawLine(color, hFrom, hTo, strokeWidth = stroke.width)
            drawLine(color, v.first, v.second, strokeWidth = stroke.width)
        }
    }

/** 画面全体に薄い横線を重ねる。触れないように装飾専用の層に置く。 */
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
