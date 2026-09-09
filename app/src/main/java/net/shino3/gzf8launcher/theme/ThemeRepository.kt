package net.shino3.gzf8launcher.theme

import android.content.Context
import android.util.Log
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
 */
class ThemeRepository(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _theme = MutableStateFlow(LauncherTheme())
    val theme: StateFlow<LauncherTheme> = _theme

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
        val spec = withContext(Dispatchers.IO) {
            val saved = if (file.exists()) parse(file.readText(), file.path) else null
            saved ?: readAsset("$ASSET_DIR/$DEFAULT_ASSET")
        }
        if (spec != null) _theme.value = spec.toTheme()
    }

    suspend fun apply(spec: ThemeSpec) {
        _theme.value = spec.toTheme()
        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(spec)) }
    }

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

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
