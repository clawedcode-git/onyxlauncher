package com.onyxlauncher.domain.model

data class Settings(
    val homeColumns: Int = 5,
    val homeRows: Int = 5,
    val dockSlots: Int = 5,
    val iconSizeDp: Int = 56,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 11,
    val pageIndicator: PageIndicatorStyle = PageIndicatorStyle.DOTS,
    val folderStyle: FolderStyle = FolderStyle.ROUND,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,

    val activeIconPack: String? = null,

    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.NONE,
    val twoFingerSwipeUp: GestureAction = GestureAction.NONE,
    val gestureCustomApp: String? = null,

    val useTimeOfDayColor: Boolean = true,
    val useUsageSignal: Boolean = false,
    val autoRefresh: Boolean = false,
    val autoRefreshTrigger: RefreshTrigger = RefreshTrigger.DAILY,
    val activePresetId: Long? = null,

    val animationScale: Float = 1f,
    val showStatusBar: Boolean = true,
    val showSearchOnHome: Boolean = false,
    val homeSeeded: Boolean = false,
)

enum class ThemeMode { LIGHT, DARK, SYSTEM, AMOLED }
enum class PageIndicatorStyle { DOTS, LINES, NONE }
enum class FolderStyle { ROUND, SQUARE }
enum class RefreshTrigger { DAILY, USAGE_CHANGE, BOTH }

enum class GestureAction {
    NONE,
    OPEN_DRAWER,
    OPEN_SEARCH,
    NOTIFICATIONS,
    LOCK_SCREEN,
    CUSTOM_APP,
}
