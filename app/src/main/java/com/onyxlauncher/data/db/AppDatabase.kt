package com.onyxlauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.onyxlauncher.data.db.dao.*
import com.onyxlauncher.data.db.entity.*

@Database(
    entities = [
        HomeItemEntity::class,
        FolderEntity::class,
        AppOverrideEntity::class,
        WallpaperPresetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeItemDao(): HomeItemDao
    abstract fun folderDao(): FolderDao
    abstract fun appOverrideDao(): AppOverrideDao
    abstract fun wallpaperPresetDao(): WallpaperPresetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onyx_launcher.db",
                ).build().also { INSTANCE = it }
            }
    }
}
