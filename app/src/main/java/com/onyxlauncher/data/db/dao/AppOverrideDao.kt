package com.onyxlauncher.data.db.dao

import androidx.room.*
import com.onyxlauncher.data.db.entity.AppOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppOverrideDao {
    @Query("SELECT * FROM app_overrides")
    fun observeAll(): Flow<List<AppOverrideEntity>>

    @Query("SELECT * FROM app_overrides WHERE component_name = :componentName AND user_serial = :userSerial LIMIT 1")
    suspend fun get(componentName: String, userSerial: Long): AppOverrideEntity?

    @Query("SELECT * FROM app_overrides")
    suspend fun getAll(): List<AppOverrideEntity>

    @Query("DELETE FROM app_overrides")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: AppOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(overrides: List<AppOverrideEntity>)

    @Query("DELETE FROM app_overrides WHERE component_name = :componentName AND user_serial = :userSerial")
    suspend fun delete(componentName: String, userSerial: Long)
}
