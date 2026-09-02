package net.shino3.gzf8launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.shino3.gzf8launcher.data.AppEntry
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.FolderItem
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** フォルダを開いたときのポップアップ(docs/04 で両画面ともポップアップに決めた)。 */
@Composable
fun FolderPopup(
    folder: FolderItem,
    apps: Map<AppKey, AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 420.dp)
                .clip(shape)
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.line, shape)
                .padding(16.dp),
        ) {
            Text(
                text = "FOLDER // ${folder.name}",
                color = theme.colors.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(folder.apps, key = { it.component + it.user }) { app ->
                    AppCell(
                        entry = apps[app.key],
                        fallback = app.component.substringBefore('/').substringAfterLast('.'),
                        showLabel = true,
                        modifier = Modifier.aspectRatio(0.9f),
                        onClick = { entry ->
                            onLaunch(entry)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}
