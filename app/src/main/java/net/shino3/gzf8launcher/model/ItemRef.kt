package net.shino3.gzf8launcher.model

/** ゾーンの識別子。ホームの 2 ページに対応する。 */
enum class ZoneId {
    WIDGETS,
    APPS,
}

/** レイアウト内のアイテムの位置を指す参照。編集操作(移動、削除、名前変更)の対象指定に使う。 */
sealed interface ItemRef {
    data class Grid(val zone: ZoneId, val index: Int) : ItemRef
    data class Dock(val index: Int) : ItemRef
    /** フォルダの中のアプリ。folder はそのフォルダ自身の参照。 */
    data class InFolder(val folder: ItemRef, val index: Int) : ItemRef
}

fun Layout.zone(id: ZoneId): Zone = when (id) {
    ZoneId.WIDGETS -> widgets
    ZoneId.APPS -> apps
}

fun Layout.withZone(id: ZoneId, zone: Zone): Layout = when (id) {
    ZoneId.WIDGETS -> copy(widgets = zone)
    ZoneId.APPS -> copy(apps = zone)
}

/** すべてのゾーン。AppWidget の ID を集めるときなどに使う。 */
fun Layout.allZones(): List<Zone> = ZoneId.entries.map { zone(it) }
