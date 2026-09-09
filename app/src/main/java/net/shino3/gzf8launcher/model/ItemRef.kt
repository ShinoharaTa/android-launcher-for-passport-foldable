package net.shino3.gzf8launcher.model

/** ゾーンの識別子。ウィジェット面と、アプリの各ページ(#21)。 */
sealed interface ZoneId {
    data object Widgets : ZoneId
    data class Page(val index: Int) : ZoneId
}

/** レイアウト内のアイテムの位置を指す参照。編集操作(移動、削除、名前変更)の対象指定に使う。 */
sealed interface ItemRef {
    data class Grid(val zone: ZoneId, val index: Int) : ItemRef
    data class Dock(val index: Int) : ItemRef
    /** フォルダの中のアプリ。folder はそのフォルダ自身の参照。 */
    data class InFolder(val folder: ItemRef, val index: Int) : ItemRef
}

fun Layout.zone(id: ZoneId): Zone = when (id) {
    ZoneId.Widgets -> widgets
    is ZoneId.Page -> pages.getOrElse(id.index) { Zone() }
}

fun Layout.withZone(id: ZoneId, zone: Zone): Layout = when (id) {
    ZoneId.Widgets -> copy(widgets = zone)
    is ZoneId.Page -> copy(
        pages = pages.toMutableList().also { list ->
            while (list.size <= id.index) list.add(Zone())
            list[id.index] = zone
        },
    )
}

/** すべてのゾーン。AppWidget の ID を集めるときなどに使う。 */
fun Layout.allZones(): List<Zone> = listOf(widgets) + pages
