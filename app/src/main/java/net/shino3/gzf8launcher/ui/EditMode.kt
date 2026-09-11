package net.shino3.gzf8launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * ホームの編集モード(#46)。
 *
 * 空き領域の長押しで入る。モード中はアイテムがわずかに揺れ、左上に ✕ が出て、
 * 長押しを待たずにつまめる。壁紙 / ウィジェット / 設定への入口は下端のバーに移す。
 *
 * 入っているかどうかは画面のあちこちが見るので、CompositionLocal で配る。
 */
val LocalEditMode = compositionLocalOf { false }

/**
 * 編集モード中に選ばれているアイテム(#47)。
 * ウィジェットのつまみは、選ばれている 1 つにだけ出す。全部に出すと画面が点だらけになる。
 */
val LocalSelectedItem = compositionLocalOf<ItemRef?> { null }

/**
 * 編集モード中の揺れ。
 * アイテムごとに位相をずらさないと全部が同じ向きに動いて、画面が波打って見える。
 * seed には並び順を渡す。テーマが揺れを切っているときは何もしない。
 */
@Composable
fun Modifier.jiggle(seed: Int): Modifier {
    val theme = LocalLauncherTheme.current
    if (!LocalEditMode.current || !theme.decor.jiggle) return this
    val transition = rememberInfiniteTransition(label = "jiggle")
    val phase by transition.animateFloat(
        initialValue = -JIGGLE_DEGREES,
        targetValue = JIGGLE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = JIGGLE_MILLIS, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse,
            // 位相をずらす。奇数番は逆向きから始める
            initialStartOffset = androidx.compose.animation.core.StartOffset(
                offsetMillis = (seed % 4) * (JIGGLE_MILLIS / 4),
            ),
        ),
        label = "phase",
    )
    return graphicsLayer { rotationZ = if (seed % 2 == 0) phase else -phase }
}

/**
 * 編集モード中にアイテムの左上へ出す ✕。
 * 押すと消える。押す場所は #32 の 44dp を取り、見た目の丸はその左上の角に寄せる。
 * 丸を中央に置くと当たり判定の半分がセルの外にはみ出し、ページの縁で切られて当たらなくなる(#53)。
 * 呼び出し側は BADGE_OVERHANG だけ左上へずらして置く。
 */
@Composable
fun BoxScopeRemoveBadge(onRemove: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val onRemoveNow = rememberUpdatedState(onRemove)
    Box(
        modifier = Modifier
            .size(TAP_MIN)
            .pointerInput(Unit) { detectTapGestures { onRemoveNow.value() } },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .size(BADGE_DOT)
                .clip(CircleShape)
                .background(theme.colors.surface.copy(alpha = 1f))
                .border(1.dp, theme.colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 13.sp)
        }
    }
}

/**
 * 編集モードの下端バー。もとのホームメニューの中身をここに移した。
 * ホーム自体を触れるようにしておきたいので、全画面の重ね描きにはしない。
 */
@Composable
fun EditBar(
    onOpenWallpaper: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val shape = theme.shapeOf(theme.moduleRadius + 6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(shape)
            .background(theme.colors.surface.copy(alpha = 1f))
            .border(1.dp, if (theme.decor.outlines) theme.outline else theme.colors.line, shape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextAction("WALLPAPER", accent = false) { onOpenWallpaper() }
        TextAction("WIDGETS", accent = false) { onOpenWidgets() }
        TextAction("SETTINGS", accent = false) { onOpenSettings() }
        TextAction("DONE") { onDone() }
    }
}

/** 揺れの振れ幅(度)。大きすぎるとアイコンの形が読みにくくなる。 */
private const val JIGGLE_DEGREES = 1.1f

/** 揺れの片道の時間。 */
private const val JIGGLE_MILLIS = 220

/** ✕ の見た目の大きさ。当たり判定は TAP_MIN。 */
private val BADGE_DOT = 24.dp

/**
 * ✕ の丸をセルの角からはみ出させる量。
 * 端の列でも画面の縁にかからず、ドックの中では縁で切られない程度に留める(ドックの余白はテーマで 0 になる)。
 */
val BADGE_OVERHANG = 4.dp
