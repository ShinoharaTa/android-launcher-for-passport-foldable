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

/**
 * ホームの縦方向のジェスチャ(#25)。他のランチャーと同じ向きに揃える。
 *  - 上スワイプ: アプリ一覧(ドロワー)を指に追従させて引き上げる
 *  - 下スワイプ: 検索(端末の Finder など)
 *
 * 縦スクロールしない面では draggable で、縦スクロールする面では行き止まりの overscroll で受ける。
 */

/** 縦スクロールしないアプリのページ用。ページめくり(横)とは向きで切り分けられる。 */
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

/** 縦スクロールする面(ウィジェット面など)用。先頭で下に引けば検索、末尾で上に押せばドロワー。 */
@Composable
fun Modifier.homeEdgeScroll(sheet: DrawerSheetState, onSearch: () -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { SEARCH_PULL_DP.dp.toPx() }
    val tracker = remember(sheet, onSearch) { VerticalTracker(sheet, onSearch, thresholdPx) }
    val connection = remember(tracker) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // ドロワーを引き上げ始めたら、戻す向きも一覧より先に受ける
                if (!tracker.lifting || available.y <= 0f) return Offset.Zero
                tracker.drag(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y == 0f) return Offset.Zero
                tracker.drag(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!tracker.active) return Velocity.Zero
                tracker.stop(available.y)
                return available
            }
        }
    }
    return nestedScroll(connection)
}

/**
 * 1 回の指の動きを追う。上に動き始めたらドロワー、下に引いたぶんは検索の判定に積む。
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
        lifting = false
        pull = 0f
    }

    private companion object {
        const val FLING = 1500f
    }
}

/** 下に引いて検索になるまでの距離。 */
private const val SEARCH_PULL_DP = 72
