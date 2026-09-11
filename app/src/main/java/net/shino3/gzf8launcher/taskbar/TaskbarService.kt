package net.shino3.gzf8launcher.taskbar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * タスクバー(#55〜#57)の土台になるユーザー補助サービス。
 *
 * One UI のタスクバーは既定ホームの特権機能なので、サードパーティのランチャーからは
 * 「前面のアプリを知る」「開いた画面かを知る」「分割画面に入る」のどれも普通には取れない。
 * ユーザー補助サービスなら、画面の内容を読まなくても前面のウィンドウの変化と
 * 分割画面のトグル(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)が使える。
 *
 * ここでは前面のパッケージと画面の開閉を TaskbarMonitor に書くだけにする。
 * 重ねて出すドック(#56)と分割起動(#57)はこの上に積む。
 */
class TaskbarService : AccessibilityService() {
    /** IME のパッケージ。キーボードが出ただけでも前面の変化として届くので除く。 */
    private var imePackages: Set<String> = emptySet()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) readScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        imePackages = getSystemService(InputMethodManager::class.java)?.inputMethodList
            ?.map { it.packageName }?.toSet() ?: emptySet()
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
        TaskbarMonitor.update { it.copy(serviceRunning = true) }
        readScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED_PACKAGES || pkg in imePackages) return
        val now = System.currentTimeMillis()
        TaskbarMonitor.update { s ->
            if (s.foreground == pkg) {
                s
            } else {
                s.copy(
                    foreground = pkg,
                    foregroundAt = now,
                    recent = (listOf(ForegroundEvent(pkg, now)) + s.recent).take(RECENT_MAX),
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        TaskbarMonitor.update { it.copy(serviceRunning = false, opened = null) }
        return super.onUnbind(intent)
    }

    /**
     * 開いた画面かを最大ウィンドウの最小幅で決める。
     * Fold8 はカバーが 512dp 幅、開くと 758dp 幅なので、600dp を境にすれば両方を取りこぼさない。
     * WindowManager はウィンドウを持つ Context から取る決まりなので、表示用の Context を作って読む。
     */
    private fun readScreen() {
        val sw = runCatching {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val ui = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            val bounds = ui.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            (minOf(bounds.width(), bounds.height()) / ui.resources.displayMetrics.density).toInt()
        }.getOrElse { resources.configuration.smallestScreenWidthDp }
        TaskbarMonitor.update { it.copy(opened = sw >= OPENED_MIN_SW_DP, smallestWidthDp = sw) }
    }

    companion object {
        /** これ以上の最小幅なら開いた画面と見なす。 */
        private const val OPENED_MIN_SW_DP = 600

        private const val RECENT_MAX = 8

        /** 前面の変化として扱わないもの。システム UI と、システムのダイアログ。 */
        private val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
    }
}
