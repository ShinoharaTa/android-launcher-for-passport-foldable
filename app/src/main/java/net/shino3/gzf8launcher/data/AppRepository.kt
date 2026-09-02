package net.shino3.gzf8launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.shino3.gzf8launcher.model.AppKey

/** 起動可能なアクティビティ 1 件。 */
data class AppEntry(
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    /** ApplicationInfo.category。規則つきフォルダのカテゴリ分類に使う。 */
    val category: Int,
    val icon: ImageBitmap,
) {
    val key: AppKey get() = AppKey(componentName.flattenToString(), userSerial)
}

/**
 * LauncherApps 経由でアプリ一覧を取得し、起動する。
 * 仕事用プロファイルなど複数ユーザーのアプリも一緒に列挙する。
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun loadApps(): List<AppEntry> {
        val densityDpi = context.resources.displayMetrics.densityDpi
        return userManager.userProfiles
            .flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    // ホーム自身は一覧に出さない
                    .filter { it.componentName.packageName != context.packageName }
                    .map { info ->
                        AppEntry(
                            label = info.label.toString(),
                            componentName = info.componentName,
                            user = user,
                            userSerial = serial,
                            category = info.applicationInfo.category,
                            icon = info.getIcon(densityDpi).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                        )
                    }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(entry: AppEntry, sourceBounds: Rect? = null) {
        launcherApps.startMainActivity(entry.componentName, entry.user, sourceBounds, null)
    }

    /** アプリの追加・削除・更新のたびに Unit を流す。 */
    fun changes(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private companion object {
        const val ICON_PX = 192
    }
}
