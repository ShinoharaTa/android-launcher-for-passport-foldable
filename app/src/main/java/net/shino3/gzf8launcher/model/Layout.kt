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
 * 配置の全体(docs/04「配置と見た目を別のファイルに分ける」)。
 * メイン画面のアンカーゾーンは cover をそのまま参照するので、ここには持たない。
 */
@Serializable
data class Layout(
    val version: Int = 1,
    val cover: Zone = Zone(),
    val extension: Zone = Zone(),
    val dock: List<Item> = emptyList(),
)
