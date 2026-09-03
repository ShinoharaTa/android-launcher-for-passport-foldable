package net.shino3.gzf8launcher.model

import android.content.ComponentName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** アプリを一意に指すキー。component は ComponentName.flattenToString() の完全形。 */
data class AppKey(val component: String, val userSerial: Long)

/**
 * ホームに置けるものすべての共通型(docs/04)。
 * JSON では "type" フィールドで種類を区別する。
 */
@Serializable
sealed interface Item

@Serializable
@SerialName("app")
data class AppItem(
    val component: String,
    /** UserManager.getSerialNumberForUser の値。null は主ユーザー。 */
    val user: Long? = null,
) : Item {
    /** "pkg/.Cls" の短縮形でも受け付け、比較用に完全形へ正規化する。 */
    val key: AppKey
        get() = AppKey(
            ComponentName.unflattenFromString(component)?.flattenToString() ?: component,
            user ?: 0L,
        )
}

@Serializable
@SerialName("folder")
data class FolderItem(
    val name: String,
    /** 手動フォルダの中身。規則つきフォルダでは無視され、規則で解決した結果が表示される。 */
    val apps: List<AppItem> = emptyList(),
    val rule: FolderRule = FolderRule.Manual,
) : Item

/** フォルダの中身の決まり方(docs/04「フォルダとグループの区別」)。 */
@Serializable
sealed interface FolderRule {
    @Serializable @SerialName("manual") data object Manual : FolderRule
    @Serializable @SerialName("recent") data class Recent(val limit: Int = 9) : FolderRule
    @Serializable @SerialName("frequent") data class Frequent(val limit: Int = 9, val days: Int = 7) : FolderRule
    /** ApplicationInfo.CATEGORY_* の値。 */
    @Serializable @SerialName("category") data class Category(val category: Int, val limit: Int = 12) : FolderRule
}

/** ホームに固定した Android のショートカット。中身はアプリ側が持つので参照だけを保存する。 */
@Serializable
@SerialName("shortcut")
data class ShortcutItem(
    val packageName: String,
    val shortcutId: String,
    val user: Long? = null,
    /** 引き直せなかったときに出す控えの表示名。 */
    val label: String = "",
) : Item

@Serializable
@SerialName("widget")
data class NativeWidgetItem(
    val widget: String,
    val config: JsonObject = JsonObject(emptyMap()),
) : Item

@Serializable
@SerialName("appwidget")
data class AppWidgetItem(
    val provider: String,
    val appWidgetId: Int = -1,
) : Item
