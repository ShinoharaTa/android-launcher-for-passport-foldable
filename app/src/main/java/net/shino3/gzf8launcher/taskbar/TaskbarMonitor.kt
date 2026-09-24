package net.shino3.gzf8launcher.taskbar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 前面に出たアプリの記録。サービスが動いていることを実機で確かめるための履歴に使う。 */
data class ForegroundEvent(val packageName: String, val at: Long)

/**
 * タスクバー(#55〜#57)が見ている端末の状態。
 * ユーザー補助サービス(TaskbarService)が書き、設定画面と METRICS ウィジェットが読む。
 */
data class TaskbarState(
    /** サービスが接続されているか。端末設定で有効でも、省電力などで止まっていれば false。 */
    val serviceRunning: Boolean = false,
    /** 前面のパッケージ。ランチャー自身のこともある。 */
    val foreground: String? = null,
    val foregroundAt: Long = 0L,
    /** 開いた画面か。最大ウィンドウの最小幅で判定する。サービスが読めていなければ null。 */
    val opened: Boolean? = null,
    val smallestWidthDp: Int = 0,
    /** 直近に前面へ出たアプリ。新しいものが先頭。 */
    val recent: List<ForegroundEvent> = emptyList(),
    /** 重ねたドックがいま出ているか(#56)。 */
    val overlayShown: Boolean = false,
)

/**
 * TaskbarState の置き場と、権限まわりの入口。
 * サービスは Android が起動するのでインスタンスを掴めない。プロセス内の唯一の状態としてここに置く。
 */
object TaskbarMonitor {
    private val _state = MutableStateFlow(TaskbarState())
    val state: StateFlow<TaskbarState> = _state

    internal fun update(transform: (TaskbarState) -> TaskbarState) = _state.update(transform)

    private val _dockEnabled = MutableStateFlow(true)

    /** 重ねたドックを出すか(#56)。サービスを切らずに止められるように、設定で持つ。 */
    val dockEnabled: StateFlow<Boolean> = _dockEnabled

    /** 設定の読み込み。サービスと Activity のどちらが先に動いても同じ値を見る。 */
    fun init(context: Context) {
        _dockEnabled.value = prefs(context).getBoolean(KEY_DOCK_ENABLED, true)
    }

    fun setDockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOCK_ENABLED, enabled).apply()
        _dockEnabled.value = enabled
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "taskbar"
    private const val KEY_DOCK_ENABLED = "dock_enabled"

    /** 端末設定でユーザー補助サービスが有効になっているか。動いているかは state.serviceRunning で見る。 */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TaskbarService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
