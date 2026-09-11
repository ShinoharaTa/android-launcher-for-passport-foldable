package net.shino3.gzf8launcher.taskbar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
)

/**
 * TaskbarState の置き場と、権限まわりの入口。
 * サービスは Android が起動するのでインスタンスを掴めない。プロセス内の唯一の状態としてここに置く。
 */
object TaskbarMonitor {
    private val _state = MutableStateFlow(TaskbarState())
    val state: StateFlow<TaskbarState> = _state

    internal fun update(transform: (TaskbarState) -> TaskbarState) = _state.update(transform)

    /** 端末設定でユーザー補助サービスが有効になっているか。動いているかは state.serviceRunning で見る。 */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TaskbarService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** 「他のアプリの上に重ねて表示」が許可されているか(#56 で使う)。 */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
