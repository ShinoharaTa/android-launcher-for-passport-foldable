package net.shino3.gzf8launcher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ドロワーの開閉の進捗(0 = 閉、1 = 開)。
 * 閾値で切り替えるのではなく指の移動に追従させ、離した位置と速度で行き先を決める。
 */
class DrawerSheetState(private val scope: CoroutineScope) {
    private val progressAnim = Animatable(0f)

    /**
     * 指の移動を積む先。
     * Animatable への反映はコルーチンなので、1 フレームに複数回来る移動を
     * progressAnim.value から計算すると取りこぼす。同期的に持つ値を正とする。
     */
    private var accumulated = 0f

    /** ドロワーの高さ px。進捗と指の移動量を対応させるのに使う。 */
    var heightPx by mutableFloatStateOf(1f)

    val progress: Float get() = progressAnim.value
    val isOpen: Boolean get() = progressAnim.targetValue > 0.5f
    val isSettled: Boolean get() = !progressAnim.isRunning

    /** 指の移動量を進捗に足す。上方向(負)で開く向き。 */
    fun dragBy(deltaPx: Float) {
        accumulated = (accumulated - deltaPx / heightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        scope.launch { progressAnim.snapTo(accumulated) }
    }

    /**
     * 指を離したときの行き先を決める。velocity は px/秒、上方向が負。
     *
     * commitTo を渡すと、速度がはっきり逆を向いていない限りそこへ落ち着く。
     * 抵抗を越えてから引いた操作は、その時点で向きが決まったものとして扱うためである(#27)。
     * 位置で決めると、抵抗のぶんに加えて画面の半分まで引かないと開かず、指を離した瞬間に戻ってしまう。
     */
    fun settle(velocity: Float, commitTo: Float? = null) {
        val target = when {
            velocity < -FLING -> 1f
            velocity > FLING -> 0f
            commitTo != null -> commitTo
            else -> if (accumulated > 0.5f) 1f else 0f
        }
        animate(target)
    }

    fun open() = animate(1f)

    fun close() = animate(0f)

    private fun animate(target: Float) {
        accumulated = target
        scope.launch {
            progressAnim.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    private companion object {
        /** これを超える速さで振ったら、位置に関わらずその向きに開閉する。 */
        const val FLING = 500f
    }
}

@Composable
fun rememberDrawerSheetState(): DrawerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerSheetState(scope) }
}

/**
 * 一覧が先頭で下に引かれたぶんをドロワーの開閉に回す。
 * 一覧が動ける限りは一覧に任せるので、スクロールと閉じる操作が競合しない。
 *
 * 先頭に当たってすぐには閉じない(#27)。最初の一定距離は一覧が伸びるだけで、
 * それを超えて引き続けたときだけ進捗が指に付いてくる。速く払ったときはその場で閉じる。
 * 指の動き以外(フリングの減速で先頭に当たった分)は受けない。受けると離した後に勝手に閉じていく。
 */
@Composable
fun DrawerSheetState.closeOnOverscroll(deadZone: Float): NestedScrollConnection {
    val state = this
    return remember(state, deadZone) {
        object : NestedScrollConnection {
            private var pull = 0f
            private var closing = false
            private val deadPx: Float get() = state.heightPx * deadZone

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 閉じ始めたら、戻す向き(上)も一覧より先に受けて進捗に返す
                if (source != NestedScrollSource.UserInput || !closing || available.y >= 0f) return Offset.Zero
                state.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                if (closing) {
                    state.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                if (available.y < 0f) {
                    pull = 0f
                    return Offset.Zero
                }
                pull += available.y
                if (pull > deadPx) {
                    closing = true
                    state.dragBy(pull - deadPx)
                    return Offset(0f, available.y)
                }
                // 抵抗のあいだは消費せず、標準の伸びに任せる
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasClosing = closing
                // 先頭に当たっていた(余りが来ていた)ときだけ、速い払いで閉じる。
                // 一覧の途中からの速いフリングは一覧が受けるので、ここで閉じてはいけない
                val atTop = pull > 0f
                val down = available.y
                closing = false
                pull = 0f
                return when {
                    // 抵抗を越えて引いた。上へ強く払い戻さない限り閉じる
                    wasClosing -> {
                        state.settle(down, commitTo = 0f)
                        available
                    }
                    atTop && down > FAST_CLOSE -> {
                        state.close()
                        available
                    }
                    else -> Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                closing = false
                pull = 0f
                return Velocity.Zero
            }
        }
    }
}

/** 一覧の先頭でこれより速く下に払ったら、距離に関わらず閉じる(px/秒)。 */
private const val FAST_CLOSE = 2500f
