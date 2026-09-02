package net.shino3.gzf8launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/** 起動回数と最終起動時刻を UsageStats から取る(docs/04、2026-09-03 決定)。 */
class UsageRepository(private val context: Context) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    data class PackageUsage(val packageName: String, val launches: Int, val lastUsed: Long)

    fun hasPermission(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 直近 days 日のアクティビティ再開イベントをパッケージごとに数える。 */
    fun query(days: Int): Map<String, PackageUsage> {
        if (!hasPermission()) return emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - days * 24L * 60 * 60 * 1000
        val events = usageStatsManager.queryEvents(begin, end) ?: return emptyMap()
        val result = HashMap<String, PackageUsage>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val prev = result[event.packageName]
            result[event.packageName] = PackageUsage(
                packageName = event.packageName,
                launches = (prev?.launches ?: 0) + 1,
                lastUsed = maxOf(prev?.lastUsed ?: 0L, event.timeStamp),
            )
        }
        return result
    }
}
