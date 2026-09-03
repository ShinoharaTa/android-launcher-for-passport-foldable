package net.shino3.gzf8launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import net.shino3.gzf8launcher.model.ShortcutItem

/** アプリが持つ Android のショートカット 1 件。 */
data class ShortcutEntry(
    val id: String,
    val packageName: String,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val icon: ImageBitmap?,
) {
    fun toItem() = ShortcutItem(
        packageName = packageName,
        shortcutId = id,
        user = userSerial.takeIf { it != 0L },
        label = label,
    )
}

/**
 * アプリのショートカットを読み、起動と固定を行う。
 * 既定ホームでないと読めない(hasShortcutHostPermission が false になる)。
 */
class ShortcutRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun isHost(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** そのアクティビティに紐づく、宣言済み・動的・固定済みのショートカット。 */
    fun forActivity(component: ComponentName, user: UserHandle): List<ShortcutEntry> {
        if (!isHost()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(component.packageName)
            .setActivity(component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val found = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
        return found.filter { it.isEnabled }.sortedBy { it.rank }.map { it.toEntry(user) }
    }

    /** 固定済みのショートカット 1 件を、レイアウトの参照から引き直す。 */
    fun resolve(item: ShortcutItem): ShortcutEntry? {
        if (!isHost()) return null
        val user = userFor(item.user)
        val query = LauncherApps.ShortcutQuery()
            .setPackage(item.packageName)
            .setShortcutIds(listOf(item.shortcutId))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return runCatching { launcherApps.getShortcuts(query, user) }.getOrNull()
            ?.firstOrNull()
            ?.toEntry(user)
    }

    fun launch(item: ShortcutItem) {
        if (!isHost()) {
            Log.w(TAG, "既定ホームでないのでショートカットを起動できない")
            return
        }
        runCatching {
            launcherApps.startShortcut(item.packageName, item.shortcutId, null, null, userFor(item.user))
        }.onFailure { Log.e(TAG, "ショートカットの起動に失敗した: ${item.packageName}/${item.shortcutId}", it) }
    }

    /**
     * ホームに置いたショートカットが消えないように固定する。
     * pinShortcuts は与えた集合で置き換えるので、既に固定済みのものと合わせて渡す。
     */
    fun pin(item: ShortcutItem) {
        if (!isHost()) return
        val user = userFor(item.user)
        val pinnedQuery = LauncherApps.ShortcutQuery()
            .setPackage(item.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val pinned = runCatching { launcherApps.getShortcuts(pinnedQuery, user) }
            .getOrNull().orEmpty().mapNotNull { it.id }
        if (item.shortcutId in pinned) return
        runCatching { launcherApps.pinShortcuts(item.packageName, pinned + item.shortcutId, user) }
    }

    private fun userFor(serial: Long?): UserHandle =
        serial?.let { userManager.getUserForSerialNumber(it) } ?: android.os.Process.myUserHandle()

    private fun ShortcutInfo.toEntry(user: UserHandle): ShortcutEntry {
        val densityDpi = context.resources.displayMetrics.densityDpi
        val drawable = runCatching { launcherApps.getShortcutIconDrawable(this, densityDpi) }.getOrNull()
        return ShortcutEntry(
            id = id,
            packageName = `package`,
            user = user,
            userSerial = userManager.getSerialNumberForUser(user),
            label = (longLabel ?: shortLabel ?: id).toString(),
            icon = drawable?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap(),
        )
    }

    private companion object {
        const val TAG = "ShortcutRepository"
        const val ICON_PX = 144
    }
}
