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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.roundToInt

/**
 * 編集モード中にウィジェットの角へ出すつまみ(#47)。
 *
 * 斜めに引くと幅と高さが同時に変わる。セルの大きさで割って段数に直し、
 * 変わった瞬間だけ呼び出し側へ伝える。置けない大きさは呼び出し側が拒否するので、
 * ここは「何段ぶん引いたか」だけを見る。拒否されたら振動で返す(原則 5)。
 *
 * 上や左のつまみは、引いた側の辺が指に付いてくるように、反対側の角を留めて原点を動かす(#53)。
 * 左上は ✕ の場所なので出さない。残りの三つで、どの辺も動かせる。
 * 辺の中央にもつまみを出さない。1x1 のウィジェットでつまみ同士が重なるためである。
 */
@Composable
fun BoxScope.ResizeHandles(
    /** セル 1 つの大きさ px。引いた距離を段数に直すのに使う。 */
    cellPx: Float,
    /** 左上の移動量と、幅と高さの変化を段数で渡す。受け付けたら true を返す。 */
    onResize: (dcol: Int, drow: Int, dw: Int, dh: Int) -> Boolean,
) {
    val theme = LocalLauncherTheme.current
    val haptic = LocalHapticFeedback.current
    val onResizeNow = rememberUpdatedState(onResize)

    // 三つの角。sx, sy は「そのつまみが動かす辺」の側。-1 なら左(上)の辺で、引くと原点も動く
    listOf(
        Triple(Alignment.TopEnd, 1, -1),
        Triple(Alignment.BottomStart, -1, 1),
        Triple(Alignment.BottomEnd, 1, 1),
    ).forEach { (align, sx, sy) ->
        Box(
            modifier = Modifier
                .align(align)
                // 丸の中心が角に乗り、当たり判定はセルの内側へ広がるようにずらす
                .offset(
                    x = if (sx < 0) -HANDLE_OVERHANG else HANDLE_OVERHANG,
                    y = if (sy < 0) -HANDLE_OVERHANG else HANDLE_OVERHANG,
                )
                .size(TAP_MIN)
                .pointerInput(cellPx, sx, sy) {
                    // 引いた合計を持ち、セルをまたぐたびに 1 段ぶん伝える
                    var acc = 0f to 0f
                    var sent = 0 to 0
                    var refused = 0 to 0
                    detectDragGestures(
                        onDragStart = { acc = 0f to 0f; sent = 0 to 0; refused = 0 to 0 },
                        onDragEnd = { acc = 0f to 0f; sent = 0 to 0; refused = 0 to 0 },
                        onDragCancel = { acc = 0f to 0f; sent = 0 to 0; refused = 0 to 0 },
                    ) { change, amount ->
                        change.consume()
                        if (cellPx <= 0f) return@detectDragGestures
                        acc = (acc.first + amount.x) to (acc.second + amount.y)
                        // 指が動いた段数。右と下が正
                        val kx = (acc.first / cellPx).roundToInt()
                        val ky = (acc.second / cellPx).roundToInt()
                        val dx = kx - sent.first
                        val dy = ky - sent.second
                        if (dx == 0 && dy == 0) return@detectDragGestures
                        // 右(下)の辺なら幅(高さ)がそのまま増減。左(上)の辺なら原点が動き、幅(高さ)は逆に増減
                        val accepted = onResizeNow.value(
                            if (sx < 0) dx else 0,
                            if (sy < 0) dy else 0,
                            sx * dx,
                            sy * dy,
                        )
                        if (accepted) {
                            // 受け付けられた分だけ「送った」ことにする。拒否された段は、指を戻せばまた試される
                            sent = kx to ky
                            refused = 0 to 0
                        } else if (refused != (kx to ky)) {
                            // 同じ段で何度も震わせない。指が別の段に進んだときだけ再び返す
                            refused = kx to ky
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    }
                },
            contentAlignment = when (align) {
                Alignment.TopEnd -> Alignment.TopEnd
                Alignment.BottomStart -> Alignment.BottomStart
                else -> Alignment.BottomEnd
            },
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

/** つまみの見た目の大きさ。当たり判定は TAP_MIN。 */
private val HANDLE_DOT = 20.dp

/** つまみの丸を角からはみ出させる量。丸の中心がちょうど角に乗る。 */
private val HANDLE_OVERHANG = HANDLE_DOT / 2
