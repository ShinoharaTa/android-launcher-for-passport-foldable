package net.shino3.gzf8launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.theme.IconStyle

/** 起動可能なアクティビティ 1 件。 */
data class AppEntry(
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    /** ApplicationInfo.category。ドロワーのカテゴリ絞り込みに使う。付けていないアプリは -1。 */
    val category: Int,
    /** 初回インストール時刻(ミリ秒)。ドロワーの「新着」に使う。 */
    val installedAt: Long,
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

    /** style はアイコンの形と色の落とし方。テーマや設定で変わったら呼び直して描き直す(#32、#40)。 */
    fun loadApps(style: IconStyle): List<AppEntry> {
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
                            installedAt = info.firstInstallTime,
                            icon = IconRenderer.render(info.getIcon(densityDpi), ICON_PX, style.shape, style.tint, style.accent).asImageBitmap(),
                        )
                    }
            }
            .sortedBy { it.label.lowercase() }
    }

    /** opts は ActivityOptions.toBundle()。アイコンから画面が広がる動きに使う(#23)。 */
    fun launch(entry: AppEntry, sourceBounds: Rect? = null, opts: Bundle? = null) {
        launcherApps.startMainActivity(entry.componentName, entry.user, sourceBounds, opts)
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
