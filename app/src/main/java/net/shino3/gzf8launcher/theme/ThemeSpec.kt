package net.shino3.gzf8launcher.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * テーマファイルの形(docs/04「配置と見た目を別のファイルに分ける」)。
 * 色は "#AARRGGBB" または "#RRGGBB" の文字列で書く。
 */
@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val palette: Palette,
    val surface: SurfaceSpec = SurfaceSpec(),
    val grid: GridSpec = GridSpec(),
    val icon: IconSpec = IconSpec(),
    val widgets: WidgetsSpec = WidgetsSpec(),
    val decor: DecorSpec = DecorSpec(),
    val typography: TypographySpec = TypographySpec(),
)

@Serializable
data class Palette(
    /** ホーム全体の下地。 */
    val panel: String,
    /** ドロワーやポップアップなど、前に出る面。 */
    val surface: String,
    /** ウィジェットとフォルダの箱。 */
    val module: String,
    /** ドックのレール。 */
    val dock: String,
    val line: String,
    val accent: String,
    val text: String,
    val textDim: String,
)

@Serializable
data class SurfaceSpec(
    /** 壁紙を透かすか。false なら panel を不透明に塗るのと同じ扱いになる。 */
    val showWallpaper: Boolean = true,
    /** panel の上に重ねるグラデーション。 */
    val gradient: Gradient? = null,
    /** 明るい下地か。ステータスバーのアイコン色をこれで決める。 */
    val light: Boolean = false,
)

@Serializable
data class Gradient(val from: String, val to: String, val vertical: Boolean = true)

@Serializable
data class GridSpec(
    val columns: Int = 6,
    val dockSlots: Int = 6,
    val folderColumns: Int = 3,
)

@Serializable
data class IconSpec(
    /** セルの短辺に対するアイコンの比率。 */
    val scale: Float = 0.62f,
    val labels: Boolean = false,
    val shape: IconShape = IconShape.SYSTEM,
)

/** アイコンの形。SYSTEM はアプリが持つ形をそのまま出す。 */
@Serializable
enum class IconShape {
    @SerialName("system") SYSTEM,
    @SerialName("circle") CIRCLE,
    @SerialName("squircle") SQUIRCLE,
}

@Serializable
data class WidgetsSpec(
    /** ウィジェット枠の見出し("CLOCK // LOCAL" など)を出すか。 */
    val headers: Boolean = true,
    /** ウィジェットとフォルダの角丸 px。 */
    val radius: Int = 12,
    /** ウィジェット種別 ID → 使うレンダラ名。未指定は default。 */
    val variants: Map<String, String> = emptyMap(),
)

@Serializable
data class DecorSpec(
    val cornerBrackets: Boolean = false,
    val scanlines: Boolean = false,
    val zoneHeaders: Boolean = true,
    val hingeMarker: Boolean = true,
    /** 枠線を描くか。ミニマル寄りのテーマでは消せる。 */
    val outlines: Boolean = true,
    /** カバーでページをめくるときの現在位置の点。 */
    val pageIndicator: Boolean = true,
)

/** 書体。"mono" / "sans" / "serif" / "default" のいずれか。 */
@Serializable
data class TypographySpec(val ui: String = "sans", val mono: String = "mono")
