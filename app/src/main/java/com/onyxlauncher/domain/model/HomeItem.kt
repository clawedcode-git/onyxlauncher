package com.onyxlauncher.domain.model

import android.content.ComponentName

sealed interface HomeItem {
    val id: Long
    val page: Int       // -1 = dock
    val gridX: Int
    val gridY: Int

    data class Shortcut(
        override val id: Long,
        override val page: Int,
        override val gridX: Int,
        override val gridY: Int,
        val componentName: ComponentName,
        val userSerial: Long,
    ) : HomeItem

    data class FolderRef(
        override val id: Long,
        override val page: Int,
        override val gridX: Int,
        override val gridY: Int,
        val folderId: Long,
    ) : HomeItem

    data class WidgetRef(
        override val id: Long,
        override val page: Int,
        override val gridX: Int,
        override val gridY: Int,
        val appWidgetId: Int,
        val spanX: Int = 1,
        val spanY: Int = 1,
    ) : HomeItem
}
