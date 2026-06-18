package com.onyxlauncher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyxlauncher.data.backup.BackupManager
import com.onyxlauncher.data.datastore.SettingsRepository
import com.onyxlauncher.domain.model.*
import com.onyxlauncher.onyxApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) : AndroidViewModel(app) {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    // ── layout ───────────────────────────────────────────────────────────────
    fun setColumns(v: Int) = update { homeColumns = v }
    fun setRows(v: Int) = update { homeRows = v }
    fun setDockSlots(v: Int) = update { dockSlots = v }
    fun setIconSize(v: Int) = update { iconSizeDp = v }
    fun setShowLabels(v: Boolean) = update { showLabels = v }
    fun setLabelSize(v: Int) = update { labelSizeSp = v }
    fun setPageIndicator(v: PageIndicatorStyle) = update { pageIndicator = v.ordinal }
    fun setFolderStyle(v: FolderStyle) = update { folderStyle = v.ordinal }
    fun setAnimationScale(v: Float) = update { animationScale = v }

    // ── appearance ─────────────────────────────────────────────────────────
    fun setThemeMode(v: ThemeMode) = update { themeMode = v.ordinal }
    fun setDynamicColor(v: Boolean) = update { useDynamicColor = v }

    // ── gestures ─────────────────────────────────────────────────────────────
    fun setSwipeUp(v: GestureAction) = update { swipeUp = v.ordinal }
    fun setSwipeDown(v: GestureAction) = update { swipeDown = v.ordinal }
    fun setDoubleTap(v: GestureAction) = update { doubleTap = v.ordinal }
    fun setTwoFingerSwipeUp(v: GestureAction) = update { twoFingerSwipeUp = v.ordinal }

    // ── wallpaper ─────────────────────────────────────────────────────────────
    fun setUseTimeOfDayColor(v: Boolean) = update { useTimeOfDayColor = v }
    fun setUseUsageSignal(v: Boolean) = update { useUsageSignal = v }
    fun setAutoRefresh(v: Boolean) = update { autoRefresh = v }

    // ── misc ──────────────────────────────────────────────────────────────────
    fun setShowStatusBar(v: Boolean) = update { showStatusBar = v }
    fun setShowSearchOnHome(v: Boolean) = update { showSearchOnHome = v }

    private inline fun update(crossinline block: com.onyxlauncher.data.datastore.SettingsProto.Builder.() -> Unit) {
        viewModelScope.launch { settingsRepository.update { block() } }
    }

    // ── backup / restore ──────────────────────────────────────────────────────
    suspend fun exportTo(uri: android.net.Uri): Result<Unit> = backupManager.export(uri)
    suspend fun importFrom(uri: android.net.Uri): Result<Unit> = backupManager.import(uri)

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val a = app.onyxApp
            return SettingsViewModel(app, a.settingsRepository, a.backupManager) as T
        }
    }
}
