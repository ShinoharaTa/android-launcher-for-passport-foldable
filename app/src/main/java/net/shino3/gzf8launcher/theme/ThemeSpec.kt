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
    /** 副色。並べたグラフの 2 本目など。未指定なら accent に落ちる(#40)。 */
    val alt: String? = null,
    /** 注意を引く色。残量の少なさなど。未指定なら accent に落ちる(#40)。 */
    val warn: String? = null,
)

@Serializable
data class SurfaceSpec(
    /** 壁紙を透かすか。false なら panel を不透明に塗るのと同じ扱いになる。 */
    val showWallpaper: Boolean = true,
    /** panel の上に重ねるグラデーション。 */
    val gradient: Gradient? = null,
    /** 明るい下地か。ステータスバーのアイコン色をこれで決める。 */
    val light: Boolean = false,
    /**
     * すりガラスの度合い(#40)。0 より大きいと、面を半透明にして縁を光らせる。
     *
     * 真の背面ぼかしではない。Compose には背面をぼかす素直な手段が無く、
     * ここでは「半透明 + 明るい縁 + 上面のわずかな光」で近似する。
     * 壁紙を主役にするテーマ向けで、壁紙が無いと効果が出ない。
     */
    val blur: Int = 0,
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
    /** レールの見せ方(#40)。 */
    val style: DockStyle = DockStyle.RAIL,
)

/**
 * ドックのレールの見せ方(#40)。
 * RAIL は箱、PILL は左右を丸めた帯、SEPARATE はレールを描かずアイコンだけ、NONE は落とし先だけ残して見せない。
 */
@Serializable
enum class DockStyle {
    @SerialName("rail") RAIL,
    @SerialName("pill") PILL,
    @SerialName("separate") SEPARATE,
    @SerialName("none") NONE,
}

/** 画面の上下に足す余白 dp。システムバーの分は別に取るので、その内側の余白(#34)。 */
@Serializable
data class InsetsSpec(val top: Int = 0, val bottom: Int = 0)

@Serializable
data class IconSpec(
    /** セルの短辺に対するアイコンの比率。 */
    val scale: Float = 0.62f,
    val labels: Boolean = false,
    val shape: IconShape = IconShape.SYSTEM,
    /** 色の落とし方(#40)。 */
    val tint: IconTint = IconTint.NONE,
)

/**
 * アイコンの色の扱い(#40)。
 * NONE はそのまま。MONO は彩度を落として単色に。QUANTIZE は階調を刻んで減色する。
 * TINT はテーマの accent 一色に染めて、形だけを残す。
 */
@Serializable
enum class IconTint(val label: String) {
    @SerialName("none") NONE("NONE"),
    @SerialName("mono") MONO("MONO"),
    @SerialName("quantize") QUANTIZE("QUANTIZE"),
    @SerialName("tint") TINT("TINT"),
}

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
    /** ウィジェットとフォルダの角丸 px。shape が ROUNDED 以外なら角の大きさとして働く。 */
    val radius: Int = 12,
    /** ウィジェット種別 ID → 使うレンダラ名。未指定は default。 */
    val variants: Map<String, String> = emptyMap(),
    /** 枠の形(#40)。 */
    val shape: ModuleShape = ModuleShape.ROUNDED,
)

/**
 * ウィジェットとフォルダの枠の形(#40)。
 * ROUNDED は角丸、CLIPPED は角を斜めに落とす、SQUARE は直角、PIXEL は角を階段状に刻む。
 */
@Serializable
enum class ModuleShape(val label: String) {
    @SerialName("rounded") ROUNDED("ROUNDED"),
    @SerialName("clipped") CLIPPED("CLIPPED"),
    @SerialName("square") SQUARE("SQUARE"),
    @SerialName("pixel") PIXEL("PIXEL"),
}

@Serializable
data class DecorSpec(
    /** 旧いテーマとの互換。texture を書いていないときだけ、これで SCANLINES か NONE を決める。 */
    val scanlines: Boolean = false,
    val zoneHeaders: Boolean = true,
    val hingeMarker: Boolean = true,
    /** 枠線を描くか。ミニマル寄りのテーマでは消せる。 */
    val outlines: Boolean = true,
    /** カバーでページをめくるときの現在位置の点。 */
    val pageIndicator: Boolean = true,
    /** 編集モード中にアイテムを揺らすか(#46)。動きが苦手なら切れる。 */
    val jiggle: Boolean = true,
    /** 下地に敷く模様(#40)。未指定なら scanlines から決める。 */
    val texture: Texture? = null,
    /** 枠線の太さ dp(#40)。 */
    val outlineWidth: Int = 1,
    /** 枠に付ける影(#40)。 */
    val shadow: ShadowStyle = ShadowStyle.NONE,
)

/**
 * ホーム全体の下地に敷く模様(#40)。触れないように装飾専用の層に描く。
 * SCANLINES は横線、DOTS は点の方眼、GRID は方眼、HATCH は斜線、BRUSHED は縦の削り目。
 */
@Serializable
enum class Texture {
    @SerialName("none") NONE,
    @SerialName("scanlines") SCANLINES,
    @SerialName("dots") DOTS,
    @SerialName("grid") GRID,
    @SerialName("hatch") HATCH,
    @SerialName("brushed") BRUSHED,
}

/**
 * 枠に付ける影(#40)。
 * DROP は右下にずらした単色、GLOW は枠の色のにじみ、INSET は上端の光と内側の影(削り出しの面)。
 */
@Serializable
enum class ShadowStyle {
    @SerialName("none") NONE,
    @SerialName("drop") DROP,
    @SerialName("glow") GLOW,
    @SerialName("inset") INSET,
}

/**
 * 書体。"default"(システム既定) / "sans" / "serif" / "mono" のいずれか。
 * ui はアプリ名やフォルダ名など日本語が入る場所、mono は "SEARCH // 26 APPS" のような英字の添え書き。
 * 日本語を等幅で描くと崩れるので、ui は default にしておくのが安全(#32)。
 */
@Serializable
data class TypographySpec(val ui: String = "default", val mono: String = "mono")
