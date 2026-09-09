package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.theme.Palette
import net.shino3.gzf8launcher.theme.ThemeSpec
import net.shino3.gzf8launcher.theme.parseColor

/**
 * 設定画面。ホームの空き領域の長押しメニューから開く(2026-09-03 決定)。
 * いまはテーマの選択だけを持つ。今後の設定項目もここに足す。
 */
@Composable
fun SettingsScreen(
    themes: List<ThemeSpec>,
    currentThemeId: String,
    onApplyTheme: (ThemeSpec) -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        ) {
            Text("SETTINGS", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                text = "CLOSE",
                color = theme.colors.accent,
                fontFamily = theme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "THEME",
                color = theme.colors.textDim,
                fontFamily = theme.monoFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            themes.forEach { spec ->
                ThemeRow(spec, selected = spec.id == currentThemeId) { onApplyTheme(spec) }
            }
            Text(
                text = "同梱テーマを選ぶと内部ストレージの theme.json に書き出される。" +
                    "そのファイルを直接書き換えれば、ここに無い見た目も作れる。",
                color = theme.colors.textDim,
                fontFamily = theme.uiFont,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
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
