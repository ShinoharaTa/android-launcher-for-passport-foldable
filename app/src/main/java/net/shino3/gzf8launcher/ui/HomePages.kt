package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.Zone
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import kotlin.math.ceil

/** ページの並び。カバーでは横にめくり、開くと左右に並ぶ。着地はアプリ面(#19)。 */
object Pages {
    const val WIDGETS = 0
    const val APPS = 1
    const val COUNT = 2
}

/** 1 ページ。縦にいくらでも伸びるグリッドを、画面の高さを下限にしてスクロールさせる。 */
@Composable
fun HomePage(
    zone: Zone,
    zoneId: ZoneId,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 8.dp,
) {
    val theme = LocalLauncherTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gridWidth = maxWidth - sidePadding * 2
        val cell = gridWidth / theme.columns
        val minRows = if (cell > 0.dp) ceil((maxHeight / cell).toDouble()).toInt() else 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HomeGrid(
                zone = zone,
                zoneId = zoneId,
                columns = theme.columns,
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
                minRows = minRows,
            ) { index, placed ->
                ItemView(placed.item, ItemRef.Grid(zoneId, index), apps, actions, w = placed.w, h = placed.h)
            }
        }
    }
}

/**
 * 1 ページずつ見せる面。カバー画面と、開いて縦長にしたメイン画面で使う。
 * 縦長のメインでは余白を広げ、1 ページを余裕をもって置く。
 */
@Composable
fun PagedSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 8.dp,
) {
    val theme = LocalLauncherTheme.current
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            when (page) {
                Pages.WIDGETS -> HomePage(layout.widgets, ZoneId.WIDGETS, apps, actions, sidePadding = sidePadding)
                else -> HomePage(layout.apps, ZoneId.APPS, apps, actions, sidePadding = sidePadding)
            }
        }
        if (theme.decor.pageIndicator) PageIndicator(pagerState)
    }
}

/** 開いた横長のメイン画面。左にウィジェット面、右にアプリ面。それぞれ縦スクロール。 */
@Composable
fun SideBySideSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Row(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("BOARD // GLANCE")
            HomePage(layout.widgets, ZoneId.WIDGETS, apps, actions, Modifier.weight(1f))
        }
        if (theme.decor.hingeMarker) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.colors.line))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ZoneHeader("APPS // PRIMARY")
            HomePage(layout.apps, ZoneId.APPS, apps, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    val theme = LocalLauncherTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        repeat(pagerState.pageCount) { page ->
            val active = pagerState.currentPage == page
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (active) theme.colors.accent else theme.colors.line),
            )
        }
    }
}

@Composable
private fun ZoneHeader(text: String) {
    val theme = LocalLauncherTheme.current
    if (!theme.decor.zoneHeaders) return
    Text(
        text = text,
        color = theme.colors.textDim,
        fontFamily = theme.monoFont,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
