package net.shino3.gzf8launcher.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** ウィジェット種別の仕様(docs/04「自作ウィジェットは仕様、データ源、描画の三つに分ける」)。 */
data class WidgetSpec(
    val id: String,
    val name: String,
    val defaultW: Int,
    val defaultH: Int,
    val minW: Int = 1,
    val minH: Int = 1,
    val maxW: Int = 6,
    val maxH: Int = 6,
)

/** データを Flow で流す。取得手段はここに閉じる。 */
fun interface WidgetDataSource<T> {
    fun data(context: Context): Flow<T>
}

/** データを受け取って描く。同じ種別に複数登録でき、テーマがどれを使うかを選ぶ。 */
class WidgetRenderer<T>(val content: @Composable (state: T, config: JsonObject, modifier: Modifier) -> Unit)

class NativeWidget<T>(
    val spec: WidgetSpec,
    val source: WidgetDataSource<T>,
    val renderers: Map<String, WidgetRenderer<T>>,
) {
    fun renderer(variant: String?): WidgetRenderer<T> = renderers[variant] ?: renderers.getValue(DEFAULT_VARIANT)

    companion object {
        const val DEFAULT_VARIANT = "default"
    }
}

/** 種別の登録簿。レイアウトとテーマからは ID で参照する。 */
object WidgetRegistry {
    private val widgets = linkedMapOf<String, NativeWidget<*>>()

    fun register(widget: NativeWidget<*>) {
        widgets[widget.spec.id] = widget
    }

    fun get(id: String): NativeWidget<*>? = widgets[id]

    val all: Collection<NativeWidget<*>> get() = widgets.values
}
