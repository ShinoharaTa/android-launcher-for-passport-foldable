package net.shino3.gzf8launcher.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 見た目の定義。マイルストーン 7 で JSON から読むようにするまでは Kotlin の既定値で運用する。
 * 配置(Layout)とは別物(docs/04)。
 */
data class LauncherTheme(
    /** カバー面の列数。docs/03 の確定要件は 6 列。 */
    val columns: Int = 6,
    val dockSlots: Int = 6,
    /** アイコン下にアプリ名を出すか。既定はなし(2026-09-03 決定)。 */
    val showLabels: Boolean = false,
    /** セル幅に対するアイコンの比率。 */
    val iconScale: Float = 0.62f,
    val folderColumns: Int = 3,
    /** ウィジェット枠の見出し("CLOCK // LOCAL" など)を出すか。 */
    val widgetHeaders: Boolean = true,
    /** ウィジェット種別 ID → 使うレンダラ名。未指定は default。 */
    val widgetVariants: Map<String, String> = emptyMap(),
    val colors: LauncherColors = LauncherColors(),
)

data class LauncherColors(
    val panel: Color = Color(0xCC0B0F14),
    val surface: Color = Color(0xFF05070A),
    val folder: Color = Color(0x99182230),
    val widget: Color = Color(0x8C0B1118),
    val line: Color = Color(0x33E6E1D6),
    val accent: Color = Color(0xFFFFB000),
    val text: Color = Color(0xFFE6E1D6),
    val textDim: Color = Color(0xFF8A8F98),
)

val LocalLauncherTheme = staticCompositionLocalOf { LauncherTheme() }
