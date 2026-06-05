package com.onyxlauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpaper_presets")
data class WallpaperPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val seed: Long,
    val style: String,
    @ColumnInfo(name = "locked_palette") val lockedPalette: String? = null, // JSON int array
    @ColumnInfo(name = "is_live") val isLive: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
