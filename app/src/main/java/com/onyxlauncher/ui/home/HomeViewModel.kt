package com.onyxlauncher.ui.home

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyxlauncher.data.`package`.PackageMonitor
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.data.db.FolderMapper.toDomain
import com.onyxlauncher.data.db.FolderMapper.toEntity
import com.onyxlauncher.data.db.HomeItemMapper.toDomain
import com.onyxlauncher.data.db.HomeItemMapper.toEntity
import com.onyxlauncher.data.db.dao.AppOverrideDao
import com.onyxlauncher.data.db.dao.FolderDao
import com.onyxlauncher.data.db.dao.HomeItemDao
import com.onyxlauncher.data.db.entity.AppOverrideEntity
import com.onyxlauncher.data.widget.OnyxWidgetHost
import com.onyxlauncher.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────
data class HomeState(
    val pages: Map<Int, List<HomeItem>> = emptyMap(),
    val dock: List<HomeItem> = emptyList(),
    val folders: Map<Long, Folder> = emptyMap(),
    val pageCount: Int = 1,
    val settings: Settings = Settings(),
    val apps: List<App> = emptyList(),
)

data class ContextMenuState(
    val item: HomeItem.Shortcut,
    val app: App,
    val shortcuts: List<ShortcutInfo> = emptyList(),
)

data class FolderSheetState(
    val folder: Folder,
    val apps: List<App>,
)

