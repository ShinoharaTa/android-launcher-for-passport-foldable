package net.shino3.gzf8launcher.taskbar

import android.app.ActivityOptions
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.data.AppRepository
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.Item
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * 他のアプリの上に出すドックのウィンドウ(#56)。
 *
 * ユーザー補助サービスの Context から TYPE_ACCESSIBILITY_OVERLAY で出す。
 * この種類はサービスが有効なら出せて、「他のアプリの上に重ねて表示」の許可は要らない。
 * Compose を Activity の外で動かすので、ライフサイクルの持ち主を自前で用意する。
 */
class TaskbarOverlay(private val context: Context, private val appRepository: AppRepository) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    var dock by mutableStateOf<List<Item>>(emptyList())
    var apps by mutableStateOf<Map<AppKey, AppEntry>>(emptyMap())
    var theme by mutableStateOf(LauncherTheme())

    val shown: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val owner = OverlayOwner().also { this.owner = it }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                CompositionLocalProvider(LocalLauncherTheme provides theme) {
                    OverlayDock(items = dock, apps = apps, onLaunch = ::launch)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 焦点は取らない(下のアプリの入力を奪わない)。触った所だけ受け、外側は素通し
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 画面の下端から自分で上げる。システムに避けさせると、ジェスチャーナビのピルの上に被った(AVD で確認)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            fitInsetsTypes = 0
            y = (BOTTOM_GAP_DP * context.resources.displayMetrics.density).toInt()
        }
        // 足す前に今の余白を読む。ウィンドウに届く insets は WRAP_CONTENT の重ね窓には来ないことがある(AVD で確認)
        params.y += bottomInset()
        runCatching { windowManager.addView(view, params) }
            .onFailure { Log.w(TAG, "ドックを重ねられない", it); return }
        owner.start()
        this.view = view
    }

    fun hide() {
        val view = view ?: return
        owner?.stop()
        runCatching { windowManager.removeView(view) }
        this.view = null
        this.owner = null
    }

    /**
     * ナビゲーションバー(3 ボタン)かピル(ジェスチャー)の高さ。どちらでも上に乗るように大きい方を取る。
     * サービスの Context は画面を持つので currentWindowMetrics が読める。読めなければ 0。
     */
    private fun bottomInset(): Int = runCatching {
        val insets = windowManager.currentWindowMetrics.windowInsets
        val nav = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        val gesture = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
        Log.d(TAG, "insets nav=$nav gesture=$gesture")
        maxOf(nav, gesture)
    }.getOrDefault(0)

    private fun launch(entry: AppEntry, bounds: androidx.compose.ui.geometry.Rect) {
        val rect = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        val opts = ActivityOptions.makeScaleUpAnimation(view ?: return, rect.left, rect.top, rect.width(), rect.height())
        runCatching { appRepository.launch(entry, rect, opts.toBundle()) }
            .onFailure { Log.w(TAG, "重ねたドックから起動できない: ${entry.label}", it) }
    }

    /** Compose が要るライフサイクル一式。ウィンドウを足したら RESUMED、外したら DESTROYED。 */
    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private companion object {
        const val TAG = "TaskbarOverlay"

        /** ナビゲーションバーの上に置く隙間。 */
        const val BOTTOM_GAP_DP = 8
    }
}
