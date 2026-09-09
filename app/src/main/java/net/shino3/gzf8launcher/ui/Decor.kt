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
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** 四隅に L 字の線を描く。amber-terminal の計器らしさを出すための装飾。 */
fun Modifier.cornerBrackets(color: Color, length: Dp = 10.dp, width: Dp = 1.dp): Modifier =
    drawBehind {
        val len = length.toPx()
        val stroke = Stroke(width = width.toPx())
        val w = size.width
        val h = size.height
        listOf(
            Offset(0f, 0f) to listOf(Offset(len, 0f), Offset(0f, len)),
            Offset(w, 0f) to listOf(Offset(w - len, 0f), Offset(w, len)),
            Offset(0f, h) to listOf(Offset(len, h), Offset(0f, h - len)),
            Offset(w, h) to listOf(Offset(w - len, h), Offset(w, h - len)),
        ).forEach { (corner, ends) ->
            ends.forEach { end -> drawLine(color, corner, end, strokeWidth = stroke.width) }
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
