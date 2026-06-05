package com.onyxlauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Per-app user overrides: hidden flag, custom label, custom icon. */
@Entity(tableName = "app_overrides", primaryKeys = ["component_name", "user_serial"])
data class AppOverrideEntity(
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "custom_label") val customLabel: String? = null,
    @ColumnInfo(name = "custom_icon_key") val customIconKey: String? = null,
)
