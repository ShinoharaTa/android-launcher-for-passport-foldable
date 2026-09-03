package net.shino3.gzf8launcher.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import net.shino3.gzf8launcher.theme.LocalLauncherTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 時計と日付。毎秒更新する。 */
object ClockWidget {
    val spec = WidgetSpec(id = "clock", name = "CLOCK // LOCAL", defaultW = 3, defaultH = 2, minW = 2, minH = 1)

    private val source = WidgetDataSource {
        flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(1_000)
            }
        }
    }

    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val seconds = DateTimeFormatter.ofPattern("ss")
    private val dateMono = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)
    private val datePlain = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

    /** 計器風。秒まで出す。 */
    private val terminal = WidgetRenderer<LocalDateTime> { now, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(now.format(time), color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 40.sp)
                Text(" ${now.format(seconds)}", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 16.sp)
            }
            Text(now.format(dateMono).uppercase(), color = theme.colors.text, fontFamily = theme.monoFont, fontSize = 12.sp)
        }
    }

    /** ミニマル。秒を出さず、日付を綴りで書く。 */
    private val plain = WidgetRenderer<LocalDateTime> { now, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(now.format(time), color = theme.colors.text, fontFamily = theme.uiFont, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(now.format(datePlain), color = theme.colors.textDim, fontFamily = theme.uiFont, fontSize = 13.sp)
        }
    }

    val widget = NativeWidget(spec, source, mapOf(NativeWidget.DEFAULT_VARIANT to terminal, "plain" to plain))
}
