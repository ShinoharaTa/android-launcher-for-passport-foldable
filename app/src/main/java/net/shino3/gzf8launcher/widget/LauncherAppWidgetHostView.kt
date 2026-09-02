package net.shino3.gzf8launcher.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * 長押しを横取りしてドラッグに変える AppWidgetHostView。
 * 中身のボタン等への通常のタップはそのまま通す。長押しが成立したら子には CANCEL を送り、
 * 以後の移動を画面座標でリスナーに渡す(Compose 側のポインタ処理は View が消費するため使えない)。
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    interface DragListener {
        fun onLongPress(rawX: Float, rawY: Float)
        fun onDrag(rawX: Float, rawY: Float)
        fun onRelease()
        fun onCancel()
    }

    var dragListener: DragListener? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var pending: Runnable? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> dragListener?.onDrag(ev.rawX, ev.rawY)
                MotionEvent.ACTION_UP -> {
                    longPressed = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    dragListener?.onRelease()
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressed = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    dragListener?.onCancel()
                }
            }
            return true
        }
        // 子が何も消費しなかったときもここで長押しを見る
        track(ev)
        return true
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pending != null) return
                downX = ev.x
                downY = ev.y
                longPressed = false
                pending = Runnable {
                    pending = null
                    longPressed = true
                    // Compose 側の親(上スワイプ検出など)に横取りされると CANCEL が来るので、ドラッグ中は止める
                    parent?.requestDisallowInterceptTouchEvent(true)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    dragListener?.onLongPress(ev.rawX, ev.rawY)
                }.also { postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) cancelPending()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPending()
        }
    }

    private fun cancelPending() {
        pending?.let { removeCallbacks(it) }
        pending = null
    }
}
