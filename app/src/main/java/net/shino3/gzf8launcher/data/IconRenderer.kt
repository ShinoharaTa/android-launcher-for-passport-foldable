package net.shino3.gzf8launcher.data

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import net.shino3.gzf8launcher.theme.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * アイコンをテーマの形で描く(#32)。
 *
 * LauncherApps から届く AdaptiveIcon は、そのまま Bitmap にすると端末のマスク(Galaxy なら角丸四角)で切られる。
 * 形を選べるようにするため、背景と前景の層を自前で描いてから、自前の形で切る。
 * 層は表示領域の 1.5 倍の大きさで描く決まり(AdaptiveIconDrawable の EXTRA_INSET_PERCENTAGE = 1/4)。
 * AdaptiveIcon でない古いアイコンは形を持たないので、そのまま出す。
 */
object IconRenderer {
    fun render(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
        if (shape == IconShape.SYSTEM || drawable !is AdaptiveIconDrawable) {
            return drawable.toBitmap(sizePx, sizePx)
        }
        val layers = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layers)
        val inset = -(sizePx * LAYER_OVERHANG).roundToInt()
        listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
            layer.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            layer.draw(canvas)
        }
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(layers, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawPath(maskPath(shape, sizePx.toFloat()), paint)
        return out
    }

    /** 形の輪郭。size 四方に収まる。 */
    fun maskPath(shape: IconShape, size: Float): Path {
        val path = Path()
        val half = size / 2f
        when (shape) {
            IconShape.CIRCLE -> path.addCircle(half, half, half, Path.Direction.CW)
            IconShape.ROUNDED -> path.addRoundRect(0f, 0f, size, size, size * ROUNDED_RADIUS, size * ROUNDED_RADIUS, Path.Direction.CW)
            IconShape.SQUARE -> path.addRoundRect(0f, 0f, size, size, size * SQUARE_RADIUS, size * SQUARE_RADIUS, Path.Direction.CW)
            IconShape.SQUIRCLE -> superellipse(path, half, half, half, SQUIRCLE_EXPONENT)
            IconShape.SYSTEM -> path.addRoundRect(0f, 0f, size, size, size * ROUNDED_RADIUS, size * ROUNDED_RADIUS, Path.Direction.CW)
        }
        return path
    }

    /** 超楕円 |x|^n + |y|^n = r^n。n が大きいほど四角に近づく。 */
    private fun superellipse(path: Path, cx: Float, cy: Float, r: Float, n: Float) {
        val steps = 96
        for (i in 0 until steps) {
            val t = (i.toDouble() / steps) * 2 * Math.PI
            val c = cos(t)
            val s = sin(t)
            val x = cx + r * sign(c).toFloat() * abs(c).pow(2.0 / n).toFloat()
            val y = cy + r * sign(s).toFloat() * abs(s).pow(2.0 / n).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** 層を表示領域の外へはみ出させる割合(片側)。1/4 ずつで 1.5 倍になる。 */
    private const val LAYER_OVERHANG = 0.25f

    /** 角丸四角の半径(一辺に対する割合)。 */
    const val ROUNDED_RADIUS = 0.24f

    /** 正方形の角。完全な直角だと硬すぎるので少しだけ丸める。 */
    const val SQUARE_RADIUS = 0.06f

    /** スクワークルの指数。5 で iOS や One UI に近い。 */
    const val SQUIRCLE_EXPONENT = 5f
}
