package com.littlehelper.data

import com.littlehelper.MemoryCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOperationExecutorClearAllTest {

    @Test
    fun execute_clearAll_returnsPendingWithoutWriting() = runBlocking {
        val dao = FakeMemoryDao()
        val executor = MemoryOperationExecutor(MemoryRepository(dao))

        val result = executor.execute(listOf(MemoryOperation(op = "clear_all")))

        assertTrue(result.pendingClearAll)
        assertEquals(0, result.applied)
        assertTrue(dao.allRecords.isEmpty())
    }

    private class FakeMemoryDao : MemoryDao {
        val allRecords = mutableListOf<MemoryRecord>()

        override suspend fun insert(record: MemoryRecord): Long {
            val id = (allRecords.maxOfOrNull { it.id } ?: 0L) + 1
            allRecords.add(record.copy(id = id))
            return id
        }

        override suspend fun update(record: MemoryRecord) {
            val index = allRecords.indexOfFirst { it.id == record.id }
            if (index >= 0) allRecords[index] = record
        }

        override suspend fun delete(record: MemoryRecord) {
            allRecords.removeAll { it.id == record.id }
        }

        override suspend fun getAll(): List<MemoryRecord> = allRecords.toList()

        override suspend fun getById(id: Long): MemoryRecord? = allRecords.firstOrNull { it.id == id }

        override suspend fun getByCategory(category: String, limit: Int): List<MemoryRecord> =
            allRecords.filter { it.category == category }.take(limit)

        override suspend fun searchByKeyword(keyword: String, limit: Int): List<MemoryRecord> =
            allRecords.filter { it.summary.contains(keyword) }.take(limit)

        override suspend fun getWithEventDate(limit: Int): List<MemoryRecord> =
            allRecords.filter { !it.eventDate.isNullOrBlank() }.take(limit)

        override suspend fun getLatestByCategory(category: String): MemoryRecord? =
            allRecords.firstOrNull { it.category == category }

        override suspend fun getLatest(): MemoryRecord? = allRecords.maxByOrNull { it.createdAt }

        override suspend fun deleteAll() {
            allRecords.clear()
        }

        override suspend fun resetDailyTodoDoneFlags(): Int = 0

        override suspend fun searchIncompleteTodos(keyword: String, limit: Int): List<MemoryRecord> =
            emptyList()

        override fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<MemoryRecord>> = kotlinx.coroutines.flow.flowOf(allRecords.toList())
    }
}
