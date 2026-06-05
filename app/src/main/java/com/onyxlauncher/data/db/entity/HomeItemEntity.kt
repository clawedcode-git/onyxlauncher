package com.onyxlauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single flat table for all home/dock items.
 * type: "shortcut" | "folder" | "widget"
 * payload: JSON blob for type-specific fields.
 */
@Entity(tableName = "home_items")
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,          // -1 = dock
    @ColumnInfo(name = "grid_x") val gridX: Int,
    @ColumnInfo(name = "grid_y") val gridY: Int,
    val type: String,
    val payload: String,    // JSON
)
