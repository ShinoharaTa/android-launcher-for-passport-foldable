package net.shino3.gzf8launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.shino3.gzf8launcher.model.AppWidgetItem
import net.shino3.gzf8launcher.model.NativeWidgetItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.DragPayload
import net.shino3.gzf8launcher.ui.drag.dragSource
import net.shino3.gzf8launcher.widget.NativeWidget
import net.shino3.gzf8launcher.widget.NativeWidgetPreview
import kotlin.math.ceil

/**
 * ウィジェットの一覧。ホームの空き領域の長押しメニューから開く(#25)。
 * 端末のウィジェット選択と同じく、置く前に姿が見える(#32)。
 * 自作ウィジェットは実物を既定サイズの比で描き、AppWidget は provider のプレビュー画像(無ければアイコン)を出す。
 * 長押しドラッグでホームに置く。
 */
@Composable
fun WidgetPicker(
    visible: Boolean,
    source: Rect?,
    hidden: Boolean,
    widgets: List<NativeWidget<*>>,
    providers: List<AppWidgetProviderInfo>,
    /** ドロップ先グリッドのセル幅 px。AppWidget の最小サイズをセル数に直すのに使う。 */
    cellPx: Float,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val pm = context.packageManager
    val sorted = remember(providers) { providers.sortedBy { it.loadLabel(pm).lowercase() } }
    val shape = theme.shapeOf(theme.moduleRadius + 6.dp)

    OverlayScaffold(visible = visible, source = source, hidden = hidden, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.78f)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(12.dp),
        ) {
            Text(
                text = "WIDGETS // HOLD AND DRAG TO HOME",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(widgets, key = { it.spec.id }) { widget ->
                    val spec = widget.spec
                    PickerCard(
                        title = spec.name,
                        subtitle = "${spec.defaultW} x ${spec.defaultH}",
                        modifier = Modifier.dragSource(
                            DragPayload(NativeWidgetItem(spec.id), null, null, spec.name, spec.defaultW, spec.defaultH),
                            enabled = !hidden,
                        ),
                    ) {
                        // 実物を既定サイズの比で描く。幅はカードに合わせ、横長でも中身が見える高さは確保する
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val height = (maxWidth * spec.defaultH / spec.defaultW).coerceIn(PREVIEW_MIN_HEIGHT, PREVIEW_MAX_HEIGHT)
                            NativeWidgetPreview(widget = widget, modifier = Modifier.fillMaxWidth().height(height))
                        }
                    }
                }
                items(sorted, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                    val label = remember(info) { info.loadLabel(pm) }
                    val appLabel = remember(info) {
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                            .getOrDefault(info.provider.packageName)
                    }
                    val w = spanFor(info.minWidth, cellPx).coerceIn(1, 6)
                    val h = spanFor(info.minHeight, cellPx).coerceIn(1, 6)
                    PickerCard(
                        title = label,
                        subtitle = "$appLabel  //  $w x $h",
                        modifier = Modifier.dragSource(
                            DragPayload(AppWidgetItem(info.provider.flattenToString()), null, null, label, w, h),
                            enabled = !hidden,
                        ),
                    ) {
                        AppWidgetPreview(info)
                    }
                }
            }
        }
    }
}

/** 1 枚のカード。上にプレビュー、下に名前と大きさ。 */
@Composable
private fun PickerCard(title: String, subtitle: String, modifier: Modifier, preview: @Composable () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius)
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clip(shape)
            .border(1.dp, theme.outline, shape)
            .then(modifier)
            .padding(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { preview() }
        // 名前は日本語のアプリ名が入るので UI 書体
        Text(
            text = title,
            color = theme.colors.text,
            fontFamily = theme.uiFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(subtitle, color = theme.colors.textDim, fontFamily = theme.uiFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * AppWidget のプレビュー。provider が持つ previewImage を出し、無ければアプリのアイコンで代える。
 * 画像の読み込みは重いことがあるので IO で行う。
 */
@Composable
private fun AppWidgetPreview(info: AppWidgetProviderInfo) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, info) {
        value = withContext(Dispatchers.IO) {
            val drawable: Drawable? = runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
                ?: runCatching { info.loadIcon(context, 0) }.getOrNull()
            drawable?.let { d ->
                // 大きすぎる画像は縮めて持つ
                val scale = if (d.intrinsicWidth > PREVIEW_MAX_PX) PREVIEW_MAX_PX.toFloat() / d.intrinsicWidth else 1f
                val w = (d.intrinsicWidth * scale).toInt().coerceAtLeast(1)
                val h = (d.intrinsicHeight * scale).toInt().coerceAtLeast(1)
                runCatching { d.toBitmap(w, h).asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(theme.moduleRadius))
            .background(theme.colors.module)
            .padding(8.dp),
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PREVIEW_MAX_PX = 800
private val PREVIEW_MIN_HEIGHT = 84.dp
private val PREVIEW_MAX_HEIGHT = 200.dp

/** px の最小サイズをセル数に切り上げる。 */
private fun spanFor(minPx: Int, cellPx: Float): Int =
    if (cellPx <= 0f) 1 else ceil(minPx / cellPx).toInt().coerceAtLeast(1)
