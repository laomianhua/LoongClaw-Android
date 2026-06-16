package com.littlehelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(record: MemoryRecord): Long

    @Update
    suspend fun update(record: MemoryRecord)

    @Delete
    suspend fun delete(record: MemoryRecord)

    @Query("SELECT * FROM memories ORDER BY created_at DESC")
    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<MemoryRecord>>

    @Query("SELECT * FROM memories ORDER BY created_at DESC")
    suspend fun getAll(): List<MemoryRecord>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryRecord?

    @Query(
        """
        SELECT * FROM memories
        WHERE category = :category
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    suspend fun getByCategory(category: String, limit: Int = 20): List<MemoryRecord>

    @Query(
        """
        SELECT * FROM memories
        WHERE summary LIKE '%' || :keyword || '%'
           OR raw_text LIKE '%' || :keyword || '%'
           OR person LIKE '%' || :keyword || '%'
           OR person_pinyin LIKE '%' || :keyword || '%'
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    suspend fun searchByKeyword(keyword: String, limit: Int = 20): List<MemoryRecord>

    @Query(
        """
        SELECT * FROM memories
        WHERE event_date IS NOT NULL
          AND event_date != ''
        ORDER BY event_date DESC, created_at DESC
        LIMIT :limit
        """
    )
    suspend fun getWithEventDate(limit: Int = 50): List<MemoryRecord>

    @Query(
        """
        SELECT * FROM memories
        WHERE category = :category
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByCategory(category: String): MemoryRecord?

    @Query("SELECT * FROM memories ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): MemoryRecord?

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    /** 每日 0 点：重置「循环提醒 + 待办」的 done 标记（不含生日类年度循环）。 */
    @Query(
        """
        UPDATE memories SET done = 0
        WHERE type = 'todo'
          AND is_recurring = 1
          AND done = 1
          AND event_time IS NOT NULL
          AND event_time != ''
          AND category != 'birthday'
        """
    )
    suspend fun resetDailyTodoDoneFlags(): Int
}
