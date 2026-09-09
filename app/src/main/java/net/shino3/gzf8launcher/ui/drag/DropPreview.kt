package net.shino3.gzf8launcher.ui.drag

import androidx.compose.runtime.compositionLocalOf
import net.shino3.gzf8launcher.model.LayoutEditor
import net.shino3.gzf8launcher.model.Placement
import net.shino3.gzf8launcher.model.ZoneId

/** ドラッグ中に落ちる位置と、落としたら何が起きるか。グリッドが枠を描くのに使う(#25)。 */
data class DropPreview(val zone: ZoneId, val placement: Placement, val kind: LayoutEditor.DropKind)

val LocalDropPreview = compositionLocalOf<DropPreview?> { null }
