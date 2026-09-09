package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.ui.drawer.DrawerSheetState
import net.shino3.gzf8launcher.ui.drawer.RELEASE_EPS

/**
 * ホームの縦方向のジェスチャ(#25)。他のランチャーと同じ向きに揃える。
 *  - 上スワイプ: アプリ一覧(ドロワー)を指に追従させて引き上げる
 *  - 下スワイプ: 検索(端末の Finder など)
 *
 * 縦スクロールしない面では draggable で受け、指の動きにそのまま追従する。
 * 縦スクロールする面では行き止まりの overscroll で受けるが、スクロールと紛れないように抵抗を付ける(#27)。
 * 端に当たっても最初の一定距離は一覧が伸びるだけで、それを超えて引き続けたときだけ面の出入りに入る。
 */

/** 縦スクロールしないアプリのページとドック用。ページめくり(横)とは向きで切り分けられる。 */
@Composable
fun Modifier.homeVerticalGestures(sheet: DrawerSheetState, onSearch: () -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { SEARCH_PULL_DP.dp.toPx() }
    val tracker = remember(sheet, onSearch) { VerticalTracker(sheet, onSearch, thresholdPx) }
    val state = rememberDraggableState { delta -> tracker.drag(delta) }
    return draggable(
        state = state,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> tracker.stop(velocity) },
    )
}

/**
 * 縦スクロールする面(ウィジェット面など)用。
 * 先頭で下に、末尾で上に、抵抗の距離を超えて引き続けたときだけ検索/ドロワーになる。
 * 抵抗のあいだは消費しないので、標準の overscroll の伸びが見え、行き止まりだと分かる。
 */
@Composable
fun Modifier.homeEdgeScroll(sheet: DrawerSheetState, onSearch: () -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { SEARCH_PULL_DP.dp.toPx() }
    val tracker = remember(sheet, onSearch) { EdgeTracker(sheet, onSearch, thresholdPx) }
    val connection = remember(tracker) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // ドロワーを引き上げ始めたら、戻す向きも一覧より先に受ける
                if (source != NestedScrollSource.UserInput || !tracker.lifting || available.y <= 0f) return Offset.Zero
                tracker.overscroll(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 指の動きだけを見る。フリングの減速で端に当たった分を積むと、次の操作で誤発火する
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val taken = tracker.overscroll(available.y)
                return Offset(0f, taken)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!tracker.active) return Velocity.Zero
                val lifting = tracker.lifting
                tracker.stop(available.y)
                return if (lifting) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                tracker.reset()
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/**
 * 1 回の指の動きを追う(スクロールしない面用)。上に動き始めたらドロワー、下に引いたぶんは検索の判定に積む。
 * 混ざらないように、どちらかに決まったらもう一方には流さない。
 */
private class VerticalTracker(
    private val sheet: DrawerSheetState,
    private val onSearch: () -> Unit,
    private val thresholdPx: Float,
) {
    /** ドロワーを持ち上げている最中。 */
    var lifting = false
        private set
    private var pull = 0f
    val active: Boolean get() = lifting || pull != 0f

    fun drag(delta: Float) {
        when {
            lifting -> sheet.dragBy(delta)
            delta < 0f && pull <= 0f -> {
                lifting = true
                sheet.dragBy(delta)
            }
            else -> pull += delta
        }
    }

    fun stop(velocity: Float) {
        if (lifting) {
            sheet.settle(velocity)
        } else if (pull > thresholdPx || (pull > 0f && velocity > FLING)) {
            onSearch()
        }
        reset()
    }

    fun reset() {
        lifting = false
        pull = 0f
    }

    private companion object {
        const val FLING = 1500f
    }
}

/**
 * 行き止まりの overscroll を追う(スクロールする面用、#27)。
 * 端に当たってからの量を積み、抵抗の距離(画面の高さの一定割合)を超えた分だけを面の出入りに回す。
 */
private class EdgeTracker(
    private val sheet: DrawerSheetState,
    private val onSearch: () -> Unit,
    private val thresholdPx: Float,
) {
    var lifting = false
        private set
    /** 端に当たってから積んだ量。下向きが正。向きが変わったら振り出しに戻す。 */
    private var pull = 0f
    val active: Boolean get() = lifting || pull != 0f
    private val deadPx: Float get() = sheet.heightPx * DEAD_ZONE

    /** 消費した量を返す。抵抗のあいだは 0(標準の伸びに任せる)。 */
    fun overscroll(delta: Float): Float {
        if (lifting) {
            sheet.dragBy(delta)
            // 引き戻しきった。持ち上げを解いて一覧にスクロールを返す。解かないと離したときに開いてしまう
            if (delta > 0f && sheet.dragFraction <= RELEASE_EPS) {
                lifting = false
                pull = 0f
                sheet.close()
            }
            return delta
        }
        if ((delta > 0f && pull < 0f) || (delta < 0f && pull > 0f)) pull = 0f
        pull += delta
        if (pull < -deadPx) {
            // 抵抗を超えた。超えた分からドロワーを引き上げ始める
            lifting = true
            sheet.dragBy(pull + deadPx)
            return delta
        }
        return 0f
    }

    fun stop(velocity: Float) {
        if (lifting) {
            // 抵抗を越えて押し上げた。下へ強く払い戻さない限り開く
            sheet.settle(velocity, commitTo = 1f)
        } else if (pull > deadPx + thresholdPx) {
            onSearch()
        }
        reset()
    }

    fun reset() {
        lifting = false
        pull = 0f
    }
}

/** 下に引いて検索になるまでの距離(抵抗を超えてから)。 */
private const val SEARCH_PULL_DP = 72

/** 端に当たってから面の出入りに入るまでの抵抗。画面の高さに対する割合。 */
const val DEAD_ZONE = 0.15f
