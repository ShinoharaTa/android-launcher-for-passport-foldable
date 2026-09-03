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

    /** 指を離したときの行き先を決める。velocity は px/秒、上方向が負。 */
    fun settle(velocity: Float) {
        val target = when {
            velocity < -FLING -> 1f
            velocity > FLING -> 0f
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
 */
@Composable
fun DrawerSheetState.closeOnOverscroll(): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val state = this
    return remember(state) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                state.dragBy(available.y)
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (state.progress >= 1f) return androidx.compose.ui.unit.Velocity.Zero
                state.settle(-available.y)
                return available
            }
        }
    }
}
