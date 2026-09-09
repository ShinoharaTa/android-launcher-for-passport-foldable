package net.shino3.gzf8launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    /** HUD。時刻の下に、その日の進み具合を升で刻む(#40)。 */
    private val hud = WidgetRenderer<LocalDateTime> { now, _, modifier ->
        val theme = LocalLauncherTheme.current
        val filled = (now.hour * 60 + now.minute) * HUD_SEGMENTS / (24 * 60)
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(now.format(time), color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 38.sp)
            Text(now.format(dateMono).uppercase(), color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 10.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(HUD_SEGMENTS) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(if (i < filled) theme.colors.accent else theme.colors.accent.copy(alpha = 0.16f)),
                    )
                }
            }
        }
    }

    /** 計器の窓。桁ごとに彫り込んだ枠へ数字を落とす(#40)。 */
    private val odometer = WidgetRenderer<LocalDateTime> { now, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                now.format(time).forEach { ch ->
                    if (ch == ':') {
                        Text(":", color = theme.colors.textDim, fontFamily = theme.monoFont, fontSize = 26.sp)
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .background(theme.colors.panel, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("$ch", color = theme.colors.text, fontFamily = theme.uiFont, fontSize = 30.sp)
                        }
                    }
                }
            }
            Text(
                text = now.format(dateMono).uppercase(),
                color = theme.colors.textDim,
                fontFamily = theme.uiFont,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    /** いちばん静か。時刻と日付だけを置き、線も枠も引かない(#40)。 */
    private val quiet = WidgetRenderer<LocalDateTime> { now, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(now.format(time), color = theme.colors.text, fontFamily = theme.uiFont, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(now.format(datePlain), color = theme.colors.textDim, fontFamily = theme.uiFont, fontSize = 11.sp)
        }
    }

    /** 一日を刻む升の数。 */
    private const val HUD_SEGMENTS = 12

    val widget = NativeWidget(
        spec,
        source,
        mapOf(
            NativeWidget.DEFAULT_VARIANT to terminal,
            "plain" to plain,
            "hud" to hud,
            "odometer" to odometer,
            "quiet" to quiet,
        ),
    )
}
