package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.theme.Texture

/**
 * 画面全体の下地に敷く模様(#40)。触れないように装飾専用の層に置く。
 *
 * もとは走査線だけだったものを、点の方眼、方眼、斜線、削り目に広げた。
 * どれも 1px の細い線か点で、下の壁紙を潰さない濃さにする。
 *
 * 四隅の L 字の飾り(cornerBrackets)はここにあったが、#32 で外した。
 * 角丸の箱に重ねると、角だけ明るさが段になって切り替わり、枠線の一部が浮いて見えるためである。
 */
@Composable
fun TextureOverlay(texture: Texture, color: Color, spacing: Dp = 3.dp) {
    if (texture == Texture.NONE) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val step = spacing.toPx().coerceAtLeast(1f)
                onDrawWithContent {
                    drawContent()
                    when (texture) {
                        Texture.NONE -> Unit
                        Texture.SCANLINES -> {
                            var y = 0f
                            while (y < size.height) {
                                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                y += step
                            }
                        }
                        // 点の方眼。電子ペーパーの方眼紙
                        Texture.DOTS -> {
                            val gap = step * DOT_GAP
                            var y = gap / 2
                            while (y < size.height) {
                                var x = gap / 2
                                while (x < size.width) {
                                    drawCircle(color, radius = 1f, center = Offset(x, y))
                                    x += gap
                                }
                                y += gap
                            }
                        }
                        // 方眼。図面の下敷き
                        Texture.GRID -> {
                            val gap = step * GRID_GAP
                            var x = 0f
                            while (x < size.width) {
                                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                x += gap
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                y += gap
                            }
                        }
                        // 斜線。計器の空き地に引く網
                        Texture.HATCH -> {
                            val gap = step * HATCH_GAP
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1f)
                                x += gap
                            }
                        }
                        // 縦の削り目。金属の面
                        Texture.BRUSHED -> {
                            var x = 0f
                            while (x < size.width) {
                                drawRect(color, topLeft = Offset(x, 0f), size = Size(1f, size.height))
                                x += step
                            }
                        }
                    }
                }
            },
    )
}

/** 点の間隔(走査線の間隔に対する倍率)。 */
private const val DOT_GAP = 5f

/** 方眼の間隔。 */
private const val GRID_GAP = 6f

/** 斜線の間隔。 */
private const val HATCH_GAP = 3f
