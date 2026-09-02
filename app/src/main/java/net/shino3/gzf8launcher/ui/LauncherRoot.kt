package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.data.LauncherController
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** どの面を出すかを実寸 dp で決め(docs/01)、共通のドックとフォルダポップアップを持つ。 */
@Composable
fun LauncherRoot(controller: LauncherController) {
    val theme = LocalLauncherTheme.current
    val apps by controller.apps.collectAsStateWithLifecycle()
    val layout by controller.layout.collectAsStateWithLifecycle()
    var openFolder by remember { mutableStateOf<FolderItem?>(null) }
    val actions = remember(controller) {
        ItemActions(
            onLaunch = { controller.launch(it) },
            onOpenFolder = { openFolder = it },
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(LocalDensity.current) { containerSize.width.toDp() }
    val isCover = widthDp < 600.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.colors.panel)
            .systemBarsPadding(),
    ) {
        MetricsStrip()
        if (isCover) {
            CoverSurface(layout, apps, actions, Modifier.weight(1f))
        } else {
            MainSurface(layout, apps, actions, Modifier.weight(1f))
        }
        Dock(layout.dock, apps, actions)
    }

    openFolder?.let { folder ->
        FolderPopup(folder, apps, onLaunch = { controller.launch(it) }, onDismiss = { openFolder = null })
    }
}

/** カバー画面(閉)。カバー面のゾーンをそのまま描く。 */
@Composable
fun CoverSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    HomeGrid(layout.cover, theme.columns, modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { placed ->
        ItemView(placed.item, apps, actions)
    }
}

/**
 * メイン画面(開)。右をアンカー(カバー面の参照)、左を拡張パネルにする(docs/03)。
 * アンカーの左右切り替えはテーマに載せるまで右固定。
 */
@Composable
fun MainSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Row(modifier = modifier) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("EXT.PANEL // UNFOLD+")
            HomeGrid(layout.extension, theme.columns, Modifier.weight(1f).padding(horizontal = 8.dp)) { placed ->
                ItemView(placed.item, apps, actions)
            }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.colors.line))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("PRIMARY // SYNC:COVER")
            HomeGrid(layout.cover, theme.columns, Modifier.weight(1f).padding(horizontal = 8.dp)) { placed ->
                ItemView(placed.item, apps, actions)
            }
        }
    }
}

@Composable
private fun ZoneHeader(text: String) {
    val theme = LocalLauncherTheme.current
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
