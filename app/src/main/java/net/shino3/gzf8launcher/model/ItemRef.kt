package net.shino3.gzf8launcher.model

/** ゾーンの識別子。面(カバー / 拡張)と、面の中の上下(ウィジェット面 / アプリ棚)の組。 */
enum class ZoneId(val isShelf: Boolean) {
    COVER_WIDGETS(false),
    COVER_SHELF(true),
    EXTENSION_WIDGETS(false),
    EXTENSION_SHELF(true),
}

/** レイアウト内のアイテムの位置を指す参照。編集操作(移動、削除、名前変更)の対象指定に使う。 */
sealed interface ItemRef {
    data class Grid(val zone: ZoneId, val index: Int) : ItemRef
    data class Dock(val index: Int) : ItemRef
    /** フォルダの中のアプリ。folder はそのフォルダ自身の参照。 */
    data class InFolder(val folder: ItemRef, val index: Int) : ItemRef
}

fun Layout.zone(id: ZoneId): Zone = when (id) {
    ZoneId.COVER_WIDGETS -> cover.widgets
    ZoneId.COVER_SHELF -> cover.shelf
    ZoneId.EXTENSION_WIDGETS -> extension.widgets
    ZoneId.EXTENSION_SHELF -> extension.shelf
}

fun Layout.withZone(id: ZoneId, zone: Zone): Layout = when (id) {
    ZoneId.COVER_WIDGETS -> copy(cover = cover.copy(widgets = zone))
    ZoneId.COVER_SHELF -> copy(cover = cover.copy(shelf = zone))
    ZoneId.EXTENSION_WIDGETS -> copy(extension = extension.copy(widgets = zone))
    ZoneId.EXTENSION_SHELF -> copy(extension = extension.copy(shelf = zone))
}

/** すべてのゾーン。AppWidget の ID を集めるときなどに使う。 */
fun Layout.allZones(): List<Zone> = ZoneId.entries.map { zone(it) }
