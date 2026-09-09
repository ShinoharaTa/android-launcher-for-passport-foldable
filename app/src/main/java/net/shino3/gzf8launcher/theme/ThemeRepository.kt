package net.shino3.gzf8launcher.theme

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 見た目の唯一の保持者。配置(LayoutRepository)とは別ファイルにしてあるので、
 * テーマを切り替えても配置は巻き戻らない(docs/04)。
 *
 * 内部ストレージの theme.json が現在のテーマ。無ければ同梱プリセットの先頭。
 * 設定画面からの上書き(書体、アイコンの形)は SharedPreferences に持ち、テーマの上に重ねる(#32)。
 */
class ThemeRepository(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var spec: ThemeSpec? = null
    private val _theme = MutableStateFlow(LauncherTheme())
    val theme: StateFlow<LauncherTheme> = _theme
    private val _overrides = MutableStateFlow(readOverrides())
    val overrides: StateFlow<ThemeOverrides> = _overrides

    /** 同梱テーマ。設定画面の一覧に出す。 */
    suspend fun bundled(): List<ThemeSpec> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.list(ASSET_DIR).orEmpty()
                .filter { it.endsWith(".json") }
                .sorted()
                .mapNotNull { name -> readAsset("$ASSET_DIR/$name") }
        }.getOrDefault(emptyList())
    }

    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            val saved = if (file.exists()) parse(file.readText(), file.path) else null
            saved ?: readAsset("$ASSET_DIR/$DEFAULT_ASSET")
        }
        if (loaded != null) {
            spec = loaded
            publish()
        }
    }

    suspend fun apply(spec: ThemeSpec) {
        this.spec = spec
        publish()
        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(spec)) }
    }

    fun setOverrides(overrides: ThemeOverrides) {
        _overrides.value = overrides
        prefs.edit {
            putString(KEY_FONT, overrides.font.name)
            putString(KEY_ICON_SHAPE, overrides.iconShape?.name ?: "")
            putInt(KEY_SIDE_PADDING, overrides.sidePadding ?: UNSET)
            putInt(KEY_DOCK_HEIGHT, overrides.dockHeight ?: UNSET)
            putInt(KEY_DOCK_PADDING, overrides.dockPadding ?: UNSET)
            putInt(KEY_DOCK_INSET, overrides.dockInset ?: UNSET)
            putInt(KEY_INSET_TOP, overrides.insetTop ?: UNSET)
            putInt(KEY_INSET_BOTTOM, overrides.insetBottom ?: UNSET)
        }
        publish()
    }

    private fun publish() {
        val current = spec ?: return
        _theme.value = current.toTheme(_overrides.value)
    }

    private fun readOverrides(): ThemeOverrides = ThemeOverrides(
        font = prefs.getString(KEY_FONT, null)?.let { name -> FontChoice.entries.firstOrNull { it.name == name } }
            ?: FontChoice.THEME,
        iconShape = prefs.getString(KEY_ICON_SHAPE, null)?.let { name -> IconShape.entries.firstOrNull { it.name == name } },
        sidePadding = readDp(KEY_SIDE_PADDING),
        dockHeight = readDp(KEY_DOCK_HEIGHT),
        dockPadding = readDp(KEY_DOCK_PADDING),
        dockInset = readDp(KEY_DOCK_INSET),
        insetTop = readDp(KEY_INSET_TOP),
        insetBottom = readDp(KEY_INSET_BOTTOM),
    )

    private fun readDp(key: String): Int? = prefs.getInt(key, UNSET).takeIf { it != UNSET }

    private fun readAsset(path: String): ThemeSpec? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            .getOrNull()
            ?.let { parse(it, path) }

    private fun parse(text: String, source: String): ThemeSpec? =
        runCatching { json.decodeFromString<ThemeSpec>(text) }
            .onFailure { Log.e(TAG, "テーマの読み込みに失敗した: $source", it) }
            .getOrNull()

    companion object {
        private const val TAG = "ThemeRepository"
        private const val FILE_NAME = "theme.json"
        private const val ASSET_DIR = "themes"
        private const val DEFAULT_ASSET = "amber-terminal.json"
        private const val PREFS_NAME = "settings"
        private const val KEY_FONT = "font"
        private const val KEY_ICON_SHAPE = "iconShape"
        private const val KEY_SIDE_PADDING = "sidePadding"
        private const val KEY_DOCK_HEIGHT = "dockHeight"
        private const val KEY_DOCK_PADDING = "dockPadding"
        private const val KEY_DOCK_INSET = "dockInset"
        private const val KEY_INSET_TOP = "insetTop"
        private const val KEY_INSET_BOTTOM = "insetBottom"
        /** 寸法の上書きが無いことを表す値。dp は負にならない。 */
        private const val UNSET = -1

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
