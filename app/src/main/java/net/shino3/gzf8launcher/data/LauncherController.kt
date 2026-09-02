package net.shino3.gzf8launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.shino3.gzf8launcher.model.AppItem
import net.shino3.gzf8launcher.model.AppKey
import net.shino3.gzf8launcher.model.Layout

/** 画面に流す状態をまとめる。アクティビティの lifecycleScope で動く。 */
class LauncherController(context: Context, private val scope: CoroutineScope) {
    private val appRepository = AppRepository(context)
    private val layoutRepository = LayoutRepository(context)

    private val _apps = MutableStateFlow<Map<AppKey, AppEntry>>(emptyMap())
    val apps: StateFlow<Map<AppKey, AppEntry>> = _apps
    val layout: StateFlow<Layout> = layoutRepository.layout

    fun start() {
        scope.launch { layoutRepository.load() }
        scope.launch {
            refreshApps()
            appRepository.changes().collect { refreshApps() }
        }
    }

    fun launch(entry: AppEntry) = appRepository.launch(entry)

    fun resolve(item: AppItem): AppEntry? = _apps.value[item.key]

    private suspend fun refreshApps() {
        _apps.value = withContext(Dispatchers.IO) { appRepository.loadApps() }.associateBy { it.key }
    }
}
