package net.shino3.gzf8launcher.data

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
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
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.ThemeRepository
import net.shino3.gzf8launcher.theme.ThemeSpec
import net.shino3.gzf8launcher.widget.AppWidgetHostManager
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.FolderRule
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.model.ShortcutItem
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.widget.WidgetRegistry
import net.shino3.gzf8launcher.ui.drag.DragSession
import net.shino3.gzf8launcher.ui.drag.DropTarget

/** 画面に流す状態と、レイアウトへの編集操作の入口。アクティビティの lifecycleScope で動く。 */
class LauncherController(private val context: Context, private val scope: CoroutineScope) {
    private val appRepository = AppRepository(context)
    private val layoutRepository = LayoutRepository(context)
    private val usageRepository = UsageRepository(context)
    private val themeRepository = ThemeRepository(context)
    private val shortcutRepository = ShortcutRepository(context)
    val appWidgets = AppWidgetHostManager(context)

    /** バインド許可と設定アクティビティはアクティビティの結果が要るので、その部分だけ外に出す。 */
    interface AppWidgetBindHost {
        fun requestBind(appWidgetId: Int, provider: ComponentName, profile: UserHandle)
        fun requestConfigure(appWidgetId: Int)
    }

    var bindHost: AppWidgetBindHost? = null

    private class PendingAppWidget(
        val appWidgetId: Int,
        val info: AppWidgetProviderInfo,
        val zone: ZoneId,
        val col: Int,
        val row: Int,
        val w: Int,
        val h: Int,
        val columns: Int,
    )

    private var pendingAppWidget: PendingAppWidget? = null

    private val _apps = MutableStateFlow<Map<AppKey, AppEntry>>(emptyMap())
    val apps: StateFlow<Map<AppKey, AppEntry>> = _apps
    val layout: StateFlow<Layout> = layoutRepository.layout

    private val _usage = MutableStateFlow<Map<String, UsageRepository.PackageUsage>>(emptyMap())
    private val _usagePermitted = MutableStateFlow(false)
    val usagePermitted: StateFlow<Boolean> = _usagePermitted

    val theme: StateFlow<LauncherTheme> = themeRepository.theme

    private val _themes = MutableStateFlow<List<ThemeSpec>>(emptyList())
    /** 設定画面に出す同梱テーマ。 */
    val themes: StateFlow<List<ThemeSpec>> = _themes

    private val _homeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** HOME キーで再表示されたとき。重ね描きを閉じる合図。 */
    val homeSignal: SharedFlow<Unit> = _homeSignal

    fun start() {
        scope.launch { layoutRepository.load() }
        scope.launch {
            themeRepository.load()
            _themes.value = themeRepository.bundled()
        }
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

    // ---- ショートカット(#11) ----

    /** そのアプリが持つ Android のショートカット。既定ホームでないと空になる。 */
    suspend fun shortcutsFor(item: AppItem): List<ShortcutEntry> {
        val entry = _apps.value[item.key] ?: return emptyList()
        return withContext(Dispatchers.IO) { shortcutRepository.forActivity(entry.componentName, entry.user) }
    }

    suspend fun resolveShortcut(item: ShortcutItem): ShortcutEntry? =
        withContext(Dispatchers.IO) { shortcutRepository.resolve(item) }

    fun launchShortcut(item: ShortcutItem) = shortcutRepository.launch(item)

    fun applyTheme(spec: ThemeSpec) {
        scope.launch { themeRepository.apply(spec) }
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
        val item = p.item
        // ホームに置くショートカットは、アプリ側から消えないように固定しておく
        if (item is ShortcutItem && p.source == null) {
            scope.launch { withContext(Dispatchers.IO) { shortcutRepository.pin(item) } }
        }
        if (item is AppWidgetItem && item.appWidgetId < 0) {
            // ドロワーから来た新しい AppWidget。バインドしてから置く
            if (target is DropTarget.Grid) {
                val (col, row) = target.cellFor(session.position, p.w, p.h)
                beginAppWidgetPlacement(item, target.zone, col, row, p.w, p.h, target.columns)
            }
            return
        }
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

    // ---- AppWidget のバインド ----

    private fun beginAppWidgetPlacement(item: AppWidgetItem, zone: ZoneId, col: Int, row: Int, w: Int, h: Int, columns: Int) {
        val provider = ComponentName.unflattenFromString(item.provider) ?: return
        val info = appWidgets.providerInfo(provider) ?: return
        val id = appWidgets.allocateId()
        pendingAppWidget = PendingAppWidget(id, info, zone, col, row, w, h, columns)
        if (appWidgets.bindIfAllowed(id, provider, info.profile)) {
            onBindResult(true)
        } else {
            bindHost?.requestBind(id, provider, info.profile) ?: onBindResult(false)
        }
    }

    fun onBindResult(ok: Boolean) {
        val pending = pendingAppWidget ?: return
        if (!ok) {
            cancelPendingAppWidget()
            return
        }
        if (pending.info.configure != null) {
            bindHost?.requestConfigure(pending.appWidgetId) ?: onConfigureResult(false)
        } else {
            onConfigureResult(true)
        }
    }

    fun onConfigureResult(ok: Boolean) {
        val pending = pendingAppWidget ?: return
        pendingAppWidget = null
        if (!ok) {
            appWidgets.deleteId(pending.appWidgetId)
            return
        }
        val item = AppWidgetItem(pending.info.provider.flattenToString(), pending.appWidgetId)
        val next = LayoutEditor.dropOnGrid(layout.value, pending.zone, pending.col, pending.row, item, pending.w, pending.h, pending.columns)
        if (next == null) {
            appWidgets.deleteId(pending.appWidgetId)
            return
        }
        scope.launch { layoutRepository.update { next } }
    }

    private fun cancelPendingAppWidget() {
        pendingAppWidget?.let { appWidgets.deleteId(it.appWidgetId) }
        pendingAppWidget = null
    }

    /** 拒否された(null を返した)操作はレイアウトを変えない。消えた AppWidget の ID は解放する。 */
    private fun edit(transform: (Layout) -> Layout?) {
        scope.launch {
            layoutRepository.update { layout ->
                val next = transform(layout) ?: return@update layout
                (appWidgetIds(layout) - appWidgetIds(next)).forEach { appWidgets.deleteId(it) }
                next
            }
        }
    }

    private fun appWidgetIds(layout: Layout): Set<Int> =
        (layout.cover.items + layout.extension.items)
            .map { it.item }
            .filterIsInstance<AppWidgetItem>()
            .map { it.appWidgetId }
            .toSet()

    private suspend fun refreshApps() {
        _apps.value = withContext(Dispatchers.IO) { appRepository.loadApps() }.associateBy { it.key }
    }

    private suspend fun refreshUsage() {
        _usagePermitted.value = usageRepository.hasPermission()
        _usage.value = withContext(Dispatchers.IO) { usageRepository.query(days = 7) }
    }
}
