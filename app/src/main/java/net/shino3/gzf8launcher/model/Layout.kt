package net.shino3.gzf8launcher.model

import kotlinx.serialization.Serializable

/** グリッド上の矩形。col/row は 0 始まり、w/h はセル数。 */
@Serializable
data class Placement(val col: Int, val row: Int, val w: Int = 1, val h: Int = 1)

/** ゾーンに置かれたアイテム。JSON では配置のフィールドを item と同じ階層に平らに書く。 */
@Serializable
data class PlacedItem(
    val col: Int,
    val row: Int,
    val w: Int = 1,
    val h: Int = 1,
    val item: Item,
) {
    val placement: Placement get() = Placement(col, row, w, h)
}

@Serializable
data class Zone(val items: List<PlacedItem> = emptyList()) {
    /** 置かれているアイテムが占める段数。空なら 0。 */
    val occupiedRows: Int get() = items.maxOfOrNull { it.row + it.h } ?: 0
}

/**
 * 配置の全体(docs/04「配置と見た目を別のファイルに分ける」)。
 * ウィジェット面は縦にいくらでも伸び、アプリは固定の段数のページを横にめくる(#19、#21)。
 * カバーではウィジェット面とアプリの各ページを横にめくり、開くと左にウィジェット面、右にアプリのページが並ぶ。
 * version 3 以前は読み込み時に移す。
 */
@Serializable
data class Layout(
    val version: Int = CURRENT_VERSION,
    /** ウィジェットと大型フォルダの面。 */
    val widgets: Zone = Zone(),
    /** アプリのページ。少なくとも 1 枚。先頭が HOME の着地。 */
    val pages: List<Zone> = listOf(Zone()),
    val dock: List<Item> = emptyList(),
) {
    /** 末尾に空のページを足す。ドラッグで右端に留めたときに使う。 */
    fun withNewPage(): Layout = copy(pages = pages + Zone())

    /** 空のページを消す。ただし最低 1 枚は残す。 */
    fun withoutEmptyPages(): Layout {
        val kept = pages.filter { it.items.isNotEmpty() }
        return copy(pages = kept.ifEmpty { listOf(Zone()) })
    }

    companion object {
        const val CURRENT_VERSION = 4
    }
}