class HomeViewModel(
    private val homeItemDao: HomeItemDao,
    private val folderDao: FolderDao,
    private val appOverrideDao: AppOverrideDao,
    packageMonitor: PackageMonitor,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // ── home state ───────────────────────────────────────────────────────────
    val state: StateFlow<HomeState> = combine(
        homeItemDao.observeAll(),
        folderDao.observeAll(),
        settingsRepository.settings,
        packageMonitor.apps,
    ) { entities, folderEntities, settings, apps ->
        val items = entities.mapNotNull { it.toDomain() }
        val dock = items.filter { it.page == -1 }.sortedBy { it.gridX }
        val pageItems = items.filter { it.page >= 0 }.groupBy { it.page }
        val pageCount = maxOf(1, (pageItems.keys.maxOrNull() ?: 0) + 1)
        val folders = folderEntities.associate { it.id to it.toDomain() }
        HomeState(
            pages = pageItems,
            dock = dock,
            folders = folders,
            pageCount = pageCount,
            settings = settings,
            apps = apps,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ── widget add flow ──────────────────────────────────────────────────────
    /** Set when the user picks a provider; consumed by MainActivity to drive the
     *  BIND_APPWIDGET → configure → placeWidget flow. Reset to null after consumption. */
    val pendingWidgetProvider = MutableStateFlow<AppWidgetProviderInfo?>(null)

    fun startWidgetAdd(info: AppWidgetProviderInfo) {
        pendingWidgetProvider.value = info
    }

    fun placeWidget(appWidgetId: Int, page: Int, col: Int, row: Int, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val item = HomeItem.WidgetRef(
                    id = 0, page = page, gridX = col, gridY = row,
                    appWidgetId = appWidgetId,
                    spanX = spanX, spanY = spanY,
                )
                homeItemDao.insert(item.toEntity())
            }
        }
    }

    fun removeWidget(item: HomeItem.WidgetRef, widgetHost: OnyxWidgetHost) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                homeItemDao.deleteById(item.id)
            }
            widgetHost.deleteAppWidgetId(item.appWidgetId)
        }
    }

    fun resizeWidget(item: HomeItem.WidgetRef, newSpanX: Int, newSpanY: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                homeItemDao.update(
                    item.toEntity().copy(
                        payload = buildString {
                            append("{\"widgetId\":\"${item.appWidgetId}\"")
                            append(",\"spanX\":\"$newSpanX\"")
                            append(",\"spanY\":\"$newSpanY\"}")
                        }
                    )
                )
            }
        }
    }

    // ── overlay state ────────────────────────────────────────────────────────
    private val _contextMenu = MutableStateFlow<ContextMenuState?>(null)
    val contextMenu: StateFlow<ContextMenuState?> = _contextMenu.asStateFlow()

    private val _folderSheet = MutableStateFlow<FolderSheetState?>(null)
    val folderSheet: StateFlow<FolderSheetState?> = _folderSheet.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Drop handling
    // ─────────────────────────────────────────────────────────────────────────
    fun onDrop(dragState: DragAndDropState, currentPage: Int) {
        val item = dragState.draggedItem ?: return
        val s = state.value

        val occupiedKeys = buildSet {
            s.pages.forEach { (_, items) ->
                items.forEach { add("${it.page}:${it.gridX}:${it.gridY}") }
            }
            s.dock.forEach { add("${it.page}:${it.gridX}:${it.gridY}") }
        }

        val target = dragState.findDropTarget(
            x = dragState.dragX,
            y = dragState.dragY,
            occupiedCells = occupiedKeys,
            draggedItemId = item.id,
        )

        viewModelScope.launch {
            when (target) {
                null -> { /* no-op: item snaps back */ }

                DropTarget.Trash -> {
                    homeItemDao.deleteById(item.id)
                }

                is DropTarget.DockSlot -> {
                    val existing = s.dock.find { it.gridX == target.slot }
                    if (existing != null && existing.id != item.id) {
                        // swap
                        homeItemDao.update(existing.toEntity().copy(
                            page = item.page, gridX = item.gridX, gridY = item.gridY
                        ))
                    }
                    homeItemDao.update(item.toEntity().copy(page = -1, gridX = target.slot, gridY = 0))
                }

                is DropTarget.MergeWithItem -> { /* handled via GridCell path */ }

                is DropTarget.GridCell -> {
                    val pg = target.page
                    val col = target.col
                    val row = target.row

                    // Check if target cell is occupied by a different item
                    val occupant = (s.pages[pg] ?: emptyList()).find {
                        it.gridX == col && it.gridY == row && it.id != item.id
                    }

                    if (occupant != null && item is HomeItem.Shortcut && occupant is HomeItem.Shortcut) {
                        // Create folder
                        createFolder(item, occupant, pg, col, row)
                    } else if (occupant == null) {
                        homeItemDao.update(item.toEntity().copy(page = pg, gridX = col, gridY = row))
                    }
                    // else occupied by widget/folder ref or same item → no-op
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page management
    // ─────────────────────────────────────────────────────────────────────────
    fun addPage() {
        // Page is implicit — items placed on the new index create it
        // Just ensure we have at least one extra slot
    }

    fun removeEmptyPage(page: Int) {
        val s = state.value
        val hasItems = (s.pages[page]?.isNotEmpty() == true)
        if (hasItems) return
        // Shift items on later pages down by one
        viewModelScope.launch {
            val toShift = s.pages.entries
                .filter { it.key > page }
                .flatMap { it.value }
            toShift.forEach { item ->
                homeItemDao.update(item.toEntity().copy(page = item.page - 1))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Folder operations
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun createFolder(
        item1: HomeItem.Shortcut,
        item2: HomeItem.Shortcut,
        page: Int,
        col: Int,
        row: Int,
    ) {
        val folder = Folder(
            name = "Folder",
            items = listOf(item1.componentName, item2.componentName),
        )
        val folderId = folderDao.insert(folder.toEntity())
        // Replace item2's slot with a FolderRef
        homeItemDao.deleteById(item1.id)
        homeItemDao.update(
            item2.toEntity().copy(
                type = "folder",
                payload = """{"folderId":"$folderId"}""",
                page = page, gridX = col, gridY = row,
            )
        )
    }

    fun addAppToFolder(folderId: Long, componentName: ComponentName) {
        viewModelScope.launch {
            val entity = folderDao.getById(folderId) ?: return@launch
            val folder = entity.toDomain()
            if (componentName in folder.items) return@launch
            folderDao.update(folder.copy(items = folder.items + componentName).toEntity())
        }
    }

    fun removeAppFromFolder(folderId: Long, componentName: ComponentName) {
        viewModelScope.launch {
            val entity = folderDao.getById(folderId) ?: return@launch
            val folder = entity.toDomain()
            val newItems = folder.items.filter { it != componentName }
            if (newItems.size <= 1) {
                // Dissolve folder — place remaining app back on home
                val folderRef = state.value.pages.values.flatten()
                    .firstOrNull { it is HomeItem.FolderRef && (it as HomeItem.FolderRef).folderId == folderId }
                if (folderRef != null && newItems.isNotEmpty()) {
                    val lastApp = newItems.first()
                    homeItemDao.update(
                        folderRef.toEntity().copy(
                            type = "shortcut",
                            payload = buildString {
                                append("{\"pkg\":\"${lastApp.packageName}\"")
                                append(",\"cls\":\"${lastApp.className}\"")
                                append(",\"user\":\"0\"}")
                            }
                        )
                    )
                } else if (folderRef != null) {
                    homeItemDao.deleteById(folderRef.id)
                }
                folderDao.delete(folder.toEntity())
            } else {
                folderDao.update(folder.copy(items = newItems).toEntity())
            }
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        viewModelScope.launch {
            val entity = folderDao.getById(folderId) ?: return@launch
            folderDao.update(entity.copy(name = name))
        }
    }

    fun openFolder(folderId: Long) {
        val s = state.value
        val folder = s.folders[folderId] ?: return
        _folderSheet.value = FolderSheetState(folder = folder, apps = s.apps)
    }

    fun closeFolder() { _folderSheet.value = null }

    // ─────────────────────────────────────────────────────────────────────────
    // Context menu / shortcuts
    // ─────────────────────────────────────────────────────────────────────────
    fun showContextMenu(item: HomeItem.Shortcut, app: App, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val shortcuts = runCatching {
                val launcherApps = context.getSystemService(LauncherApps::class.java)!!
                val userManager = context.getSystemService(UserManager::class.java)!!
                val userHandle: UserHandle =
                    userManager.getUserForSerialNumber(app.userSerial) ?: return@runCatching emptyList()
                val query = LauncherApps.ShortcutQuery()
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                    .setPackage(app.packageName)
                launcherApps.getShortcuts(query, userHandle)?.take(5) ?: emptyList()
            }.getOrDefault(emptyList())
            _contextMenu.value = ContextMenuState(item = item, app = app, shortcuts = shortcuts)
        }
    }

    fun dismissContextMenu() { _contextMenu.value = null }

    fun launchShortcut(context: Context, shortcut: ShortcutInfo, userHandle: UserHandle) {
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)!!
            launcherApps.startShortcut(shortcut, null, null)
        }
        dismissContextMenu()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item CRUD
    // ─────────────────────────────────────────────────────────────────────────
    fun addShortcut(app: App, page: Int, gridX: Int, gridY: Int) {
        viewModelScope.launch {
            val item = HomeItem.Shortcut(
                id = 0, page = page, gridX = gridX, gridY = gridY,
                componentName = app.componentName, userSerial = app.userSerial,
            )
            homeItemDao.insert(item.toEntity())
        }
    }

    fun removeItem(item: HomeItem) {
        viewModelScope.launch { homeItemDao.deleteById(item.id) }
    }

    fun renameApp(app: App, newLabel: String) {
        viewModelScope.launch {
            appOverrideDao.upsert(
                AppOverrideEntity(
                    componentName = app.componentName.flattenToString(),
                    userSerial = app.userSerial,
                    customLabel = newLabel.ifBlank { null },
                )
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    class Factory(
        private val homeItemDao: HomeItemDao,
        private val folderDao: FolderDao,
        private val appOverrideDao: AppOverrideDao,
        private val packageMonitor: PackageMonitor,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(homeItemDao, folderDao, appOverrideDao, packageMonitor, settingsRepository) as T
    }
}
