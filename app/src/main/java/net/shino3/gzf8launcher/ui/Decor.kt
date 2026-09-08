package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 四隅に L 字の線を描く。amber-terminal の計器らしさを出すための装飾。
 *
 * 角丸の箱では、直角の L 字を角に置くと丸めた枠線と交わって角だけ濃い塊になる(#32)。
 * radius に角丸の半径を渡すと、L 字そのものを角丸に沿わせ(弧 + 両脚を一筆で)、枠線の角の上にぴったり重ねる。
 * 枠線(border)は太さの半分だけ内側に描かれるので、こちらも同じだけ内側に寄せて線を揃える。
 */
fun Modifier.cornerBrackets(color: Color, radius: Dp = 0.dp, length: Dp = 10.dp, width: Dp = 1.dp): Modifier =
    drawBehind {
        val len = length.toPx()
        val sw = width.toPx()
        val hw = sw / 2f
        // 枠線の中心線に合わせた矩形と、その角の半径
        val l = hw
        val t = hw
        val r = size.width - hw
        val b = size.height - hw
        val rad = (radius.toPx() - hw).coerceAtLeast(0f)
        val d = rad * 2f

        val path = Path()
        // 左上: 左辺を上がり、弧を回って上辺へ
        path.moveTo(l, t + rad + len)
        path.lineTo(l, t + rad)
        if (rad > 0f) path.arcTo(Rect(l, t, l + d, t + d), 180f, 90f, forceMoveTo = false)
        path.lineTo(l + rad + len, t)
        // 右上
        path.moveTo(r - rad - len, t)
        path.lineTo(r - rad, t)
        if (rad > 0f) path.arcTo(Rect(r - d, t, r, t + d), 270f, 90f, forceMoveTo = false)
        path.lineTo(r, t + rad + len)
        // 右下
        path.moveTo(r, b - rad - len)
        path.lineTo(r, b - rad)
        if (rad > 0f) path.arcTo(Rect(r - d, b - d, r, b), 0f, 90f, forceMoveTo = false)
        path.lineTo(r - rad - len, b)
        // 左下
        path.moveTo(l + rad + len, b)
        path.lineTo(l + rad, b)
        if (rad > 0f) path.arcTo(Rect(l, b - d, l + d, b), 90f, 90f, forceMoveTo = false)
        path.lineTo(l, b - rad - len)

        drawPath(path, color, style = Stroke(width = sw))
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
