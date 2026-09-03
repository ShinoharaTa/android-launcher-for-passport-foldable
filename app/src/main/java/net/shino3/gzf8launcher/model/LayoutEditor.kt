package net.shino3.gzf8launcher.model

/**
 * レイアウトに対する編集操作。すべて純粋関数で、拒否する操作は null を返す。
 * 種類ごとの結合規則(アプリ同士を重ねるとフォルダになる、など)はここに集める。
 */
object LayoutEditor {
    const val DEFAULT_FOLDER_NAME = "FOLDER"

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

    /** 取り除く。フォルダから抜いて 1 個になったらフォルダを解いてアプリに戻し、0 個ならフォルダごと消す。 */
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
                rest.size == 1 -> replace(layout, ref.folder, rest.single())
                else -> replace(layout, ref.folder, folder.copy(apps = rest))
            }
        }
    }

    fun rename(layout: Layout, ref: ItemRef, name: String): Layout {
        val folder = itemAt(layout, ref) as? FolderItem ?: return layout
        return replace(layout, ref, folder.copy(name = name))
    }

    /** ウィジェットの大きさを変える。列や段からはみ出す、他と重なる場合は null。rows は棚のように段数が固定のゾーンで渡す。 */
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

    /**
     * ゾーンの (col,row) に置く。
     * 空きなら追加。占有者がアプリで置くのもアプリならフォルダにまとめる。占有者がフォルダで置くのがアプリなら中に入れる。
     */
    fun dropOnGrid(layout: Layout, zoneId: ZoneId, col: Int, row: Int, item: Item, w: Int, h: Int, columns: Int, rows: Int? = null): Layout? {
        if (col < 0 || row < 0 || col + w > columns) return null
        if (rows != null && row + h > rows) return null
        val zone = layout.zone(zoneId)
        val occupantIndex = zone.items.indexOfFirst { it.contains(col, row) }
        if (occupantIndex >= 0) {
            val merged = merge(zone.items[occupantIndex].item, item) ?: return null
            return layout.withZone(zoneId, zone.copy(items = zone.items.mapIndexed { i, p -> if (i == occupantIndex) p.copy(item = merged) else p }))
        }
        val candidate = PlacedItem(col, row, w, h, item)
        if (zone.items.any { it.overlaps(candidate) }) return null
        return layout.withZone(zoneId, zone.copy(items = zone.items + candidate))
    }

    fun dropOnDock(layout: Layout, slot: Int, item: Item, slots: Int): Layout? {
        if (item !is AppItem && item !is FolderItem) return null
        val dock = layout.dock
        if (slot < dock.size) {
            val merged = merge(dock[slot], item)
            if (merged != null) return layout.copy(dock = dock.mapIndexed { i, it -> if (i == slot) merged else it })
            if (dock.size >= slots) return null
            return layout.copy(dock = dock.toMutableList().apply { add(slot, item) })
        }
        if (dock.size >= slots) return null
        return layout.copy(dock = dock + item)
    }

    /** アプリ + アプリ → 新しいフォルダ。フォルダ + アプリ → 追加。それ以外は結合しない。 */
    private fun merge(occupant: Item, incoming: Item): Item? = when {
        occupant is AppItem && incoming is AppItem -> FolderItem(DEFAULT_FOLDER_NAME, listOf(occupant, incoming))
        occupant is FolderItem && incoming is AppItem && occupant.rule == FolderRule.Manual -> occupant.copy(apps = occupant.apps + incoming)
        else -> null
    }

    private fun PlacedItem.contains(c: Int, r: Int) = c in col until col + w && r in row until row + h

    private fun PlacedItem.overlaps(o: PlacedItem) =
        col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h
}
