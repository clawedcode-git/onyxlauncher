package com.onyxlauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.onyxlauncher.domain.model.GestureAction
import com.onyxlauncher.domain.model.FolderStyle
import com.onyxlauncher.domain.model.PageIndicatorStyle
import com.onyxlauncher.domain.model.RefreshTrigger
import com.onyxlauncher.domain.model.Settings
import com.onyxlauncher.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<SettingsProto> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { it.toDomain() }

    suspend fun update(block: SettingsProto.Builder.() -> Unit) {
        context.settingsDataStore.updateData { current ->
            current.toBuilder().apply(block).build()
        }
    }

    private fun SettingsProto.toDomain() = Settings(
        homeColumns = if (homeColumns == 0) 5 else homeColumns,
        homeRows = if (homeRows == 0) 5 else homeRows,
        dockSlots = if (dockSlots == 0) 5 else dockSlots,
        iconSizeDp = if (iconSizeDp == 0) 56 else iconSizeDp,
        showLabels = showLabels,
        labelSizeSp = if (labelSizeSp == 0) 11 else labelSizeSp,
        pageIndicator = PageIndicatorStyle.entries.getOrElse(pageIndicator) { PageIndicatorStyle.DOTS },
        folderStyle = FolderStyle.entries.getOrElse(folderStyle) { FolderStyle.ROUND },
        themeMode = ThemeMode.entries.getOrElse(themeMode) { ThemeMode.SYSTEM },
        useDynamicColor = useDynamicColor,
        activeIconPack = activeIconPack.ifEmpty { null },
        swipeUp = GestureAction.entries.getOrElse(swipeUp) { GestureAction.OPEN_DRAWER },
        swipeDown = GestureAction.entries.getOrElse(swipeDown) { GestureAction.NOTIFICATIONS },
        doubleTap = GestureAction.entries.getOrElse(doubleTap) { GestureAction.NONE },
        twoFingerSwipeUp = GestureAction.entries.getOrElse(twoFingerSwipeUp) { GestureAction.NONE },
        gestureCustomApp = gestureCustomApp.ifEmpty { null },
        useTimeOfDayColor = useTimeOfDayColor,
        useUsageSignal = useUsageSignal,
        autoRefresh = autoRefresh,
        autoRefreshTrigger = RefreshTrigger.entries.getOrElse(autoRefreshTrigger) { RefreshTrigger.DAILY },
        activePresetId = if (activePresetId == 0L) null else activePresetId,
        animationScale = if (animationScale == 0f) 1f else animationScale,
        showStatusBar = showStatusBar,
        showSearchOnHome = showSearchOnHome,
        homeSeeded = homeSeeded,
    )
}
