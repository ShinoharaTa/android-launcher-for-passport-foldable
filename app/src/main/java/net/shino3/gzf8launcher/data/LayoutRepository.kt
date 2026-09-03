package net.shino3.gzf8launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
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

    /**
     * 古い版を現在の形に移す。
     * version 1: cover / extension が 1 枚ずつ → cover をウィジェット面、extension をその下に足す。
     * version 2: 面ごとに widgets / shelf → widgets 同士、shelf 同士を上下に連ねる(cover が上)。
     */
    private fun parse(text: String): Layout {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
        val element = when {
            version >= Layout.CURRENT_VERSION -> root
            version == 2 -> migrateV2(root)
            else -> migrateV1(root)
        }
        return json.decodeFromJsonElement(Layout.serializer(), element)
    }

    private fun migrateV1(root: JsonObject): JsonObject = buildJsonObject {
        put("version", Layout.CURRENT_VERSION)
        put("widgets", stack(root["cover"]?.jsonObject, root["extension"]?.jsonObject))
        put("apps", buildJsonObject { put("items", JsonArray(emptyList())) })
        root["dock"]?.let { put("dock", it) }
    }

    private fun migrateV2(root: JsonObject): JsonObject = buildJsonObject {
        val cover = root["cover"]?.jsonObject
        val extension = root["extension"]?.jsonObject
        put("version", Layout.CURRENT_VERSION)
        put("widgets", stack(cover?.get("widgets")?.jsonObject, extension?.get("widgets")?.jsonObject))
        put("apps", stack(cover?.get("shelf")?.jsonObject, extension?.get("shelf")?.jsonObject))
        root["dock"]?.let { put("dock", it) }
    }

    /** 二つのゾーンを縦に連ねる。下に足す側の row を、上側が占める段数ぶんずらす。 */
    private fun stack(upper: JsonObject?, lower: JsonObject?): JsonObject {
        val upperItems = upper?.get("items")?.jsonArray?.map { it.jsonObject }.orEmpty()
        val offset = upperItems.maxOfOrNull { (it["row"]?.jsonPrimitive?.intOrNull ?: 0) + (it["h"]?.jsonPrimitive?.intOrNull ?: 1) } ?: 0
        val lowerItems = lower?.get("items")?.jsonArray?.map { element ->
            val obj = element.jsonObject
            val row = obj["row"]?.jsonPrimitive?.intOrNull ?: 0
            JsonObject(obj + ("row" to JsonPrimitive(row + offset)))
        }.orEmpty()
        return buildJsonObject { put("items", JsonArray(upperItems + lowerItems)) }
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
