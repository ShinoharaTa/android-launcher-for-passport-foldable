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

/** ドロップ先。root 座標の矩形で判定する。 */
sealed class DropTarget(val bounds: Rect, val priority: Int) {
    /** rows は段数が固定のゾーン(アプリ棚)で渡す。null なら下方向に制限しない。 */
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
    private val onDrop: (DragSession, DropTarget?) -> Unit,
    private val onLongPress: (DragPayload, Rect) -> Unit,
    private val onCancel: () -> Unit = {},
) {
    var session by mutableStateOf<DragSession?>(null)
        private set

    /** 直前に長押しした要素の矩形。長押しメニューを出す起点に使う(#23)。 */
    private var itemBounds: Rect = Rect.Zero
    val targets = mutableStateMapOf<String, DropTarget>()

    fun begin(payload: DragPayload, position: Offset, bounds: Rect = Rect.Zero) {
        itemBounds = bounds
        session = DragSession(payload, position)
    }

    fun move(delta: Offset) {
        val s = session ?: return
        s.position += delta
        if (!s.moved && (s.position - s.start).getDistance() > slopPx) s.moved = true
    }

    fun end() {
        val s = session ?: return
        session = null
        if (s.moved) onDrop(s, hitTest(s.position)) else onLongPress(s.payload, itemBounds)
    }

    fun cancel() {
        session = null
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
    val origin = bounds.topLeft
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(payload, enabled, onTap) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!enabled) {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onTap?.invoke(bounds)
                    }
                    return@awaitEachGesture
                }
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.begin(payload, origin + longPress.position, bounds)
                    drag(longPress.id) { change ->
                        controller.move(change.positionChange())
                        change.consume()
                    }
                    controller.end()
                    return@awaitEachGesture
                }
                // 長押しにならなかった。指が既に離れていればタップ、まだ触れているならスクロール等に譲る
                val stillDown = currentEvent.changes.any { it.id == down.id && it.pressed }
                if (!stillDown) onTap?.invoke(bounds)
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
