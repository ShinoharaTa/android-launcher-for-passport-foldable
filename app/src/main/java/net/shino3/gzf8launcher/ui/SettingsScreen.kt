package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.theme.FontChoice
import net.shino3.gzf8launcher.theme.IconShape
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.theme.Palette
import net.shino3.gzf8launcher.theme.ThemeOverrides
import net.shino3.gzf8launcher.theme.ThemeSpec
import net.shino3.gzf8launcher.theme.parseColor

/**
 * 設定画面。ホームの空き領域の長押しメニューから開く(2026-09-03 決定)。
 * テーマの選択と、その上に重ねる上書き(書体、アイコンの形、#32)を持つ。
 * 上書きはテーマを切り替えても残る。
 */
@Composable
fun SettingsScreen(
    themes: List<ThemeSpec>,
    currentThemeId: String,
    overrides: ThemeOverrides,
    onApplyTheme: (ThemeSpec) -> Unit,
    onFont: (FontChoice) -> Unit,
    onIconShape: (IconShape?) -> Unit,
    /** 寸法の上書きを部分的に変える(#34)。 */
    onOverrides: ((ThemeOverrides) -> ThemeOverrides) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 設定は読ませる画面なので、テーマの surface が半透明でも不透明にする
            .background(theme.colors.surface.copy(alpha = 1f))
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Text("SETTINGS", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextAction("CLOSE") { onClose() }
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("FONT")
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
                FontChoice.entries.forEach { choice ->
                    Chip(choice.label, selected = overrides.font == choice, modifier = Modifier.padding(end = 8.dp)) { onFont(choice) }
                }
            }
            Note("SYSTEM にすると、すべての文字を端末の既定の書体で描く。日本語が崩れるときはこれにする。")

            SectionTitle("ICON SHAPE")
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
                Chip("THEME", selected = overrides.iconShape == null, modifier = Modifier.padding(end = 8.dp)) { onIconShape(null) }
                IconShape.entries.forEach { shape ->
                    Chip(shape.label, selected = overrides.iconShape == shape, modifier = Modifier.padding(end = 8.dp)) { onIconShape(shape) }
                }
            }
            Note("SYSTEM は端末が切った形(Galaxy なら角丸四角)をそのまま出す。それ以外はアプリの背景と前景をこの形で切り直す。古い形式のアイコンは変わらない。")

            SectionTitle("LAYOUT")
            // 表示する値は上書きを重ねた後の実際の値(LocalLauncherTheme)。上書きが無い項目はテーマの値が出る
            Stepper("SIDE PADDING", theme.sidePadding.value.toInt(), 0..48, 2, overrides.sidePadding != null) { v ->
                onOverrides { it.copy(sidePadding = v) }
            }
            Stepper("DOCK HEIGHT", theme.dockHeight.value.toInt(), 48..120, 4, overrides.dockHeight != null) { v ->
                onOverrides { it.copy(dockHeight = v) }
            }
            Stepper("DOCK PADDING", theme.dockPadding.value.toInt(), 0..24, 2, overrides.dockPadding != null) { v ->
                onOverrides { it.copy(dockPadding = v) }
            }
            Stepper("TOP INSET", theme.insetTop.value.toInt(), 0..96, 4, overrides.insetTop != null) { v ->
                onOverrides { it.copy(insetTop = v) }
            }
            Stepper("BOTTOM INSET", theme.insetBottom.value.toInt(), 0..96, 4, overrides.insetBottom != null) { v ->
                onOverrides { it.copy(insetBottom = v) }
            }
            if (overrides.hasLayout) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextAction("RESET ALL LAYOUT") { onOverrides { it.withoutLayout() } }
                }
            }
            Note("SIDE PADDING はグリッドとドックの左右の余白で、ドックのレールの幅はグリッドに揃う。TOP / BOTTOM INSET はステータスバーとナビゲーションバーの内側に足す余白。アクセント色の値は設定で上書きしている。")

            SectionTitle("THEME")
            themes.forEach { spec ->
                ThemeRow(spec, selected = spec.id == currentThemeId) { onApplyTheme(spec) }
            }
            Note("同梱テーマを選ぶと内部ストレージの theme.json に書き出される。そのファイルを直接書き換えれば、ここに無い見た目も作れる。書体、アイコンの形、寸法の上書きはテーマとは別に残る。")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val theme = LocalLauncherTheme.current
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = theme.monoFont,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    val theme = LocalLauncherTheme.current
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = theme.uiFont,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ThemeRow(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.moduleRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(theme.colors.module)
            .border(1.dp, if (selected) theme.colors.accent else theme.outline, shape)
            .pointerInput(spec.id) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(spec.name, color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 13.sp)
            Text(spec.id, color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp)
        }
        Swatches(spec.palette)
        Text(
            text = if (selected) "  ●" else "  ○",
            color = if (selected) theme.colors.accent else theme.colors.textDim,
            fontFamily = theme.monoFont,
            fontSize = 13.sp,
        )
    }
}

/** テーマの配色を小さな帯で見せる。 */
@Composable
private fun Swatches(palette: Palette) {
    val theme = LocalLauncherTheme.current
    Row {
        listOf(palette.panel, palette.module, palette.accent, palette.text).forEach { value ->
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(18.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(parseColor(value))
                    .border(1.dp, theme.colors.line, RoundedCornerShape(4.dp)),
            )
        }
    }
}
