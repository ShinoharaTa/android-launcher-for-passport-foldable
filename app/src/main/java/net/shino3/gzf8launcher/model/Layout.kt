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
 * ホームは 2 ページで、どちらも縦にいくらでも伸びる(#19)。
 * カバーではページを横にめくり、開くとそのページが左右に並ぶ。
 * version 1 と 2 は読み込み時に移す。
 */
@Serializable
data class Layout(
    val version: Int = CURRENT_VERSION,
    /** ページ 1。ウィジェットと大型フォルダの面。 */
    val widgets: Zone = Zone(),
    /** ページ 2。アプリとフォルダの面。HOME の着地。 */
    val apps: Zone = Zone(),
    val dock: List<Item> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 3
    }
}
