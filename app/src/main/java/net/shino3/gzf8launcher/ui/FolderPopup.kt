package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.model.FolderRule
import net.shino3.gzf8launcher.model.ItemRef
import net.shino3.gzf8launcher.model.Layout
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/**
 * フォルダを開いたときのポップアップ(docs/04 で両画面ともポップアップに決めた)。
 * ドラッグでホームへ運び出せるように、別ウィンドウの Dialog ではなく同じウィンドウの重ね描きにする。
 */
@Composable
fun FolderPopup(
    folderRef: ItemRef,
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    actions: ItemActions,
    hidden: Boolean,
    usagePermitted: Boolean,
    onRename: (String) -> Unit,
    onRuleChange: (FolderRule) -> Unit,
    onRequestUsagePermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val folder = LayoutEditor.itemAt(layout, folderRef) as? FolderItem
    LaunchedEffect(folder == null) { if (folder == null) onDismiss() }
    if (folder == null) return
    val members = actions.resolveFolder(folder)
    val shape = RoundedCornerShape(theme.moduleRadius + 6.dp)

    Overlay(hidden = hidden, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 440.dp)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.outline, shape)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FOLDER // ", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 12.sp)
                BasicTextField(
                    value = folder.name,
                    onValueChange = onRename,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 12.sp),
                    cursorBrush = SolidColor(theme.colors.accent),
                    modifier = Modifier.weight(1f),
                )
            }
            RuleSelector(folder.rule, onRuleChange)
            if (folder.rule != FolderRule.Manual && !usagePermitted && folder.rule !is FolderRule.Category) {
                Text(
                    text = "使用状況へのアクセスを許可する →",
                    color = theme.colors.accent,
                    fontFamily = theme.uiFont,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .pointerInput(Unit) { detectTapGestures { onRequestUsagePermission() } },
                )
            }
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(members, key = { i, app -> "${app.component}@${app.user}#$i" }) { index, app ->
                    ItemView(
                        item = app,
                        ref = ItemRef.InFolder(folderRef, index),
                        apps = apps,
                        actions = ItemActions(
                            onLaunch = { actions.onLaunch(it); onDismiss() },
                            onOpenFolder = {},
                            resolveFolder = actions.resolveFolder,
                        ),
                        modifier = Modifier.aspectRatio(0.9f),
                        showLabel = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSelector(current: FolderRule, onChange: (FolderRule) -> Unit) {
    val theme = LocalLauncherTheme.current
    val options = listOf(
        "MANUAL" to FolderRule.Manual,
        "RECENT" to FolderRule.Recent(),
        "FREQUENT" to FolderRule.Frequent(),
        "SOCIAL" to FolderRule.Category(android.content.pm.ApplicationInfo.CATEGORY_SOCIAL),
        "GAME" to FolderRule.Category(android.content.pm.ApplicationInfo.CATEGORY_GAME),
        "AUDIO/VIDEO" to FolderRule.Category(android.content.pm.ApplicationInfo.CATEGORY_AUDIO),
    )
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        options.forEach { (label, rule) ->
            val selected = current::class == rule::class &&
                (rule !is FolderRule.Category || (current as FolderRule.Category).category == rule.category)
            Text(
                text = label,
                color = if (selected) theme.colors.surface else theme.colors.textDim,
                fontFamily = theme.monoFont,
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) theme.colors.accent else Color.Transparent)
                    .border(1.dp, if (selected) theme.colors.accent else theme.colors.line, RoundedCornerShape(6.dp))
                    .pointerInput(rule) { detectTapGestures { onChange(rule) } }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** 画面全体を覆う重ね描き。外側のタップで閉じる。hidden のあいだは見えないが合成には残る(ドラッグ継続のため)。 */
@Composable
fun Overlay(hidden: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val scrim = if (LocalLauncherTheme.current.light) Color(0x99FFFFFF) else Color(0x99000000)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (hidden) 0f else 1f)
            .background(scrim)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
            content()
        }
    }
}
