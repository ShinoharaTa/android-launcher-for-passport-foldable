package net.shino3.gzf8launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.shino3.gzf8launcher.model.Layout
import java.io.File

/**
 * 配置の唯一の保持者(docs/04)。
 * 内部ストレージの layout.json を読み書きし、無ければ同梱プリセットから始める。
 */
class LayoutRepository(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _layout = MutableStateFlow(Layout())
    val layout: StateFlow<Layout> = _layout

    suspend fun load() {
        val parsed = withContext(Dispatchers.IO) {
            val text = if (file.exists()) file.readText() else readPreset()
            runCatching { json.decodeFromString<Layout>(text) }
                .onFailure { Log.e(TAG, "layout.json の読み込みに失敗。空のレイアウトで続行する", it) }
                .getOrDefault(Layout())
        }
        _layout.value = parsed
    }

    suspend fun update(transform: (Layout) -> Layout) {
        val next = transform(_layout.value)
        _layout.value = next
        withContext(Dispatchers.IO) { file.writeText(json.encodeToString(next)) }
    }

    fun export(): String = json.encodeToString(_layout.value)

    private fun readPreset(): String =
        context.assets.open(PRESET_PATH).bufferedReader().use { it.readText() }

    companion object {
        private const val TAG = "LayoutRepository"
        private const val FILE_NAME = "layout.json"
        private const val PRESET_PATH = "layouts/default.json"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            classDiscriminator = "type"
            encodeDefaults = false
        }
    }
}
