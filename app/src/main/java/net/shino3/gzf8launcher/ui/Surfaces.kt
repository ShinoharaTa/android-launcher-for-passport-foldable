package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.shino3.gzf8launcher.theme.LauncherTheme
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.theme.ModuleShape
import net.shino3.gzf8launcher.theme.ShadowStyle

/**
 * ウィジェット、フォルダ、ドック、重ね描きが共有する「面」の描き方(#40)。
 *
 * 形、枠線の太さ、影、すりガラスをテーマから決める。
 * ここに集めておかないと、案を増やすたびに同じ分岐が画面のあちこちに散る。
 */

/** テーマの枠の形。corner はその面に使う角の大きさで、既定はテーマの moduleRadius。 */
@Composable
fun moduleShape(corner: Dp = LocalLauncherTheme.current.moduleRadius): Shape =
    LocalLauncherTheme.current.shapeOf(corner)

fun LauncherTheme.shapeOf(corner: Dp): Shape = when (moduleShape) {
    ModuleShape.ROUNDED -> RoundedCornerShape(corner)
    ModuleShape.CLIPPED -> CutCornerShape(corner)
    ModuleShape.SQUARE -> RectangleShape
    ModuleShape.PIXEL -> steppedShape(corner)
}

/**
 * 角を階段状に刻んだ形。角丸の代わりに、一辺 step の四角を二段だけ削る。
 * 曲線を使わないので、拡大しても画素の粗さが保たれる。
 */
private fun steppedShape(corner: Dp): Shape = GenericShape { size, _ ->
    val s = (corner.value.coerceAtLeast(2f) / 2f).coerceAtMost(size.minDimension / 4f)
    val w = size.width
    val h = size.height
    moveTo(0f, 2 * s)
    lineTo(s, 2 * s); lineTo(s, s); lineTo(2 * s, s); lineTo(2 * s, 0f)
    lineTo(w - 2 * s, 0f); lineTo(w - 2 * s, s); lineTo(w - s, s); lineTo(w - s, 2 * s)
    lineTo(w, 2 * s)
    lineTo(w, h - 2 * s); lineTo(w - s, h - 2 * s); lineTo(w - s, h - s); lineTo(w - 2 * s, h - s); lineTo(w - 2 * s, h)
    lineTo(2 * s, h); lineTo(2 * s, h - s); lineTo(s, h - s); lineTo(s, h - 2 * s); lineTo(0f, h - 2 * s)
    close()
}

/**
 * 面ひとつぶんの下地。影 → 切り抜き → 塗り → 枠線 の順に重ねる。
 *
 * fill と outline を渡さなければテーマの module と outline を使う。
 * すりガラスのテーマでは、塗りを半透明にして上端に光を足す。
 */
@Composable
fun Modifier.moduleSurface(
    corner: Dp = LocalLauncherTheme.current.moduleRadius,
    fill: Color = LocalLauncherTheme.current.colors.module,
    outline: Color = LocalLauncherTheme.current.outline,
): Modifier {
    val theme = LocalLauncherTheme.current
    val shape = theme.shapeOf(corner)
    return this
        .moduleShadow(shape, outline)
        .clip(shape)
        .background(fill)
        .then(if (theme.isGlass) Modifier.background(glassSheen()) else Modifier)
        .border(theme.outlineWidth, outline, shape)
}

/** 影だけ。塗りを自前で持つ面(ドックなど)から使う。 */
@Composable
fun Modifier.moduleShadow(shape: Shape, outline: Color = LocalLauncherTheme.current.outline): Modifier {
    val theme = LocalLauncherTheme.current
    return when (theme.shadow) {
        ShadowStyle.NONE -> this
        // 右下にずらした単色。版画のように、面が紙から浮いて見える
        ShadowStyle.DROP -> drawBehind {
            val d = DROP_OFFSET.toPx()
            val shadowOutline = shape.createOutline(Size(size.width, size.height), layoutDirection, this)
            translate(left = d, top = d) { drawOutline(shadowOutline, color = outline) }
        }
        // 枠の色のにじみ。発光する計器の画面
        ShadowStyle.GLOW -> shadow(
            elevation = GLOW_ELEVATION,
            shape = shape,
            ambientColor = outline,
            spotColor = outline,
        )
        // 上端の光と内側の影。削り出した金属の面
        ShadowStyle.INSET -> shadow(elevation = 2.dp, shape = shape)
    }
}

/** すりガラスの上面に乗せる光。上ほど明るく、下は透ける。 */
private fun glassSheen(): Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.16f),
    0.55f to Color.White.copy(alpha = 0.04f),
    1f to Color.Transparent,
)

/** DrawScope で図形を塗る小さな入口。ずらした影で使う。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutline(
    outline: androidx.compose.ui.graphics.Outline,
    color: Color,
) {
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Rectangle -> drawRect(color, Offset(outline.rect.left, outline.rect.top), outline.rect.size)
        is androidx.compose.ui.graphics.Outline.Rounded -> drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }, color)
        is androidx.compose.ui.graphics.Outline.Generic -> drawPath(outline.path, color)
    }
}

/** ずらす影の量。 */
private val DROP_OFFSET = 4.dp

/** にじみの強さ。 */
private val GLOW_ELEVATION = 10.dp
