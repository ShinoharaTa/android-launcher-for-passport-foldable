package net.shino3.gzf8launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.Zone
import net.shino3.gzf8launcher.model.ZoneId
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import net.shino3.gzf8launcher.ui.drag.LocalDragController
import kotlin.math.ceil

/**
 * ウィジェット面。縦にいくらでも伸びるグリッドを、画面の高さを下限にしてスクロールさせる(#19)。
 */
@Composable
fun WidgetsPage(
    zone: Zone,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 8.dp,
) {
    val theme = LocalLauncherTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = (maxWidth - sidePadding * 2) / theme.columns
        val minRows = if (cell > 0.dp) ceil((maxHeight / cell).toDouble()).toInt() else 0
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HomeGrid(
                zone = zone,
                zoneId = ZoneId.Widgets,
                columns = theme.columns,
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
                minRows = minRows,
            ) { index, placed ->
                ItemView(placed.item, ItemRef.Grid(ZoneId.Widgets, index), apps, actions, w = placed.w, h = placed.h)
            }
        }
    }
}

/**
 * アプリのページ 1 枚。段数はテーマで固定し、それが画面に収まらないときだけ縦に流す(#21)。
 * Fold8 では収まる前提で、収まらないのは段数の少ない画面(小さい AVD など)だけ。
 */
@Composable
fun AppPage(
    zone: Zone,
    index: Int,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 8.dp,
) {
    val theme = LocalLauncherTheme.current
    val zoneId = ZoneId.Page(index)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = (maxWidth - sidePadding * 2) / theme.columns
        val needed = cell * theme.rows + 8.dp
        val scrolls = needed > maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            HomeGrid(
                zone = zone,
                zoneId = zoneId,
                columns = theme.columns,
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
                fixedRows = theme.rows,
            ) { i, placed ->
                ItemView(placed.item, ItemRef.Grid(zoneId, i), apps, actions, w = placed.w, h = placed.h)
            }
        }
    }
}

/**
 * 1 ページずつ見せる面。カバー画面と、開いて縦長にしたメイン画面で使う。
 * ページ 0 がウィジェット面、1 以降がアプリのページ。着地は 1。
 */
@Composable
fun PagedSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    pagerState: PagerState,
    onCreatePage: () -> Unit,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 8.dp,
) {
    val theme = LocalLauncherTheme.current
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).dragEdgeFlip(pagerState, onCreatePage),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            if (page == 0) {
                WidgetsPage(layout.widgets, apps, actions, sidePadding = sidePadding)
            } else {
                val index = page - 1
                AppPage(layout.pages.getOrElse(index) { Zone() }, index, apps, actions, sidePadding = sidePadding)
            }
        }
        if (theme.decor.pageIndicator) PageIndicator(pagerState)
    }
}

/**
 * 開いた横長のメイン画面。左にウィジェット面(縦スクロール)、右にアプリのページ(横めくり)。
 * 左右の境界は、縦向きのヒンジが分かればその位置に合わせ、分からなければ半分にする。
 */
@Composable
fun SideBySideSurface(
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    appsPager: PagerState,
    onCreatePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val hinge = rememberVerticalHinge()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val leftWidth = hinge?.let { with(density) { it.left.toDp() } }?.takeIf { it > 0.dp && it < maxWidth } ?: (maxWidth / 2)
        val gapWidth = hinge?.let { with(density) { it.width.toDp() } } ?: 0.dp
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(leftWidth).fillMaxHeight()) {
                ZoneHeader("BOARD // GLANCE")
                WidgetsPage(layout.widgets, apps, actions, Modifier.weight(1f))
            }
            if (theme.decor.hingeMarker) {
                Box(modifier = Modifier.width(maxOf(1.dp, gapWidth)).fillMaxHeight().background(theme.colors.line))
            } else if (gapWidth > 0.dp) {
                Box(modifier = Modifier.width(gapWidth).fillMaxHeight())
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ZoneHeader("APPS // PRIMARY")
                HorizontalPager(
                    state = appsPager,
                    modifier = Modifier.weight(1f).dragEdgeFlip(appsPager, onCreatePage),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    AppPage(layout.pages.getOrElse(page) { Zone() }, page, apps, actions)
                }
                if (theme.decor.pageIndicator) PageIndicator(appsPager)
            }
        }
    }
}

/**
 * ドラッグ中に端へ留めるとページがめくれる。最後のページの右端なら新しいページを作ってそこへ移る(#21)。
 * 留めているあいだは一定間隔でめくり続ける。
 */
@Composable
private fun Modifier.dragEdgeFlip(pagerState: PagerState, onCreatePage: () -> Unit): Modifier {
    val drag = LocalDragController.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val edgePx = with(LocalDensity.current) { 40.dp.toPx() }
    LaunchedEffect(drag, pagerState) {
        snapshotFlow {
            val pos = drag.session?.position ?: return@snapshotFlow 0
            when {
                !bounds.contains(pos) -> 0
                pos.x > bounds.right - edgePx -> 1
                pos.x < bounds.left + edgePx -> -1
                else -> 0
            }
        }.distinctUntilChanged().collectLatest { direction ->
            if (direction == 0) return@collectLatest
            while (true) {
                delay(EDGE_HOLD_MS)
                val current = pagerState.currentPage
                val target = current + direction
                if (target < 0) continue
                if (target >= pagerState.pageCount) {
                    onCreatePage()
                    // ページ数が増えるのを待ってから移る
                    snapshotFlow { pagerState.pageCount }.first { it > target }
                }
                pagerState.animateScrollToPage(target)
            }
        }
    }
    return onGloballyPositioned { bounds = it.boundsInRoot() }
}

private const val EDGE_HOLD_MS = 550L

/** 縦向きのヒンジの矩形(px)。無ければ null。実機で折りたたみ状態が取れたときだけ効く。 */
@Composable
private fun rememberVerticalHinge(): Rect? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return null
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
                ?.bounds
                ?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        }
    }
    val hinge by flow.collectAsStateWithLifecycle(initialValue = null)
    return hinge
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    val theme = LocalLauncherTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
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
