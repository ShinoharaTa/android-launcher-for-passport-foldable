package net.shino3.gzf8launcher.model

import kotlin.math.abs

/**
 * レイアウトに対する編集操作。すべて純粋関数で、拒否する操作は null を返す。
 * 種類ごとの結合規則(留めて重ねるとフォルダになる、など)と、押しのけの規則はここに集める。
 */
object LayoutEditor {
    const val DEFAULT_FOLDER_NAME = "FOLDER"

    /** 落としたときに何が起きるか。ドラッグ中の枠の描き分けに使う(#25)。 */
    enum class DropKind { PLACE, PUSH, MERGE, REJECT }

    fun itemAt(layout: Layout, ref: ItemRef): Item? = when (ref) {
        is ItemRef.Grid -> layout.zone(ref.zone).items.getOrNull(ref.index)?.item
        is ItemRef.Dock -> layout.dock.getOrNull(ref.index)
        is ItemRef.InFolder -> (itemAt(layout, ref.folder) as? FolderItem)?.apps?.getOrNull(ref.index)
    }

    fun placementOf(layout: Layout, ref: ItemRef): Placement? =
        (ref as? ItemRef.Grid)?.let { layout.zone(it.zone).items.getOrNull(it.index)?.placement }

    fun replace(layout: Layout, ref: ItemRef, item: Item): Layout = when (ref) {
        is ItemRef.Grid -> {
            val zone = layout.zone(ref.zone)
            layout.withZone(ref.zone, zone.copy(items = zone.items.mapIndexed { i, p -> if (i == ref.index) p.copy(item = item) else p }))
        }
        is ItemRef.Dock -> layout.copy(dock = layout.dock.mapIndexed { i, it -> if (i == ref.index) item else it })
        is ItemRef.InFolder -> {
            val folder = itemAt(layout, ref.folder) as? FolderItem ?: return layout
            val app = item as? AppItem ?: return layout
            replace(layout, ref.folder, folder.copy(apps = folder.apps.mapIndexed { i, a -> if (i == ref.index) app else a }))
        }
    }

    /**
     * 取り除く。フォルダから抜いて空になったらフォルダごと消す。
     * 中身が 1 個になっても解かない(#25)。フォルダを消すのはメニューからだけにする。
     */
    fun remove(layout: Layout, ref: ItemRef): Layout = when (ref) {
        is ItemRef.Grid -> {
            val zone = layout.zone(ref.zone)
            layout.withZone(ref.zone, zone.copy(items = zone.items.filterIndexed { i, _ -> i != ref.index }))
        }
        is ItemRef.Dock -> layout.copy(dock = layout.dock.filterIndexed { i, _ -> i != ref.index })
        is ItemRef.InFolder -> {
            val folder = itemAt(layout, ref.folder) as? FolderItem ?: return layout
            val rest = folder.apps.filterIndexed { i, _ -> i != ref.index }
            when {
                folder.rule != FolderRule.Manual -> replace(layout, ref.folder, folder.copy(apps = rest))
                rest.isEmpty() -> remove(layout, ref.folder)
                else -> replace(layout, ref.folder, folder.copy(apps = rest))
            }
        }
    }

    fun rename(layout: Layout, ref: ItemRef, name: String): Layout {
        val folder = itemAt(layout, ref) as? FolderItem ?: return layout
        return replace(layout, ref, folder.copy(name = name))
    }

    /** ウィジェットの大きさを変える。列や段からはみ出す、他と重なる場合は null。rows は段数が固定のゾーンで渡す。 */
    fun resize(layout: Layout, ref: ItemRef, w: Int, h: Int, columns: Int, rows: Int? = null): Layout? {
        val grid = ref as? ItemRef.Grid ?: return null
        val zone = layout.zone(grid.zone)
        val placed = zone.items.getOrNull(grid.index) ?: return null
        if (w < 1 || h < 1 || placed.col + w > columns) return null
        if (rows != null && placed.row + h > rows) return null
        val next = placed.copy(w = w, h = h)
        if (zone.items.filterIndexed { i, _ -> i != grid.index }.any { it.overlaps(next) }) return null
        return layout.withZone(grid.zone, zone.copy(items = zone.items.mapIndexed { i, p -> if (i == grid.index) next else p }))
    }

    /** 落としたら何が起きるかだけを返す。ドラッグ中の枠の描き分けに使う。 */
    fun classify(
        layout: Layout,
        source: ItemRef?,
        zoneId: ZoneId,
        col: Int,
        row: Int,
        w: Int,
        h: Int,
        item: Item,
        columns: Int,
        rows: Int?,
        dwell: Boolean,
    ): DropKind {
        if (col < 0 || row < 0 || col + w > columns || (rows != null && row + h > rows)) return DropKind.REJECT
        val base = source?.let { remove(layout, it) } ?: layout
        val zone = base.zone(zoneId)
        val occupant = zone.items.firstOrNull { it.contains(col, row) }
        if (occupant != null && dwell && merge(occupant.item, item) != null) return DropKind.MERGE
        val candidate = PlacedItem(col, row, w, h, item)
        if (zone.items.none { it.overlaps(candidate) }) return DropKind.PLACE
        return if (reflow(zone, candidate, columns, rows) != null) DropKind.PUSH else DropKind.REJECT
    }

