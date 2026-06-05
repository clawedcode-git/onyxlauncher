package com.onyxlauncher.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.Settings
import kotlinx.coroutines.flow.*

class DrawerViewModel(
    packageMonitor: PackageMonitor,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val apps: StateFlow<List<App>> = combine(
        packageMonitor.apps,
        _query,
    ) { allApps, q ->
        allApps
            .filter { !it.isHidden }
            .let { list ->
                if (q.isBlank()) list
                else list.filter { app ->
                    val label = (app.customLabel ?: app.label).lowercase()
                    val query = q.trim().lowercase()
                    // simple fuzzy: all chars appear in order
                    var idx = 0
                    for (ch in label) {
                        if (idx < query.length && ch == query[idx]) idx++
                    }
                    idx == query.length
                }
            }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    class Factory(
        private val packageMonitor: PackageMonitor,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DrawerViewModel(packageMonitor, settingsRepository) as T
    }
}
