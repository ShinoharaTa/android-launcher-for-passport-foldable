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
import net.shino3.gzf8launcher.theme.IconTint
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
    fun render(drawable: Drawable, sizePx: Int, shape: IconShape, tint: IconTint = IconTint.NONE, accent: Int = 0): Bitmap {
        val shaped = shapeOnly(drawable, sizePx, shape)
        return if (tint == IconTint.NONE) shaped else recolor(shaped, tint, accent)
    }

    private fun shapeOnly(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
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

    /**
     * 色を落とす(#40)。
     * MONO は彩度を 0 に、QUANTIZE は階調を刻んで減色、TINT は accent の一色に染めて形だけ残す。
     * どれも透明度はそのまま残すので、アイコンの輪郭は崩れない。
     */
    private fun recolor(source: Bitmap, tint: IconTint, accent: Int): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val ar = (accent shr 16) and 0xFF
        val ag = (accent shr 8) and 0xFF
        val ab = accent and 0xFF
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // 人の目の感じ方に合わせた明るさ
            val luma = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
            px[i] = when (tint) {
                IconTint.NONE -> c
                IconTint.MONO -> (a shl 24) or (luma shl 16) or (luma shl 8) or luma
                IconTint.QUANTIZE -> {
                    val step = 255 / (QUANTIZE_LEVELS - 1)
                    val q = ((luma + step / 2) / step * step).coerceIn(0, 255)
                    (a shl 24) or (q shl 16) or (q shl 8) or q
                }
                // 明るさで accent の濃さを決める。暗い画素ほど濃く出る
                IconTint.TINT -> {
                    val k = luma / 255f
                    val mix = { base: Int -> (base * (TINT_FLOOR + (1f - TINT_FLOOR) * k)).toInt().coerceIn(0, 255) }
                    (a shl 24) or (mix(ar) shl 16) or (mix(ag) shl 8) or mix(ab)
                }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
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

    /** 減色したときの階調の数。4 でゲーム機の画面に近い。 */
    private const val QUANTIZE_LEVELS = 4

    /** 一色に染めたときの、いちばん暗い画素の濃さ。0 にすると黒く潰れる。 */
    private const val TINT_FLOOR = 0.35f
}