    /**
     * ゾーンの (col,row) に置く。
     * 空きなら追加。占有なら押しのける(相手を最も近い空きへ動かす)。
     * dwell(留めてから離した)のときだけ、アプリ同士ならフォルダに、フォルダの上なら中に入れる。
     */
    fun dropOnGrid(
        layout: Layout,
        zoneId: ZoneId,
        col: Int,
        row: Int,
        item: Item,
        w: Int,
        h: Int,
        columns: Int,
        rows: Int? = null,
        dwell: Boolean = false,
    ): Layout? {
        if (col < 0 || row < 0 || col + w > columns) return null
        if (rows != null && row + h > rows) return null
        val zone = layout.zone(zoneId)
        if (dwell) {
            val occupantIndex = zone.items.indexOfFirst { it.contains(col, row) }
            if (occupantIndex >= 0) {
                val merged = merge(zone.items[occupantIndex].item, item)
                if (merged != null) {
                    return layout.withZone(zoneId, zone.copy(items = zone.items.mapIndexed { i, p -> if (i == occupantIndex) p.copy(item = merged) else p }))
                }
            }
        }
        val next = reflow(zone, PlacedItem(col, row, w, h, item), columns, rows) ?: return null
        return layout.withZone(zoneId, next)
    }

    /** ドックに落としたら何が起きるか。留めて輪を出すかの判定に使う。 */
    fun classifyDock(layout: Layout, source: ItemRef?, slot: Int, item: Item, slots: Int, dwell: Boolean): DropKind {
        val base = source?.let { remove(layout, it) } ?: layout
        val occupant = base.dock.getOrNull(slot)
        if (occupant != null && dwell && merge(occupant, item) != null) return DropKind.MERGE
        return if (dropOnDock(base, slot, item, slots, dwell = false) != null) DropKind.PLACE else DropKind.REJECT
    }

    fun dropOnDock(layout: Layout, slot: Int, item: Item, slots: Int, dwell: Boolean = false): Layout? {
        if (item !is AppItem && item !is FolderItem) return null
        val dock = layout.dock
        if (slot < dock.size) {
            if (dwell) {
                val merged = merge(dock[slot], item)
                if (merged != null) return layout.copy(dock = dock.mapIndexed { i, it -> if (i == slot) merged else it })
            }
            if (dock.size >= slots) return null
            return layout.copy(dock = dock.toMutableList().apply { add(slot, item) })
        }
        if (dock.size >= slots) return null
        return layout.copy(dock = dock + item)
    }

    /** アプリ + アプリ → 新しいフォルダ。手動フォルダ + アプリ → 追加。それ以外は結合しない。 */
    private fun merge(occupant: Item, incoming: Item): Item? = when {
        occupant is AppItem && incoming is AppItem -> FolderItem(DEFAULT_FOLDER_NAME, listOf(occupant, incoming))
        occupant is FolderItem && incoming is AppItem && occupant.rule == FolderRule.Manual -> occupant.copy(apps = occupant.apps + incoming)
        else -> null
    }

    /**
     * candidate を置き、重なる要素を最も近い空きへ動かす(押しのけ、#25)。
     * 近いものから順に動かし、どれか一つでも置き場が無ければ null。
     */
    private fun reflow(zone: Zone, candidate: PlacedItem, columns: Int, rows: Int?): Zone? {
        val (displaced, stay) = zone.items.partition { it.overlaps(candidate) }
        if (displaced.isEmpty()) return zone.copy(items = zone.items + candidate)
        val limitRows = rows ?: (maxOf(zone.occupiedRows, candidate.row + candidate.h) + displaced.sumOf { it.h } + SLACK_ROWS)
        val placed = mutableListOf(candidate)
        placed += stay
        val moved = mutableListOf<PlacedItem>()
        for (item in displaced.sortedBy { abs(it.col - candidate.col) + abs(it.row - candidate.row) }) {
            val spot = nearestFree(item, placed, columns, limitRows) ?: return null
            val relocated = item.copy(col = spot.first, row = spot.second)
            placed += relocated
            moved += relocated
        }
        return zone.copy(items = stay + moved + candidate)
    }

    /** item を置ける、元の位置から最も近いセル。無ければ null。 */
    private fun nearestFree(item: PlacedItem, occupied: List<PlacedItem>, columns: Int, rows: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0..(rows - item.h)) {
            for (c in 0..(columns - item.w)) {
                val moved = item.copy(col = c, row = r)
                if (occupied.any { it.overlaps(moved) }) continue
                val distance = abs(c - item.col) + abs(r - item.row)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = c to r
                }
            }
        }
        return best
    }

    private const val SLACK_ROWS = 2

    private fun PlacedItem.contains(c: Int, r: Int) = c in col until col + w && r in row until row + h

    private fun PlacedItem.overlaps(o: PlacedItem) =
        col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h
}
