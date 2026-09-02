package net.shino3.gzf8launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonObject
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** レイアウト上の NativeWidgetItem を、登録簿の種別で描く。 */
@Composable
fun NativeWidgetHost(item: NativeWidgetItem, modifier: Modifier = Modifier) {
    val widget = WidgetRegistry.get(item.widget)
    WidgetFrame(caption = widget?.spec?.name ?: "UNKNOWN // ${item.widget}", modifier = modifier) {
        if (widget != null) WidgetContent(widget, item.config)
    }
}

@Composable
private fun <T> WidgetContent(widget: NativeWidget<T>, config: JsonObject) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val flow = remember(widget, context) { widget.source.data(context) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val renderer = widget.renderer(theme.widgetVariants[widget.spec.id])
    val current = state ?: return
    renderer.content(current, config, Modifier.fillMaxSize())
}

/** ウィジェット共通の枠。見出しの有無はテーマで決める。 */
@Composable
fun WidgetFrame(caption: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(theme.colors.widget)
            .border(1.dp, theme.colors.line, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (theme.widgetHeaders) {
            Text(caption, color = theme.colors.textDim, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
