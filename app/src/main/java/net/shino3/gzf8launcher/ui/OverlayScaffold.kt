package net.shino3.gzf8launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** 重ね描きの出入りにかける時間。閉じるほうを短くすると、戻りが引っかからない。 */
object OverlayAnim {
    const val ENTER_MILLIS = 220
    const val EXIT_MILLIS = 160
}

/**
 * 画面全体を覆う重ね描きの土台。
 * 触った要素の矩形(source)を渡すと、その中心を原点にして広がる(#23)。
 * 外側のタップで閉じる。hidden のあいだは見えないが合成には残る(ドラッグ継続のため)。
 */
@Composable
fun OverlayScaffold(
    visible: Boolean,
    source: Rect?,
    hidden: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val scrim = if (theme.light) Color(0x99FFFFFF) else Color(0x99000000)
    // animateFloatAsState は初回に目標値から始まるので、0 から動かせる Animatable を使う
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        anim.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) OverlayAnim.ENTER_MILLIS else OverlayAnim.EXIT_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    val progress = anim.value
    // graphicsLayer より外側で測るので、拡大の途中でも矩形が動かない
    var contentBounds by remember { mutableStateOf(Rect.Zero) }
    val origin = remember(source, contentBounds) {
        if (source == null || source.isEmpty || contentBounds.isEmpty) {
            TransformOrigin.Center
        } else {
            TransformOrigin(
                ((source.center.x - contentBounds.left) / contentBounds.width).coerceIn(0f, 1f),
                ((source.center.y - contentBounds.top) / contentBounds.height).coerceIn(0f, 1f),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (hidden) 0f else 1f)
            .background(scrim.copy(alpha = scrim.alpha * progress))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { contentBounds = it.boundsInRoot() }
                .graphicsLayer {
                    transformOrigin = origin
                    val scale = 0.86f + 0.14f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            content()
        }
    }
}
