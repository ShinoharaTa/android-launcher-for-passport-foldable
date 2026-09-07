package net.shino3.gzf8launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 動かしているあいだだけ右端に出る細いスクロールバー(#27)。
 * 縦スクロールする面がどこまで続くかを伝える。止まると消える。
 * verticalScroll より外側に付けること。内側だと中身と一緒に流れてしまう。
 */
@Composable
fun Modifier.fadingScrollbar(state: ScrollState, color: Color): Modifier {
    val scrolling = state.isScrollInProgress && state.maxValue > 0
    val alpha by animateFloatAsState(
        targetValue = if (scrolling) 1f else 0f,
        animationSpec = tween(if (scrolling) 80 else 700),
        label = "scrollbar",
    )
    return drawWithContent {
        drawContent()
        if (alpha <= 0f || state.maxValue <= 0) return@drawWithContent
        val viewport = size.height
        val content = viewport + state.maxValue
        val thumb = maxOf(24.dp.toPx(), viewport * viewport / content)
        val y = (viewport - thumb) * (state.value.toFloat() / state.maxValue)
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.7f * alpha),
            topLeft = Offset(size.width - 6.dp.toPx(), y),
            size = Size(3.dp.toPx(), thumb),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}
