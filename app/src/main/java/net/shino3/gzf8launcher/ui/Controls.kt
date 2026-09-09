package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.IconRenderer
import net.shino3.gzf8launcher.theme.IconShape
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * 押す場所の共通部品(#32)。
 * 指で当てる場所は最低 44dp 四方にする。文字が小さくても、当たる範囲は小さくしない。
 */

/** 押せる最小の大きさ。 */
val TAP_MIN: Dp = 44.dp

/**
 * タップを onClick に渡す。pointerInput の鍵は固定なので、再コンポーズで onClick が変わっても
 * 最初のラムダを掴んだままになる。最新のものを読むように rememberUpdatedState を挟む。
 * これが無いと、値を読んで次の値を決める押す場所(Stepper の −/+)が 1 回しか効かない(#34 で踏んだ)。
 */
@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier {
    val current = rememberUpdatedState(onClick)
    return pointerInput(Unit) { detectTapGestures { current.value() } }
}

/** 絞り込みや設定の選択肢に使うチップ。選ばれているとアクセント色で塗る。 */
@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 40.dp)
            .clip(shape)
            .background(if (selected) theme.colors.accent else Color.Transparent)
            .border(1.dp, if (selected) theme.colors.accent else theme.colors.line, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) theme.colors.surface else theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** "CLEAR" や "CLOSE" のような文字だけの押す場所。見た目は文字だけでも、当たる範囲は 44dp 取る。 */
@Composable
fun TextAction(label: String, modifier: Modifier = Modifier, accent: Boolean = true, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = TAP_MIN, minHeight = TAP_MIN)
            .tap(onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (accent) theme.colors.accent else theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * 寸法を −/+ で変える 1 行(#34)。値は dp の整数。
 * overridden が真なら設定で上書きされている値で、RESET でテーマの値に戻せる。
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    overridden: Boolean,
    onChange: (Int?) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TAP_MIN),
    ) {
        Text(
            text = label,
            color = theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (overridden) TextAction("RESET", accent = false) { onChange(null) }
        TextAction("−", accent = value > range.first) { if (value > range.first) onChange((value - step).coerceAtLeast(range.first)) }
        Text(
            text = "${value}dp",
            color = if (overridden) theme.colors.accent else theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 12.sp,
            modifier = Modifier.defaultMinSize(minWidth = 52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TextAction("+", accent = value < range.last) { if (value < range.last) onChange((value + step).coerceAtMost(range.last)) }
    }
}

/** メニューの 1 行。行全体が押せる。 */
@Composable
fun MenuRow(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (accent) theme.colors.accent else theme.colors.text,
            fontFamily = theme.monoFont,
            fontSize = 13.sp,
        )
    }
}

/**
 * アイコンの形を Compose の Shape にしたもの。
 * アイコンそのものは Bitmap にする段階で切ってあるので、これは未インストールの印やフォルダの器に使う。
 */
fun IconShape.asShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.ROUNDED, IconShape.SYSTEM -> RoundedCornerShape((IconRenderer.ROUNDED_RADIUS * 100).toInt())
    IconShape.SQUARE -> RoundedCornerShape((IconRenderer.SQUARE_RADIUS * 100).toInt())
    IconShape.SQUIRCLE -> SquircleShape
}

/** 超楕円。IconRenderer と同じ輪郭を Compose 側でも描く。 */
private object SquircleShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val side = minOf(size.width, size.height)
        val path: Path = IconRenderer.maskPath(IconShape.SQUIRCLE, side).asComposePath()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
