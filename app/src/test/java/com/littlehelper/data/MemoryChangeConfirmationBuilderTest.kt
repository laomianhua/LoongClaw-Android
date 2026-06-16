package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryChangeConfirmationBuilderTest {

    @Test
    fun build_singleInsert_usesSummary() {
        val result = MemoryOperationExecutor.ExecutionResult(
            applied = 1,
            insertedIds = listOf(3L)
        )
        val records = mapOf(
            3L to MemoryRecord(
                id = 3L,
                summary = "明天上午10点与八达通联系",
                rawText = "test",
                category = "schedule"
            )
        )
        assertEquals(
            "好的，已经记下：明天上午10点与八达通联系。",
            MemoryChangeConfirmationBuilder.build(result, records)
        )
    }

    @Test
    fun build_multipleDeletes() {
        val result = MemoryOperationExecutor.ExecutionResult(
            applied = 2,
            deletedIds = listOf(1L, 2L)
        )
        assertEquals(
            "好的，已经删除 2 条记录。",
            MemoryChangeConfirmationBuilder.build(result, emptyMap())
        )
    }

    @Test
    fun build_clearAllPending_returnsNull() {
        val result = MemoryOperationExecutor.ExecutionResult(pendingClearAll = true)
        assertNull(MemoryChangeConfirmationBuilder.build(result, emptyMap()))
    }
}
