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

/**
 * 手動でまとめたアプリの並び。
 * 規則で中身が決まるフォルダ(最近、よく使う、カテゴリ)は #29 で廃止した。その用途はドロワーの絞り込みが担う。
 * 配置ファイル version 4 以前の規則つきフォルダは、読み込み時に消す。
 */
@Serializable
@SerialName("folder")
data class FolderItem(
    val name: String,
    val apps: List<AppItem> = emptyList(),
) : Item

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
