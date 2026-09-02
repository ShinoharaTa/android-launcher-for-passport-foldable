package net.shino3.gzf8launcher.widget

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.ui.WidgetPlaceholder
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.LocalDragController
import net.shino3.gzf8launcher.ui.drag.dragSource

/** レイアウト上の AppWidgetItem を AppWidgetHostView で描く。セルの実寸をウィジェットに伝える。 */
@Composable
fun AppWidgetView(item: AppWidgetItem, payload: DragPayload, modifier: Modifier = Modifier) {
    val hostManager = LocalAppWidgetHost.current
    val drag = LocalDragController.current
    val context = LocalContext.current
    val info = remember(item.appWidgetId) { hostManager.manager.getAppWidgetInfo(item.appWidgetId) }
    if (info == null) {
        WidgetPlaceholder("APPWIDGET // missing ${item.provider.substringBefore('/')}", modifier.dragSource(payload))
        return
    }
    val currentPayload by rememberUpdatedState(payload)

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(4.dp)) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                hostManager.createView(ctx, item.appWidgetId, info).apply {
                    dragListener = object : LauncherAppWidgetHostView.DragListener {
                        private var last = Offset.Zero
                        override fun onLongPress(rawX: Float, rawY: Float) {
                            last = Offset(rawX, rawY)
                            drag.begin(currentPayload, last)
                        }

                        override fun onDrag(rawX: Float, rawY: Float) {
                            val now = Offset(rawX, rawY)
                            drag.move(now - last)
                            last = now
                        }

                        override fun onRelease() = drag.end()
                        override fun onCancel() = drag.cancel()
                    }
                }
            },
            update = { view ->
                view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            },
        )
    }
}
