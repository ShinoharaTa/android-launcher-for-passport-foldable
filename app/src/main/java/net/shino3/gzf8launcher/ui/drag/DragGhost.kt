package net.shino3.gzf8launcher.ui.drag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.roundToInt

/** ドラッグ中にポインタへ追従する影。root の上に重ねて描く。 */
@Composable
fun DragGhost(session: DragSession) {
    val size = 64.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        modifier = Modifier.offset {
            IntOffset((session.position.x - half).roundToInt(), (session.position.y - half).roundToInt())
        },
    ) {
        GhostBody(session.payload, size)
    }
}

/**
 * 拒否された落としの影。指を離した位置から元の位置へ戻り、振動で拒否を伝える(#25)。
 * 戻り終えたら onDone で消してもらう。
 */
@Composable
fun RejectedGhost(rejected: RejectedDrop, onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val size = 64.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    val x = remember { Animatable(rejected.from.x) }
    val y = remember { Animatable(rejected.from.y) }
    LaunchedEffect(rejected) {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        val target = rejected.to.center
        val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
        launch { x.animateTo(target.x, spec) }
        y.animateTo(target.y, spec)
        onDone()
    }
    Box(
        modifier = Modifier.offset { IntOffset((x.value - half).roundToInt(), (y.value - half).roundToInt()) },
    ) {
        GhostBody(rejected.payload, size)
    }
}

@Composable
private fun GhostBody(payload: DragPayload, size: androidx.compose.ui.unit.Dp) {
    val theme = LocalLauncherTheme.current
    val icon = payload.icon
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
            Text(payload.label, color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 9.sp, maxLines = 2)
        }
    }
}
