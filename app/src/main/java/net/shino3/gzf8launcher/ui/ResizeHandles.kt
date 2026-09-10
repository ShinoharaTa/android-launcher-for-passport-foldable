package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.roundToInt

/**
 * 編集モード中にウィジェットの四隅へ出すつまみ(#47)。
 *
 * 斜めに引くと幅と高さが同時に変わる。セルの大きさで割って段数に直し、
 * 変わった瞬間だけ呼び出し側へ伝える。置けない大きさは呼び出し側が拒否するので、
 * ここは「何段ぶん引いたか」だけを見る。
 *
 * 辺の中央にはつまみを出さない。1x1 のウィジェットでつまみ同士が重なるためである。
 */
@Composable
fun BoxScope.ResizeHandles(
    /** セル 1 つの大きさ px。引いた距離を段数に直すのに使う。 */
    cellPx: Float,
    /** 幅と高さの変化を段数で渡す。受け付けたら true を返す。 */
    onResize: (dw: Int, dh: Int) -> Boolean,
) {
    val theme = LocalLauncherTheme.current
    val density = LocalDensity.current
    val onResizeNow = rememberUpdatedState(onResize)

    // 四隅。左と上のつまみは、引く向きと段数の増減が逆になる
    listOf(
        Triple(Alignment.TopStart, -1, -1),
        Triple(Alignment.TopEnd, 1, -1),
        Triple(Alignment.BottomStart, -1, 1),
        Triple(Alignment.BottomEnd, 1, 1),
    ).forEach { (align, sx, sy) ->
        val nudge = with(density) { (HANDLE_TAP / 2).toPx() }
        Box(
            modifier = Modifier
                .align(align)
                .offset(
                    x = with(density) { (if (sx < 0) -nudge else nudge).toDp() },
                    y = with(density) { (if (sy < 0) -nudge else nudge).toDp() },
                )
                .size(HANDLE_TAP)
                .pointerInput(cellPx, sx, sy) {
                    // 引いた合計を持ち、セルをまたぐたびに 1 段ぶん伝える
                    var acc = 0f to 0f
                    var sent = 0 to 0
                    detectDragGestures(
                        onDragStart = { acc = 0f to 0f; sent = 0 to 0 },
                        onDragEnd = { acc = 0f to 0f; sent = 0 to 0 },
                        onDragCancel = { acc = 0f to 0f; sent = 0 to 0 },
                    ) { change, amount ->
                        change.consume()
                        if (cellPx <= 0f) return@detectDragGestures
                        acc = (acc.first + amount.x) to (acc.second + amount.y)
                        val wantW = ((acc.first * sx) / cellPx).roundToInt()
                        val wantH = ((acc.second * sy) / cellPx).roundToInt()
                        val dw = wantW - sent.first
                        val dh = wantH - sent.second
                        if (dw == 0 && dh == 0) return@detectDragGestures
                        // 受け付けられた分だけ「送った」ことにする。拒否された段は、指を戻せばまた試される
                        if (onResizeNow.value(dw, dh)) sent = wantW to wantH
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(HANDLE_DOT)
                    .clip(CircleShape)
                    .background(theme.colors.surface.copy(alpha = 1f))
                    .border(2.dp, theme.colors.accent, CircleShape),
            )
        }
    }
}

/**
 * 編集モードでウィジェットを選ぶための枠。
 * 選ばれているあいだだけアクセント色の縁を出し、つまみの持ち主を示す。
 */
@Composable
fun BoxScope.ResizeOutline() {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(1.5.dp, theme.colors.accent, theme.shapeOf(theme.moduleRadius)),
    )
}

/** つまみの当たり判定。 */
private val HANDLE_TAP = 40.dp

/** つまみの見た目の大きさ。 */
private val HANDLE_DOT = 16.dp
