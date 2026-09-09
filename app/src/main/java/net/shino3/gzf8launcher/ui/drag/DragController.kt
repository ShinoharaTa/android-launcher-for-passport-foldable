package net.shino3.gzf8launcher.ui.drag

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.ZoneId

/** ドラッグで運ぶもの。source が null ならドロワーやウィジェット一覧など、レイアウト外から来た。 */
data class DragPayload(
    val item: Item,
    val source: ItemRef?,
    val icon: ImageBitmap?,
    val label: String,
    val w: Int = 1,
    val h: Int = 1,
)

class DragSession(val payload: DragPayload, val start: Offset) {
    var position by mutableStateOf(start)
    var moved by mutableStateOf(false)
}

/** いまポインタが指している場所。同じ場所に留まっている時間を測るのに使う(#25)。 */
sealed interface Hover {
    data class Cell(val zone: ZoneId, val col: Int, val row: Int, val w: Int, val h: Int) : Hover
    data class Dock(val slot: Int) : Hover
}

/** 拒否された落とし。影を from から to へ戻す(#25)。 */
class RejectedDrop(val payload: DragPayload, val from: Offset, val to: Rect)

/** ドロップ先。root 座標の矩形で判定する。 */
sealed class DropTarget(val bounds: Rect, val priority: Int) {
    /** rows は段数が固定のゾーン(アプリのページ)で渡す。null なら下方向に制限しない。 */
    class Grid(val zone: ZoneId, bounds: Rect, val columns: Int, val rows: Int? = null) : DropTarget(bounds, 1) {
        val cellPx: Float get() = bounds.width / columns

        /** ポインタのセルを w×h の中央に寄せた左上セル。 */
        fun cellFor(pos: Offset, w: Int, h: Int): Pair<Int, Int> {
            val col = ((pos.x - bounds.left) / cellPx).toInt() - (w - 1) / 2
            val row = ((pos.y - bounds.top) / cellPx).toInt() - (h - 1) / 2
            val maxRow = rows?.let { maxOf(0, it - h) } ?: Int.MAX_VALUE
            return col.coerceIn(0, maxOf(0, columns - w)) to row.coerceIn(0, maxRow)
        }
    }

    class Dock(bounds: Rect, val slots: Int) : DropTarget(bounds, 1) {
        fun slotAt(pos: Offset): Int = ((pos.x - bounds.left) / (bounds.width / slots)).toInt().coerceIn(0, slots - 1)
    }

    class Remove(bounds: Rect) : DropTarget(bounds, 0)
}

/**
 * ドラッグの進行状態と、登録されたドロップ先を持つ。
 * 長押しして動かさずに離した場合はドロップではなく長押しメニューとして扱う。
 */
class DragController(
    private val slopPx: Float,
    /** 落とす。受け付けたら true。第 3 引数は「留めてから離した」か。 */
    private val onDrop: (DragSession, DropTarget?, Boolean) -> Boolean,
    private val onLongPress: (DragPayload, Rect) -> Unit,
    private val onCancel: () -> Unit = {},
) {
    var session by mutableStateOf<DragSession?>(null)
        private set

    /** いま指しているセル。セルが変わるたびに更新され、留めの計測が振り出しに戻る。 */
    var hover by mutableStateOf<Hover?>(null)
        private set

    /** 同じセルに十分留まった印。離すとフォルダにまとめる。外から立てる。 */
    var dwell by mutableStateOf(false)

    /** 拒否された落とし。影を戻す動きが終わったら外から消す。 */
    var rejected by mutableStateOf<RejectedDrop?>(null)

    /** 直前に長押しした要素の矩形。長押しメニューの起点と、拒否時に影を戻す先に使う。 */
    private var itemBounds: Rect = Rect.Zero
    val targets = mutableStateMapOf<String, DropTarget>()

    fun begin(payload: DragPayload, position: Offset, bounds: Rect = Rect.Zero) {
        itemBounds = bounds
        hover = null
        dwell = false
        session = DragSession(payload, position)
    }

    fun move(delta: Offset) {
        val s = session ?: return
        s.position += delta
        if (!s.moved && (s.position - s.start).getDistance() > slopPx) s.moved = true
        updateHover(s)
    }

    private fun updateHover(s: DragSession) {
        val next = when (val t = hitTest(s.position)) {
            is DropTarget.Grid -> {
                val (col, row) = t.cellFor(s.position, s.payload.w, s.payload.h)
                Hover.Cell(t.zone, col, row, s.payload.w, s.payload.h)
            }
            is DropTarget.Dock -> Hover.Dock(t.slotAt(s.position))
            else -> null
        }
        if (next != hover) {
            hover = next
            dwell = false
        }
    }

    fun end() {
        val s = session ?: return
        session = null
        val held = dwell
        hover = null
        dwell = false
        if (!s.moved) {
            onLongPress(s.payload, itemBounds)
            return
        }
        val accepted = onDrop(s, hitTest(s.position), held)
        if (!accepted && !itemBounds.isEmpty) rejected = RejectedDrop(s.payload, s.position, itemBounds)
    }

    fun cancel() {
        session = null
        hover = null
        dwell = false
        onCancel()
    }

    fun hitTest(pos: Offset): DropTarget? =
        targets.values.filter { it.bounds.contains(pos) }.minByOrNull { it.priority }
}

val LocalDragController = staticCompositionLocalOf<DragController> { error("DragController is not provided") }

/**
 * 長押しでドラッグを始められるようにし、短いタップも同じ場所で扱う。
 *
 * タップ検出と長押し検出を別々のコルーチンで並べると、片方が最初の down を消費して
 * もう片方に届かなくなる。ひとつのジェスチャ処理にまとめて順番に判定する。
 */
@Composable
fun Modifier.dragSource(
    payload: DragPayload,
    enabled: Boolean = true,
    /** 触った要素の矩形を受け取る。起動や重ね描きの起点に使う(#23)。 */
    onTap: ((Rect) -> Unit)? = null,
): Modifier {
    val controller = LocalDragController.current
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // enabled と onTap はドラッグ中にも変わる(出どころが hidden になる、再コンポーズで別のラムダになる)。
    // pointerInput の鍵にすると進行中のジェスチャが途中で切られ、end() が呼ばれずに影が残る
    val enabledNow = rememberUpdatedState(enabled)
    val onTapNow = rememberUpdatedState(onTap)
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(payload) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!enabledNow.value) {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onTapNow.value?.invoke(bounds)
                    }
                    return@awaitEachGesture
                }
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.begin(payload, bounds.topLeft + longPress.position, bounds)
                    var finished = false
                    try {
                        drag(longPress.id) { change ->
                            controller.move(change.positionChange())
                            change.consume()
                        }
                        finished = true
                        controller.end()
                    } finally {
                        // 出どころが消えるなどでジェスチャが切られたら、影を残さない
                        if (!finished) controller.cancel()
                    }
                    return@awaitEachGesture
                }
                // 長押しにならなかった。指が既に離れていればタップ、まだ触れているならスクロール等に譲る
                val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
                if (!stillDown) onTapNow.value?.invoke(bounds)
            }
        }
}

/** この要素の矩形をドロップ先として登録する。 */
@Composable
fun Modifier.dropTarget(key: String, factory: (Rect) -> DropTarget): Modifier {
    val controller = LocalDragController.current
    DisposableEffect(key) {
        onDispose { controller.targets.remove(key) }
    }
    return onGloballyPositioned { controller.targets[key] = factory(it.boundsInRoot()) }
}
