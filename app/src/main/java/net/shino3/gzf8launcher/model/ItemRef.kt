package net.shino3.gzf8launcher.model

enum class ZoneId { COVER, EXTENSION }

/** レイアウト内のアイテムの位置を指す参照。編集操作(移動、削除、名前変更)の対象指定に使う。 */
sealed interface ItemRef {
    data class Grid(val zone: ZoneId, val index: Int) : ItemRef
    data class Dock(val index: Int) : ItemRef
    /** フォルダの中のアプリ。folder はそのフォルダ自身の参照。 */
    data class InFolder(val folder: ItemRef, val index: Int) : ItemRef
}

fun Layout.zone(id: ZoneId): Zone = when (id) {
    ZoneId.COVER -> cover
    ZoneId.EXTENSION -> extension
}

fun Layout.withZone(id: ZoneId, zone: Zone): Layout = when (id) {
    ZoneId.COVER -> copy(cover = zone)
    ZoneId.EXTENSION -> copy(extension = zone)
}
