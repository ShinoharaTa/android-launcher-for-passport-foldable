package net.shino3.gzf8launcher.taskbar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.shino3.gzf8launcher.MainActivity
import net.shino3.gzf8launcher.data.AppRepository
import net.shino3.gzf8launcher.data.LayoutRepository
import net.shino3.gzf8launcher.theme.ThemeRepository
import net.shino3.gzf8launcher.widget.BuiltInWidgets

/**
 * タスクバー(#55〜#57)の土台になるユーザー補助サービス。
 *
 * One UI のタスクバーは既定ホームの特権機能なので、サードパーティのランチャーからは
 * 「前面のアプリを知る」「開いた画面かを知る」「分割画面に入る」のどれも普通には取れない。
 * ユーザー補助サービスなら、画面の内容を読まなくても前面のウィンドウの変化と
 * 分割画面のトグル(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)が使える。
 *
 * 前面のパッケージと画面の開閉を TaskbarMonitor に書き(#55)、
 * 開いた画面で他のアプリが前面のあいだだけドックを重ねて出す(#56)。分割起動(#57)はこの上に積む。
 */
class TaskbarService : AccessibilityService() {
    /** IME のパッケージ。キーボードが出ただけでも前面の変化として届くので除く。 */
    private var imePackages: Set<String> = emptySet()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ランチャー本体(LauncherController)は Activity が持っていて、サービスからは掴めない。
    // 配置・アプリ・テーマは同じファイルを読む別の読み手を持つ
    private lateinit var appRepository: AppRepository
    private lateinit var layoutRepository: LayoutRepository
    private lateinit var themeRepository: ThemeRepository
    private lateinit var overlay: TaskbarOverlay

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) readScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        BuiltInWidgets.register()
        TaskbarMonitor.init(this)
        appRepository = AppRepository(this)
        layoutRepository = LayoutRepository(this)
        themeRepository = ThemeRepository(this)
        overlay = TaskbarOverlay(this, appRepository)
        imePackages = getSystemService(InputMethodManager::class.java)?.inputMethodList
            ?.map { it.packageName }?.toSet() ?: emptySet()
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
        TaskbarMonitor.update { it.copy(serviceRunning = true) }
        readScreen()
        scope.launch { reload() }
        // アプリの追加・削除で描き直す。状態が変わるたびに出す・消すを判定する
        scope.launch { appRepository.changes().collect { reloadApps() } }
        scope.launch {
            combine(TaskbarMonitor.state, TaskbarMonitor.dockEnabled) { s, on -> s to on }
                .collect { (s, on) -> applyVisibility(s, on) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED_PACKAGES || pkg in imePackages) return
        // 自分のパッケージは、ホームの Activity だけを前面の変化と見る。
        // 重ねたドックのウィンドウも同じパッケージで届き、そのまま拾うと「出す → 自分が前面 → 消す」の往復になる
        if (pkg == packageName && event.className?.toString() != MainActivity::class.java.name) return
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
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
        getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        TaskbarMonitor.update { it.copy(serviceRunning = false, opened = null, overlayShown = false) }
        return super.onUnbind(intent)
    }

    /**
     * 出す条件: 設定で有効、開いた画面、前面がランチャー以外。
     * ランチャーに戻ればホームのドックがあるので消す。閉じればカバーの画面が狭いので消す。
     */
    private fun applyVisibility(s: TaskbarState, enabled: Boolean) {
        val show = enabled && s.opened == true && s.foreground != null && s.foreground != packageName
        Log.d(TAG, "visibility enabled=$enabled opened=${s.opened} front=${s.foreground} shown=${overlay.shown} -> $show")
        if (show && !overlay.shown) {
            // 出す直前に配置とテーマを読み直す。ホームで並べ替えた直後でも古いドックを出さない
            scope.launch {
                reload()
                overlay.show()
                TaskbarMonitor.update { it.copy(overlayShown = overlay.shown) }
            }
        } else if (!show && overlay.shown) {
            overlay.hide()
            TaskbarMonitor.update { it.copy(overlayShown = false) }
        }
    }

    private suspend fun reload() {
        themeRepository.load()
        layoutRepository.load()
        overlay.theme = themeRepository.theme.value
        overlay.dock = layoutRepository.layout.value.dock
        reloadApps()
    }

    private suspend fun reloadApps() {
        val style = overlay.theme.iconStyle
        overlay.apps = withContext(Dispatchers.IO) { appRepository.loadApps(style) }.associateBy { it.key }
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
        private const val TAG = "TaskbarService"

        /** これ以上の最小幅なら開いた画面と見なす。 */
        private const val OPENED_MIN_SW_DP = 600

        private const val RECENT_MAX = 8

        /** 前面の変化として扱わないもの。システム UI と、システムのダイアログ。 */
        private val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
    }
}
