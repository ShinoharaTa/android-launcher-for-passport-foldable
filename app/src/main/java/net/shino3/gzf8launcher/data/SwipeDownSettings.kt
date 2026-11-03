package net.shino3.gzf8launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** ホームで下にスワイプしたときに何を開くか(#31)。 */
enum class SwipeDown(val label: String) {
    /** 端末の検索。Galaxy なら Finder、無ければ全体検索(#25、#29)。 */
    DEVICE_SEARCH("DEVICE SEARCH"),

    /** ランチャーのドロワーを開いて検索欄に焦点を当てる。 */
    APP_SEARCH("APP SEARCH"),

    /** 通知パネル。One UI ホームの既定と同じ。ユーザー補助サービス(#55)が要る。 */
    NOTIFICATIONS("NOTIFICATIONS"),
}

/** 下スワイプで端末の検索を開こうとした結果。実機でしか分からないので、設定画面に出して切り分けに使う。 */
data class SearchAttempt(
    /** どの経路を選んだか(finder(global-search) / finder(launch) / global-search / none)。 */
    val route: String,
    val component: String?,
    val ok: Boolean,
    val error: String?,
    val at: Long,
)

/**
 * 下スワイプの設定と、直近の試行の記録。
 * 設定は SharedPreferences に持ち、Activity が起動したときに読む。
 */
object SwipeDownSettings {
    private val _action = MutableStateFlow(SwipeDown.DEVICE_SEARCH)
    val action: StateFlow<SwipeDown> = _action

    private val _lastAttempt = MutableStateFlow<SearchAttempt?>(null)
    val lastAttempt: StateFlow<SearchAttempt?> = _lastAttempt

    fun init(context: Context) {
        val saved = prefs(context).getString(KEY_ACTION, null)
        _action.value = SwipeDown.entries.firstOrNull { it.name == saved } ?: SwipeDown.DEVICE_SEARCH
    }

    fun set(context: Context, action: SwipeDown) {
        prefs(context).edit().putString(KEY_ACTION, action.name).apply()
        _action.value = action
    }

    internal fun record(attempt: SearchAttempt) {
        _lastAttempt.value = attempt
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "gestures"
    private const val KEY_ACTION = "swipe_down"
}
