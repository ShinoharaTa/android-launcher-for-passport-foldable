package net.shino3.gzf8launcher.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * 下スワイプで開く端末の検索(#25、#29)。
 *
 * Galaxy の Finder は全体検索として登録されていないことがあり、`SearchManager.globalSearchActivity` に
 * 任せると Google アプリが開く。そこから他のことができないので、次の順で探す。
 *  1. 全体検索を扱う活動のうち Finder のもの
 *  2. Finder のパッケージの起動 Intent
 *  3. 端末が選んでいる全体検索(素の Android では Google)
 * どれも無ければ null を返し、呼び出し側が自前のドロワー検索に落とす。
 * どの経路で開いたかは実機でしか分からないので、ログに残す。
 */
object GlobalSearch {
    private const val TAG = "GlobalSearch"
    private const val FINDER_PACKAGE = "com.samsung.android.app.galaxyfinder"

    fun resolve(context: Context): Intent? {
        val pm = context.packageManager
        val global = Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)

        val finderHandler = pm.queryIntentActivities(global, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo?.packageName == FINDER_PACKAGE }
            ?.activityInfo
        if (finderHandler != null) {
            return Intent(global)
                .setClassName(finderHandler.packageName, finderHandler.name)
                .withQueryExtras()
                .also { Log.i(TAG, "open via finder(global-search): ${it.component}") }
        }

        pm.getLaunchIntentForPackage(FINDER_PACKAGE)?.let { launch ->
            return launch
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .also { Log.i(TAG, "open via finder(launch): ${it.component}") }
        }

        val manager = context.getSystemService(SearchManager::class.java)
        val component = runCatching { manager?.globalSearchActivity }.getOrNull()
        if (component != null) {
            return Intent(global)
                .setComponent(component)
                .withQueryExtras()
                .also { Log.i(TAG, "open via global-search: ${it.component}") }
        }

        Log.i(TAG, "no device search; falling back to the drawer")
        return null
    }

    private fun Intent.withQueryExtras(): Intent = this
        .putExtra(SearchManager.QUERY, "")
        .putExtra(SearchManager.EXTRA_SELECT_QUERY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
