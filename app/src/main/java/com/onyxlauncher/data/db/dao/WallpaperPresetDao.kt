package com.onyxlauncher.data.db.dao

import androidx.room.*
import com.onyxlauncher.data.db.entity.WallpaperPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperPresetDao {
    @Query("SELECT * FROM wallpaper_presets ORDER BY created_at DESC")
    fun observeAll(): Flow<List<WallpaperPresetEntity>>

    @Query("SELECT * FROM wallpaper_presets WHERE is_favorite = 1 ORDER BY created_at DESC")
    fun observeFavorites(): Flow<List<WallpaperPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: WallpaperPresetEntity): Long

    @Update
    suspend fun update(preset: WallpaperPresetEntity)

    @Query("DELETE FROM wallpaper_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
