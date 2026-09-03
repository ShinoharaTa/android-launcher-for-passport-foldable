package net.shino3.gzf8launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
            runCatching { parse(text) }
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

    /** version 1(面が widgets / shelf に分かれていない)は cover / extension をそのままウィジェット面に移す。 */
    private fun parse(text: String): Layout {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
        val element = if (version >= Layout.CURRENT_VERSION) root else migrateV1(root)
        return json.decodeFromJsonElement(Layout.serializer(), element)
    }

    private fun migrateV1(root: JsonObject): JsonObject = buildJsonObject {
        put("version", Layout.CURRENT_VERSION)
        put("cover", buildJsonObject { root["cover"]?.let { put("widgets", it) } })
        put("extension", buildJsonObject { root["extension"]?.let { put("widgets", it) } })
        root["dock"]?.let { put("dock", it) }
    }

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
