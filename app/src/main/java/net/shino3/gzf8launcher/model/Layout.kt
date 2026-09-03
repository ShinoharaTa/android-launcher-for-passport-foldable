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
data class Zone(val items: List<PlacedItem> = emptyList())

/**
 * 一つの面(カバー、または拡張パネル)。上のウィジェット面と、下端に固定したアプリ棚の二つを持つ(#16)。
 * 棚の段数はテーマ(grid.shelfRows)で決まり、画面の高さが変わっても棚はドックのすぐ上に留まる。
 * どちらに何を置くかは縛らない。
 */
@Serializable
data class Panel(
    val widgets: Zone = Zone(),
    val shelf: Zone = Zone(),
)

/**
 * 配置の全体(docs/04「配置と見た目を別のファイルに分ける」)。
 * メイン画面のアンカーゾーンは cover をそのまま参照するので、ここには持たない。
 * version 1(面が widgets / shelf に分かれていない)は読み込み時に移す。
 */
@Serializable
data class Layout(
    val version: Int = CURRENT_VERSION,
    val cover: Panel = Panel(),
    val extension: Panel = Panel(),
    val dock: List<Item> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}
