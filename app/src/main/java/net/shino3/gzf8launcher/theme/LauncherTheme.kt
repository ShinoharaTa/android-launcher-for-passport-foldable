package net.shino3.gzf8launcher.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 実行時のテーマ。ThemeSpec を Compose の値に直したもの。
 * 画面側はこれだけを見る。
 */
data class LauncherTheme(
    val id: String = "amber-terminal",
    val name: String = "AMBER TERMINAL",
    val columns: Int = 6,
    /** アプリのページ 1 枚の段数。 */
    val rows: Int = 7,
    val dockSlots: Int = 6,
    val folderColumns: Int = 3,
    val showLabels: Boolean = false,
    val iconScale: Float = 0.62f,
    val iconShape: IconShape = IconShape.SYSTEM,
    val widgetHeaders: Boolean = true,
    val widgetVariants: Map<String, String> = emptyMap(),
    val moduleRadius: Dp = 12.dp,
    val decor: DecorSpec = DecorSpec(scanlines = true),
    val light: Boolean = false,
    /** 壁紙を透かすか。false なら壁紙を描かせない。 */
    val showWallpaper: Boolean = true,
    val gradient: Brush? = null,
    val uiFont: FontFamily = FontFamily.SansSerif,
    val monoFont: FontFamily = FontFamily.Monospace,
    val colors: LauncherColors = LauncherColors(),
) {
    /** 枠線の色。装飾で枠線を消しているテーマでは透明を返す。 */
    val outline: Color get() = if (decor.outlines) colors.line else Color.Transparent
}

data class LauncherColors(
    val panel: Color = Color(0xCC0B0F14),
    val surface: Color = Color(0xFF05070A),
    val module: Color = Color(0x8C0B1118),
    val dock: Color = Color(0x99182230),
    val line: Color = Color(0x33E6E1D6),
    val accent: Color = Color(0xFFFFB000),
    val text: Color = Color(0xFFE6E1D6),
    val textDim: Color = Color(0xFF8A8F98),
)

val LocalLauncherTheme = staticCompositionLocalOf { LauncherTheme() }

/** "#AARRGGBB" / "#RRGGBB" を Color にする。読めない値は magenta にして間違いを目で分かるようにする。 */
fun parseColor(value: String): Color {
    val hex = value.removePrefix("#")
    val argb = when (hex.length) {
        6 -> 0xFF000000L or hex.toLong(16)
        8 -> hex.toLong(16)
        else -> return Color.Magenta
    }
    return runCatching { Color(argb.toInt()) }.getOrDefault(Color.Magenta)
}

private fun fontFamily(name: String): FontFamily = when (name) {
    "mono" -> FontFamily.Monospace
    "serif" -> FontFamily.Serif
    "sans" -> FontFamily.SansSerif
    // "default" / "system" / 未知の値はシステム既定
    else -> FontFamily.Default
}

/** 書体の上書き。THEME はテーマの指定に従い、SYSTEM は全部をシステム既定にする。 */
enum class FontChoice(val label: String) { THEME("THEME"), SYSTEM("SYSTEM") }

/**
 * 設定画面からの上書き(#32)。theme.json とは別に持ち、テーマを切り替えても残る。
 * iconShape が null ならテーマの指定に従う。
 */
data class ThemeOverrides(
    val font: FontChoice = FontChoice.THEME,
    val iconShape: IconShape? = null,
)

fun ThemeSpec.toTheme(overrides: ThemeOverrides = ThemeOverrides()): LauncherTheme = LauncherTheme(
    id = id,
    name = name,
    columns = grid.columns,
    rows = grid.rows,
    dockSlots = grid.dockSlots,
    folderColumns = grid.folderColumns,
    showLabels = icon.labels,
    iconScale = icon.scale,
    iconShape = overrides.iconShape ?: icon.shape,
    widgetHeaders = widgets.headers,
    widgetVariants = widgets.variants,
    moduleRadius = widgets.radius.dp,
    decor = decor,
    light = surface.light,
    showWallpaper = surface.showWallpaper,
    gradient = surface.gradient?.let {
        val colors = listOf(parseColor(it.from), parseColor(it.to))
        if (it.vertical) Brush.verticalGradient(colors) else Brush.horizontalGradient(colors)
    },
    // ui に等幅は使わない。日本語のアプリ名やフォルダ名が崩れる(#32)。
    // 以前の theme.json には "mono" が保存されているので、ここで既定に倒す
    uiFont = if (overrides.font == FontChoice.SYSTEM || typography.ui == "mono") FontFamily.Default else fontFamily(typography.ui),
    monoFont = if (overrides.font == FontChoice.SYSTEM) FontFamily.Default else fontFamily(typography.mono),
    colors = LauncherColors(
        panel = parseColor(palette.panel),
        surface = parseColor(palette.surface),
        module = parseColor(palette.module),
        dock = parseColor(palette.dock),
        line = parseColor(palette.line),
        accent = parseColor(palette.accent),
        text = parseColor(palette.text),
        textDim = parseColor(palette.textDim),
    ),
)
