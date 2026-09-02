package net.shino3.gzf8launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.FolderRule
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.widget.WidgetRegistry
import net.shino3.gzf8launcher.ui.drag.DragSession
import net.shino3.gzf8launcher.ui.drag.DropTarget

/** 画面に流す状態と、レイアウトへの編集操作の入口。アクティビティの lifecycleScope で動く。 */
class LauncherController(private val context: Context, private val scope: CoroutineScope) {
    private val appRepository = AppRepository(context)
    private val layoutRepository = LayoutRepository(context)
    private val usageRepository = UsageRepository(context)

    private val _apps = MutableStateFlow<Map<AppKey, AppEntry>>(emptyMap())
    val apps: StateFlow<Map<AppKey, AppEntry>> = _apps
    val layout: StateFlow<Layout> = layoutRepository.layout

    private val _usage = MutableStateFlow<Map<String, UsageRepository.PackageUsage>>(emptyMap())
    private val _usagePermitted = MutableStateFlow(false)
    val usagePermitted: StateFlow<Boolean> = _usagePermitted

    private val _homeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** HOME キーで再表示されたとき。重ね描きを閉じる合図。 */
    val homeSignal: SharedFlow<Unit> = _homeSignal

    fun start() {
        scope.launch { layoutRepository.load() }
        scope.launch {
            refreshApps()
            appRepository.changes().collect { refreshApps() }
        }
    }

    /** 前面に戻るたびに呼ぶ。使用状況の権限と集計を更新する。 */
    fun onResume() {
        scope.launch { refreshUsage() }
    }

    fun signalHome() {
        _homeSignal.tryEmit(Unit)
    }

    fun launch(entry: AppEntry) = appRepository.launch(entry)

    fun toAppItem(entry: AppEntry) = AppItem(
        component = entry.componentName.flattenToString(),
        user = entry.userSerial.takeIf { it != 0L },
    )

    fun openAppDetails(item: AppItem) {
        val entry = _apps.value[item.key] ?: return
        context.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(entry.componentName, entry.user, null, null)
    }

    fun requestUsagePermission() {
        context.startActivity(usageRepository.settingsIntent())
    }

    /** 規則つきフォルダの中身を解決する。手動フォルダはそのまま。 */
    fun resolveFolder(folder: FolderItem): List<AppItem> {
        val entries = _apps.value.values
        return when (val rule = folder.rule) {
            FolderRule.Manual -> folder.apps
            is FolderRule.Recent -> rankByUsage(entries) { it.lastUsed }.take(rule.limit)
            is FolderRule.Frequent -> rankByUsage(entries) { it.launches.toLong() }.take(rule.limit)
            is FolderRule.Category -> entries
                .filter { it.category == rule.category }
                .sortedBy { it.label.lowercase() }
                .take(rule.limit)
                .map { toAppItem(it) }
        }
    }

    private fun rankByUsage(entries: Collection<AppEntry>, score: (UsageRepository.PackageUsage) -> Long): List<AppItem> {
        val usage = _usage.value
        return entries
            .mapNotNull { entry -> usage[entry.componentName.packageName]?.let { entry to score(it) } }
            .sortedByDescending { it.second }
            .distinctBy { it.first.componentName.packageName }
            .map { toAppItem(it.first) }
    }

    // ---- 編集 ----

    fun drop(session: DragSession, target: DropTarget?, columns: Int, dockSlots: Int) {
        if (target == null) return
        val p = session.payload
        edit { layout ->
            val base = p.source?.let { LayoutEditor.remove(layout, it) } ?: layout
            when (target) {
                is DropTarget.Remove -> if (p.source == null) null else base
                is DropTarget.Grid -> {
                    val (col, row) = target.cellFor(session.position, p.w, p.h)
                    LayoutEditor.dropOnGrid(base, target.zone, col, row, p.item, p.w, p.h, target.columns)
                }
                is DropTarget.Dock -> LayoutEditor.dropOnDock(base, target.slotAt(session.position), p.item, dockSlots)
            }
        }
    }

    fun remove(ref: ItemRef) = edit { LayoutEditor.remove(it, ref) }

    fun renameFolder(ref: ItemRef, name: String) = edit { LayoutEditor.rename(it, ref, name) }

    fun setFolderRule(ref: ItemRef, rule: FolderRule) = edit { layout ->
        val folder = LayoutEditor.itemAt(layout, ref) as? FolderItem ?: return@edit null
        LayoutEditor.replace(layout, ref, folder.copy(rule = rule))
    }

    fun resize(ref: ItemRef, dw: Int, dh: Int, columns: Int) = edit { layout ->
        val p = LayoutEditor.placementOf(layout, ref) ?: return@edit null
        val w = p.w + dw
        val h = p.h + dh
        val spec = (LayoutEditor.itemAt(layout, ref) as? NativeWidgetItem)?.let { WidgetRegistry.get(it.widget)?.spec }
        if (spec != null && (w !in spec.minW..spec.maxW || h !in spec.minH..spec.maxH)) return@edit null
        LayoutEditor.resize(layout, ref, w, h, columns)
    }

    /** 拒否された(null を返した)操作はレイアウトを変えない。 */
    private fun edit(transform: (Layout) -> Layout?) {
        scope.launch { layoutRepository.update { transform(it) ?: it } }
    }

    private suspend fun refreshApps() {
        _apps.value = withContext(Dispatchers.IO) { appRepository.loadApps() }.associateBy { it.key }
    }

    private suspend fun refreshUsage() {
        _usagePermitted.value = usageRepository.hasPermission()
        _usage.value = withContext(Dispatchers.IO) { usageRepository.query(days = 7) }
    }
}
