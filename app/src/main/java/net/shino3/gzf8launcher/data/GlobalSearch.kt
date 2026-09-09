package net.shino3.gzf8launcher.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent

/**
 * 端末の全体検索を開く(#25)。Galaxy では Finder、Pixel では Google 検索が登録されている。
 * 自前の検索面は作らず、無い端末でだけ呼び出し側が一覧の検索欄に落とす。
 */
object GlobalSearch {
    fun intent(context: Context): Intent? {
        val manager = context.getSystemService(SearchManager::class.java) ?: return null
        val component = runCatching { manager.globalSearchActivity }.getOrNull() ?: return null
        return Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            .setComponent(component)
            .putExtra(SearchManager.QUERY, "")
            .putExtra(SearchManager.EXTRA_SELECT_QUERY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
