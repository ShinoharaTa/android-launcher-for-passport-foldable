package net.shino3.gzf8launcher.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    /** グリッドとドックの左右の余白。縦長メインではこれに広げる分を足す(#34)。 */
    val sidePadding: Dp = 8.dp,
    val dockHeight: Dp = 76.dp,
    /** ドックのレールの縁からアイコンまでの余白。 */
    val dockPadding: Dp = 0.dp,
    /** グリッドの外縁からレールを引っ込める量。中央寄せの度合い。 */
    val dockInset: Dp = 20.dp,
    /** システムバーの内側に足す上下の余白。 */
    val insetTop: Dp = 0.dp,
    val insetBottom: Dp = 0.dp,
    val showLabels: Boolean = false,
    val iconScale: Float = 0.62f,
    val iconShape: IconShape = IconShape.SYSTEM,
    val iconTint: IconTint = IconTint.NONE,
    val widgetHeaders: Boolean = true,
    val widgetVariants: Map<String, String> = emptyMap(),
    val moduleRadius: Dp = 12.dp,
    /** 枠の形(#40)。 */
    val moduleShape: ModuleShape = ModuleShape.ROUNDED,
    /** 枠線の太さ(#40)。 */
    val outlineWidth: Dp = 1.dp,
    /** 枠に付ける影(#40)。 */
    val shadow: ShadowStyle = ShadowStyle.NONE,
    /** 下地に敷く模様(#40)。 */
    val texture: Texture = Texture.NONE,
    /** すりガラスの度合い。0 なら不透明のまま(#40)。 */
    val glass: Dp = 0.dp,
    val dockStyle: DockStyle = DockStyle.RAIL,
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

    /** すりガラスのテーマか。面を半透明にして縁を光らせる(#40)。 */
    val isGlass: Boolean get() = glass > 0.dp

    /** アイコンを描き直すのに要るものひとまとめ。これが変わったらアプリ一覧を読み直す(#40)。 */
    val iconStyle: IconStyle get() = IconStyle(iconShape, iconTint, colors.accent.toArgb())
}

/** アイコンの描き方。形と色の落とし方、染めるときの色(#40)。 */
data class IconStyle(
    val shape: IconShape,
    val tint: IconTint = IconTint.NONE,
    val accent: Int = 0,
)

data class LauncherColors(
    val panel: Color = Color(0xCC0B0F14),
    val surface: Color = Color(0xFF05070A),
    val module: Color = Color(0x8C0B1118),
    val dock: Color = Color(0x99182230),
    val line: Color = Color(0x33E6E1D6),
    val accent: Color = Color(0xFFFFB000),
    val text: Color = Color(0xFFE6E1D6),
    val textDim: Color = Color(0xFF8A8F98),
    /** 並べたグラフの 2 本目など。テーマが指定しなければ accent と同じ(#40)。 */
    val alt: Color = accent,
    /** 注意を引く色。テーマが指定しなければ accent と同じ(#40)。 */
    val warn: Color = accent,
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
 * 設定画面からの上書き(#32、#34)。theme.json とは別に持ち、テーマを切り替えても残る。
 * null の項目はテーマの指定に従う。寸法は dp の整数。
 */
data class ThemeOverrides(
    val font: FontChoice = FontChoice.THEME,
    val iconShape: IconShape? = null,
    val sidePadding: Int? = null,
    val dockHeight: Int? = null,
    val dockPadding: Int? = null,
    val dockInset: Int? = null,
    val insetTop: Int? = null,
    val insetBottom: Int? = null,
) {
    /** 寸法の上書きを何も持っていないか。設定画面の RESET の表示に使う。 */
    val hasLayout: Boolean
        get() = sidePadding != null || dockHeight != null || dockPadding != null || dockInset != null || insetTop != null || insetBottom != null

    fun withoutLayout(): ThemeOverrides =
        copy(sidePadding = null, dockHeight = null, dockPadding = null, dockInset = null, insetTop = null, insetBottom = null)
}

fun ThemeSpec.toTheme(overrides: ThemeOverrides = ThemeOverrides()): LauncherTheme = LauncherTheme(
    id = id,
    name = name,
    columns = grid.columns,
    rows = grid.rows,
    dockSlots = grid.dockSlots,
    folderColumns = grid.folderColumns,
    sidePadding = (overrides.sidePadding ?: grid.sidePadding).dp,
    dockHeight = (overrides.dockHeight ?: dock.height).dp,
    dockPadding = (overrides.dockPadding ?: dock.padding).dp,
    dockInset = (overrides.dockInset ?: dock.inset).dp,
    insetTop = (overrides.insetTop ?: insets.top).dp,
    insetBottom = (overrides.insetBottom ?: insets.bottom).dp,
    showLabels = icon.labels,
    iconScale = icon.scale,
    iconShape = overrides.iconShape ?: icon.shape,
    iconTint = icon.tint,
    widgetHeaders = widgets.headers,
    widgetVariants = widgets.variants,
    moduleRadius = widgets.radius.dp,
    moduleShape = widgets.shape,
    outlineWidth = decor.outlineWidth.dp,
    shadow = decor.shadow,
    // texture を書いていない古いテーマは scanlines から決める(#40)
    texture = decor.texture ?: if (decor.scanlines) Texture.SCANLINES else Texture.NONE,
    glass = surface.blur.dp,
    dockStyle = dock.style,
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
        alt = palette.alt?.let { parseColor(it) } ?: parseColor(palette.accent),
        warn = palette.warn?.let { parseColor(it) } ?: parseColor(palette.accent),
    ),
)
