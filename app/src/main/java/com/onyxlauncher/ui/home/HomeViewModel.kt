package com.onyxlauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.HomeItemMapper.toDomain
import com.onyxlauncher.data.db.HomeItemMapper.toEntity
import com.onyxlauncher.data.db.dao.HomeItemDao
import com.onyxlauncher.domain.model.App
import com.onyxlauncher.domain.model.HomeItem
import com.onyxlauncher.domain.model.Settings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeState(
    val pages: Map<Int, List<HomeItem>> = emptyMap(),
    val dock: List<HomeItem> = emptyList(),
    val pageCount: Int = 1,
    val settings: Settings = Settings(),
    val apps: List<App> = emptyList(),
)

class HomeViewModel(
    private val homeItemDao: HomeItemDao,
    packageMonitor: PackageMonitor,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        homeItemDao.observeAll(),
        settingsRepository.settings,
        packageMonitor.apps,
    ) { entities, settings, apps ->
        val items = entities.mapNotNull { it.toDomain() }
        val dock = items.filter { it.page == -1 }.sortedBy { it.gridX }
        val pageItems = items.filter { it.page >= 0 }.groupBy { it.page }
        val pageCount = (pageItems.keys.maxOrNull() ?: 0) + 1
        HomeState(
            pages = pageItems,
            dock = dock,
            pageCount = pageCount,
            settings = settings,
            apps = apps,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun moveItem(item: HomeItem, toPage: Int, toX: Int, toY: Int) {
        viewModelScope.launch {
            homeItemDao.move(item.toEntity(), toPage, toX, toY)
        }
    }

    fun removeItem(item: HomeItem) {
        viewModelScope.launch {
            homeItemDao.deleteById(item.id)
        }
    }

    fun addShortcut(app: App, page: Int, gridX: Int, gridY: Int) {
        viewModelScope.launch {
            val item = HomeItem.Shortcut(
                id = 0,
                page = page,
                gridX = gridX,
                gridY = gridY,
                componentName = app.componentName,
                userSerial = app.userSerial,
            )
            homeItemDao.insert(item.toEntity())
        }
    }

    class Factory(
        private val homeItemDao: HomeItemDao,
        private val packageMonitor: PackageMonitor,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(homeItemDao, packageMonitor, settingsRepository) as T
    }
}
