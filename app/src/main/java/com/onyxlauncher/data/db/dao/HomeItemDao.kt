package com.onyxlauncher.data.db.dao

import androidx.room.*
import com.onyxlauncher.data.db.entity.HomeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items ORDER BY page, grid_y, grid_x")
    fun observeAll(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items WHERE page = :page ORDER BY grid_y, grid_x")
    fun observePage(page: Int): Flow<List<HomeItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeItemEntity>)

    @Update
    suspend fun update(item: HomeItemEntity)

    @Delete
    suspend fun delete(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT MAX(page) FROM home_items WHERE page >= 0")
    suspend fun maxPage(): Int?

    @Query("SELECT * FROM home_items")
    suspend fun getAll(): List<HomeItemEntity>

    @Query("DELETE FROM home_items")
    suspend fun clear()

    @Query("SELECT * FROM home_items WHERE page = :page AND grid_x = :col AND grid_y = :row LIMIT 1")
    suspend fun getAt(page: Int, col: Int, row: Int): HomeItemEntity?

    @Query("SELECT * FROM home_items WHERE page = -1 AND grid_x = :slot LIMIT 1")
    suspend fun getDockAt(slot: Int): HomeItemEntity?

    @Transaction
    suspend fun move(item: HomeItemEntity, newPage: Int, newX: Int, newY: Int) {
        update(item.copy(page = newPage, gridX = newX, gridY = newY))
    }
}
