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
    val dock: DockSpec = DockSpec(),
    val insets: InsetsSpec = InsetsSpec(),
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
    /** アプリのページ 1 枚の段数。Fold8 はカバーもメインの片側も 7 段(#21)。 */
    val rows: Int = 7,
    val dockSlots: Int = 6,
    val folderColumns: Int = 3,
    /** グリッドとドックの左右の余白 dp。両方で同じ値を使い、ドックのレールの幅をグリッドに揃える(#34)。 */
    val sidePadding: Int = 8,
)

/** ドックのレール(#34)。 */
@Serializable
data class DockSpec(
    /** レールの高さ dp。 */
    val height: Int = 76,
    /** レールの縁からアイコンまでの余白 dp。 */
    val padding: Int = 0,
    /** グリッドの外縁からレールを引っ込める量 dp。レールはグリッドいっぱいには広げず、中央に寄せる。 */
    val inset: Int = 20,
)

/** 画面の上下に足す余白 dp。システムバーの分は別に取るので、その内側の余白(#34)。 */
@Serializable
data class InsetsSpec(val top: Int = 0, val bottom: Int = 0)

@Serializable
data class IconSpec(
    /** セルの短辺に対するアイコンの比率。 */
    val scale: Float = 0.62f,
    val labels: Boolean = false,
    val shape: IconShape = IconShape.SYSTEM,
)

/**
 * アイコンの形(#32)。SYSTEM は端末のマスク(Galaxy なら角丸四角)で切られたものをそのまま出す。
 * それ以外は AdaptiveIcon の背景と前景を自前で描いてから、この形で切る。
 */
@Serializable
enum class IconShape(val label: String) {
    @SerialName("system") SYSTEM("SYSTEM"),
    @SerialName("circle") CIRCLE("CIRCLE"),
    @SerialName("rounded") ROUNDED("ROUNDED"),
    @SerialName("square") SQUARE("SQUARE"),
    @SerialName("squircle") SQUIRCLE("SQUIRCLE"),
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
    val scanlines: Boolean = false,
    val zoneHeaders: Boolean = true,
    val hingeMarker: Boolean = true,
    /** 枠線を描くか。ミニマル寄りのテーマでは消せる。 */
    val outlines: Boolean = true,
    /** カバーでページをめくるときの現在位置の点。 */
    val pageIndicator: Boolean = true,
)

/**
 * 書体。"default"(システム既定) / "sans" / "serif" / "mono" のいずれか。
 * ui はアプリ名やフォルダ名など日本語が入る場所、mono は "SEARCH // 26 APPS" のような英字の添え書き。
 * 日本語を等幅で描くと崩れるので、ui は default にしておくのが安全(#32)。
 */
@Serializable
data class TypographySpec(val ui: String = "default", val mono: String = "mono")
