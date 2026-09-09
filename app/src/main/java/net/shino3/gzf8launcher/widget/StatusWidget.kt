package net.shino3.gzf8launcher.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import net.shino3.gzf8launcher.theme.LocalLauncherTheme

/** バッテリーと通信状態の計器(モックの BATT 相当)。 */
object StatusWidget {
    data class State(val level: Int, val charging: Boolean, val network: String)

    val spec = WidgetSpec(id = "status", name = "SYS // STATUS", defaultW = 3, defaultH = 1, minW = 2, minH = 1)

    private fun battery(context: Context) = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                trySend(level * 100 / scale to charging)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun network(context: Context) = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
                        else -> "LINK"
                    },
                )
            }

            override fun onLost(network: Network) {
                trySend("NONE")
            }
        }
        trySend("NONE")
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    private val source = WidgetDataSource { context ->
        combine(battery(context), network(context)) { (level, charging), net -> State(level, charging, net) }
    }

    /** 計器風。太さのあるバーを敷く。 */
    private val gauge = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.level}%", color = theme.colors.accent, fontFamily = theme.monoFont, fontSize = 20.sp)
                Text(
                    text = " BATT${if (state.charging) "+" else ""} // ${state.network}",
                    color = theme.colors.textDim,
                    fontFamily = theme.monoFont,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(bottom = 3.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(theme.colors.line)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.level / 100f)
                        .fillMaxHeight()
                        .background(theme.colors.accent),
                )
            }
        }
    }

    /** ミニマル。細い線と控えめな添え字だけにする。 */
    private val plain = WidgetRenderer<State> { state, _, modifier ->
        val theme = LocalLauncherTheme.current
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.level}", color = theme.colors.text, fontFamily = theme.uiFont, fontSize = 26.sp)
                Text(
                    text = if (state.charging) "%  charging  ·  ${state.network.lowercase()}" else "%  ·  ${state.network.lowercase()}",
                    color = theme.colors.textDim,
                    fontFamily = theme.uiFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(theme.colors.line)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.level / 100f)
                        .fillMaxHeight()
                        .background(theme.colors.text),
                )
            }
        }
    }

    val widget = NativeWidget(spec, source, mapOf(NativeWidget.DEFAULT_VARIANT to gauge, "plain" to plain))
}
