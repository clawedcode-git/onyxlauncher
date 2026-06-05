package com.onyxlauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val items: String,               // JSON array of "pkg/activity"
    @ColumnInfo(name = "custom_icon_key") val customIconKey: String? = null,
)
