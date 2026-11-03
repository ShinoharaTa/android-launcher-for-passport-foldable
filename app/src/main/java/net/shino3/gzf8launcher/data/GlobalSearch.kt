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
 * どの経路で開いたかは実機でしか分からないので、ログに残し、設定画面にも出す(#31)。
 */
object GlobalSearch {
    private const val TAG = "GlobalSearch"
    private const val FINDER_PACKAGE = "com.samsung.android.app.galaxyfinder"

    /** 開く Intent と、それをどの経路で見つけたか。 */
    class Resolved(val intent: Intent, val route: String)

    fun resolve(context: Context): Resolved? {
        val pm = context.packageManager
        val global = Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)

        val finderHandler = pm.queryIntentActivities(global, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo?.packageName == FINDER_PACKAGE }
            ?.activityInfo
        if (finderHandler != null) {
            return Resolved(
                Intent(global).setClassName(finderHandler.packageName, finderHandler.name).withQueryExtras(),
                "finder(global-search)",
            ).also { Log.i(TAG, "open via ${it.route}: ${it.intent.component}") }
        }

        pm.getLaunchIntentForPackage(FINDER_PACKAGE)?.let { launch ->
            return Resolved(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "finder(launch)")
                .also { Log.i(TAG, "open via ${it.route}: ${it.intent.component}") }
        }

        val manager = context.getSystemService(SearchManager::class.java)
        val component = runCatching { manager?.globalSearchActivity }.getOrNull()
        if (component != null) {
            return Resolved(Intent(global).setComponent(component).withQueryExtras(), "global-search")
                .also { Log.i(TAG, "open via ${it.route}: ${it.intent.component}") }
        }

        Log.i(TAG, "no device search; falling back to the drawer")
        return null
    }

    /**
     * 実機で切り分けるための一覧(#31)。設定画面に出す。
     * Finder が入っているか、全体検索を名乗る活動は何か、端末が選んでいる全体検索は何か。
     */
    fun describe(context: Context): List<String> {
        val pm = context.packageManager
        val finderInstalled = runCatching { pm.getPackageInfo(FINDER_PACKAGE, 0) }.isSuccess
        val handlers = pm.queryIntentActivities(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.let { a -> "${a.packageName}/${a.name.substringAfterLast('.')}" } }
        val global = runCatching { context.getSystemService(SearchManager::class.java)?.globalSearchActivity }.getOrNull()
        return listOf(
            "FINDER ${if (finderInstalled) "installed" else "not installed"}" +
                if (finderInstalled) " launch=${pm.getLaunchIntentForPackage(FINDER_PACKAGE) != null}" else "",
            "GLOBAL_SEARCH ${handlers.ifEmpty { listOf("none") }.joinToString()}",
            "DEFAULT ${global?.flattenToShortString() ?: "none"}",
        )
    }

    private fun Intent.withQueryExtras(): Intent = this
        .putExtra(SearchManager.QUERY, "")
        .putExtra(SearchManager.EXTRA_SELECT_QUERY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
