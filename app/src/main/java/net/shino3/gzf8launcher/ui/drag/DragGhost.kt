package net.shino3.gzf8launcher.ui.drag

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.roundToInt

/** ドラッグ中にポインタへ追従する影。root の上に重ねて描く。 */
@Composable
fun DragGhost(session: DragSession) {
    val theme = LocalLauncherTheme.current
    val size = 64.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        modifier = Modifier.offset {
            IntOffset((session.position.x - half).roundToInt(), (session.position.y - half).roundToInt())
        },
    ) {
        val icon = session.payload.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.accent, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(session.payload.label, color = theme.colors.accent, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 2)
            }
        }
    }
}
