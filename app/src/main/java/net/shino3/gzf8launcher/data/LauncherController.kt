package net.shino3.gzf8launcher.data

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.content.pm.LauncherApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import net.shino3.gzf8launcher.theme.FontChoice
import net.shino3.gzf8launcher.theme.IconShape
import net.shino3.gzf8launcher.theme.ThemeOverrides
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.model.allZones
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.ThemeRepository
import net.shino3.gzf8launcher.theme.ThemeSpec
import net.shino3.gzf8launcher.widget.AppWidgetHostManager
import net.shino3.gzf8launcher.model.FolderItem
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
    private val shortcutRepository = ShortcutRepository(context) { theme.value.iconStyle }
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
        val rows: Int?,
    )

    private var pendingAppWidget: PendingAppWidget? = null

    private val _apps = MutableStateFlow<Map<AppKey, AppEntry>>(emptyMap())
    val apps: StateFlow<Map<AppKey, AppEntry>> = _apps
    val layout: StateFlow<Layout> = layoutRepository.layout

    private val _usage = MutableStateFlow<Map<String, UsageRepository.PackageUsage>>(emptyMap())
    /** 直近 7 日の起動回数と最終起動。ドロワーの「最近」「よく使う」の絞り込みに使う(#29)。 */
    val usage: StateFlow<Map<String, UsageRepository.PackageUsage>> = _usage
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
        // アイコンの形が変わったら描き直す。最初の値はテーマ読み込み前の既定なので飛ばす
        scope.launch {
            theme.map { it.iconStyle }.distinctUntilChanged().drop(1).collect { refreshApps() }
        }
    }

    /** 前面に戻るたびに呼ぶ。使用状況の権限と集計を更新する。 */
    fun onResume() {
        scope.launch { refreshUsage() }
    }

    fun signalHome() {
        _homeSignal.tryEmit(Unit)
    }

    fun launch(entry: AppEntry, sourceBounds: Rect? = null, opts: Bundle? = null) =
        appRepository.launch(entry, sourceBounds, opts)

    fun toAppItem(entry: AppEntry) = AppItem(
        component = entry.componentName.flattenToString(),
        user = entry.userSerial.takeIf { it != 0L },
    )

    fun openAppDetails(item: AppItem) {
        val entry = _apps.value[item.key] ?: return
        context.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(entry.componentName, entry.user, null, null)
    }

    /**
     * アンインストールの確認を出す(#36)。システムアプリは端末側が無効化の確認に置き換える。
     * 仕事用プロファイルのアプリはそのユーザーで出す。
     */
    fun uninstall(item: AppItem) {
        val entry = _apps.value[item.key] ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.componentName.packageName}"))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "アンインストールの確認を出せなかった: ${entry.componentName.packageName}", it) }
    }

    /** 端末の壁紙の選択を開く(#36)。壁紙アプリが複数あれば選ばせる。 */
    fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- ショートカット(#11) ----

    /** そのアプリが持つ Android のショートカット。既定ホームでないと空になる。 */
    suspend fun shortcutsFor(item: AppItem): List<ShortcutEntry> {
        val entry = _apps.value[item.key] ?: return emptyList()
        return withContext(Dispatchers.IO) { shortcutRepository.forActivity(entry.componentName, entry.user) }
    }

    suspend fun resolveShortcut(item: ShortcutItem): ShortcutEntry? =
        withContext(Dispatchers.IO) { shortcutRepository.resolve(item) }

    fun launchShortcut(item: ShortcutItem, sourceBounds: Rect? = null, opts: Bundle? = null) =
        shortcutRepository.launch(item, sourceBounds, opts)

    fun applyTheme(spec: ThemeSpec) {
        scope.launch { themeRepository.apply(spec) }
    }

    /** 設定画面からの上書き(書体、アイコンの形)。テーマとは別に残る(#32)。 */
    val overrides: StateFlow<ThemeOverrides> = themeRepository.overrides

    fun setFont(choice: FontChoice) = themeRepository.setOverrides(overrides.value.copy(font = choice))

    /** null でテーマの指定に戻す。 */
    fun setIconShape(shape: IconShape?) = themeRepository.setOverrides(overrides.value.copy(iconShape = shape))

    /** 寸法などの上書きを部分的に変える(#34)。 */
    fun updateOverrides(transform: (ThemeOverrides) -> ThemeOverrides) = themeRepository.setOverrides(transform(overrides.value))

    fun requestUsagePermission() {
        context.startActivity(usageRepository.settingsIntent())
    }

    // ---- 編集 ----

    /**
     * 落とす。受け付けたら true。拒否は false で、呼び出し側が影を戻す(#25)。
     * dwell は「アプリの上に留めてから離した」印で、そのときだけフォルダにまとめる。
     */
    fun drop(session: DragSession, target: DropTarget?, columns: Int, dockSlots: Int, dwell: Boolean): Boolean {
        if (target == null) return false
        val p = session.payload
        val item = p.item
        if (item is AppWidgetItem && item.appWidgetId < 0) {
            // ウィジェット一覧から来た新しい AppWidget。バインドしてから置く
            if (target !is DropTarget.Grid) return false
            val (col, row) = target.cellFor(session.position, p.w, p.h)
            beginAppWidgetPlacement(item, target.zone, col, row, p.w, p.h, target.columns, target.rows)
            return true
        }
        val current = layout.value
        val base = p.source?.let { LayoutEditor.remove(current, it) } ?: current
        // ドロワーで複数選んだ束(#48)。落とした場所から順に空きへ並べ、あふれたら次のページへ
        if (p.rest.isNotEmpty() && target is DropTarget.Grid) {
            val (col, row) = target.cellFor(session.position, 1, 1)
            var (placedLayout, left) = LayoutEditor.dropMany(
                base, target.zone, col, row, listOf(p.item) + p.rest, target.columns, target.rows,
            )
            var guard = 0
            while (left.isNotEmpty() && guard++ < MAX_OVERFLOW_PAGES) {
                val index = ((target.zone as? ZoneId.Page)?.index ?: 0) + guard
                if (index >= placedLayout.pages.size) placedLayout = placedLayout.withNewPage()
                val spill = LayoutEditor.dropMany(
                    placedLayout, ZoneId.Page(index), 0, 0, left, target.columns, target.rows,
                )
                placedLayout = spill.first
                left = spill.second
            }
            edit { placedLayout }
            return true
        }
        val next = when (target) {
            is DropTarget.Grid -> {
                val (col, row) = target.cellFor(session.position, p.w, p.h)
                LayoutEditor.dropOnGrid(base, target.zone, col, row, p.item, p.w, p.h, target.columns, target.rows, dwell)
            }
            is DropTarget.Dock -> LayoutEditor.dropOnDock(base, target.slotAt(session.position), p.item, dockSlots, dwell)
        } ?: return false
        // ホームに置くショートカットは、アプリ側から消えないように固定しておく
        if (item is ShortcutItem && p.source == null) {
            scope.launch { withContext(Dispatchers.IO) { shortcutRepository.pin(item) } }
        }
        edit { next }
        return true
    }

    /** 端末の検索(Galaxy の Finder、無ければ全体検索)を開く。どれも無ければ false で、呼び出し側が自前の一覧に落とす。 */
    fun openSearch(): Boolean {
        val intent = GlobalSearch.resolve(context) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun remove(ref: ItemRef) = edit { LayoutEditor.remove(it, ref) }

    fun renameFolder(ref: ItemRef, name: String) = edit { LayoutEditor.rename(it, ref, name) }


    /** pageRows はアプリのページの段数。ウィジェット面には上限がない。 */
    /**
     * つまみで大きさを変える(#47)。受け付けたら true。
     * 置けない大きさ(はみ出す、他と重なる、種別の上下限を超える)なら false を返し、
     * 呼び出し側はその段を送らなかったことにする。
     */
    fun resizeBy(ref: ItemRef, dcol: Int, drow: Int, dw: Int, dh: Int, columns: Int, pageRows: Int): Boolean {
        val layout = layout.value
        val p = LayoutEditor.placementOf(layout, ref) ?: return false
        val w = p.w + dw
        val h = p.h + dh
        val spec = (LayoutEditor.itemAt(layout, ref) as? NativeWidgetItem)?.let { WidgetRegistry.get(it.widget)?.spec }
        if (spec != null && (w !in spec.minW..spec.maxW || h !in spec.minH..spec.maxH)) return false
        val rows = ((ref as? ItemRef.Grid)?.zone as? ZoneId.Page)?.let { pageRows }
        val next = LayoutEditor.resize(layout, ref, p.col + dcol, p.row + drow, w, h, columns, rows) ?: return false
        edit { next }
        return true
    }

    // ---- ページ(#21) ----

    /** 末尾に空のページを足す。ドラッグで右端に留めたときに呼ぶ。 */
    fun addPage() = edit { it.withNewPage() }

    /** 空のページを消す。ドラッグが終わるたびに呼ぶ。 */
    fun pruneEmptyPages() = edit { layout ->
        val next = layout.withoutEmptyPages()
        if (next == layout) null else next
    }

    // ---- AppWidget のバインド ----

    private fun beginAppWidgetPlacement(item: AppWidgetItem, zone: ZoneId, col: Int, row: Int, w: Int, h: Int, columns: Int, rows: Int?) {
        val provider = ComponentName.unflattenFromString(item.provider) ?: return
        val info = appWidgets.providerInfo(provider) ?: return
        val id = appWidgets.allocateId()
        pendingAppWidget = PendingAppWidget(id, info, zone, col, row, w, h, columns, rows)
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
        val next = LayoutEditor.dropOnGrid(layout.value, pending.zone, pending.col, pending.row, item, pending.w, pending.h, pending.columns, pending.rows)
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
        layout.allZones()
            .flatMap { it.items }
            .map { it.item }
            .filterIsInstance<AppWidgetItem>()
            .map { it.appWidgetId }
            .toSet()

    private suspend fun refreshApps() {
        val style = theme.value.iconStyle
        _apps.value = withContext(Dispatchers.IO) { appRepository.loadApps(style) }.associateBy { it.key }
    }

    private suspend fun refreshUsage() {
        _usagePermitted.value = usageRepository.hasPermission()
        _usage.value = withContext(Dispatchers.IO) { usageRepository.query(days = 7) }
    }
}

private const val TAG = "LauncherController"

/** 束があふれたときに作り足すページの上限。無限に増やさないための歯止め。 */
private const val MAX_OVERFLOW_PAGES = 8
