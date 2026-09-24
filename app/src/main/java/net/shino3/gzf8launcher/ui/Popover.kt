package net.shino3.gzf8launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.roundToInt

/**
 * 触ったアイテムの近くに出す吹き出し(#45)。
 *
 * 画面中央の箱だと、どれを触ったのか分からなくなる。アイテムの上下どちらかに寄せて置き、
 * アイテム側に小さな三角を向けることで、対応を目で追えるようにする。
 *
 * 置き場所は、上に入るなら上、入らなければ下。左右は画面からはみ出さないところまで寄せる。
 * 三角はアイテムの中心に向けるが、吹き出しの角に食い込まない範囲に留める。
 */
@Composable
fun Popover(
    visible: Boolean,
    /** 触ったアイテムの矩形(root 座標)。空なら画面中央に出す。 */
    anchor: Rect?,
    hidden: Boolean,
    onDismiss: () -> Unit,
    /** 三角の色。中身の塗りと同じものを渡す。 */
    fill: Color = LocalLauncherTheme.current.colors.surface.copy(alpha = 1f),
    content: @Composable () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val scrim = if (theme.light) Color(0x66FFFFFF) else Color(0x8C000000)
    // animateFloatAsState は初回に目標値から始まるので、0 から動かせる Animatable を使う(#23)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        anim.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) OverlayAnim.ENTER_MILLIS else OverlayAnim.EXIT_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    val progress = anim.value
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (hidden) 0f else 1f)
            .background(scrim.copy(alpha = scrim.alpha * progress))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { GAP.toPx() }
        val edgePx = with(density) { EDGE.toPx() }
        val tailPx = with(density) { TAIL.toPx() }

        // 吹き出しの大きさは中身が決める。測れるまでは画面外に置いて、ちらつかせない
        var size by remember { mutableStateOf(Offset.Zero) }
        val placed = remember(anchor, size, screenW, screenH) {
            place(anchor, size, screenW, screenH, gapPx, edgePx, tailPx)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(placed.x.roundToInt(), placed.y.roundToInt()) }
                .onSizeChanged { size = Offset(it.width.toFloat(), it.height.toFloat()) }
                .graphicsLayer {
                    // 三角の根元を原点にして開く。アイテムから伸びてきたように見える
                    transformOrigin = TransformOrigin(
                        if (size.x > 0f) (placed.tailX / size.x).coerceIn(0f, 1f) else 0.5f,
                        if (placed.below) 0f else 1f,
                    )
                    val s = 0.88f + 0.12f * progress
                    scaleX = s
                    scaleY = s
                    alpha = progress
                }
                // 三角。吹き出しの外側に描くので、中身の余白を食わない
                .drawBehind {
                    if (placed.tailX <= 0f) return@drawBehind
                    val t = tailPx
                    val y = if (placed.below) 0f else this.size.height
                    val dir = if (placed.below) -1f else 1f
                    val path = Path().apply {
                        moveTo(placed.tailX - t, y)
                        lineTo(placed.tailX, y + t * dir)
                        lineTo(placed.tailX + t, y)
                        close()
                    }
                    drawPath(path, fill)
                }
                // 吹き出しの中を触っても閉じない
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            content()
        }
    }
}

/** 吹き出しの置き場所。x/y は左上、tailX は吹き出し内での三角の横位置。 */
private data class Placement(val x: Float, val y: Float, val tailX: Float, val below: Boolean)

/**
 * アイテムの上に入るなら上、入らなければ下に置く。
 * 左右は画面の縁から edge だけ内側に収め、三角はアイテムの中心へ向ける。
 */
private fun place(
    anchor: Rect?,
    size: Offset,
    screenW: Float,
    screenH: Float,
    gap: Float,
    edge: Float,
    tail: Float,
): Placement {
    if (anchor == null || anchor.isEmpty || size.x <= 0f) {
        return Placement((screenW - size.x) / 2f, (screenH - size.y) / 2f, 0f, below = true)
    }
    val above = anchor.top - gap - size.y
    val below = above < edge
    val y = if (below) (anchor.bottom + gap).coerceAtMost(screenH - size.y - edge) else above
    val x = (anchor.center.x - size.x / 2f).coerceIn(edge, (screenW - size.x - edge).coerceAtLeast(edge))
    // 三角は角丸に食い込まないところまで
    val tailX = (anchor.center.x - x).coerceIn(tail * 2f, (size.x - tail * 2f).coerceAtLeast(tail * 2f))
    return Placement(x, y, tailX, below)
}

/** アイテムと吹き出しのあいだの隙間。 */
private val GAP = 10.dp

/** 画面の縁から空ける距離。 */
private val EDGE = 12.dp

/** 三角の大きさ(底辺の半分)。 */
private val TAIL = 7.dp
