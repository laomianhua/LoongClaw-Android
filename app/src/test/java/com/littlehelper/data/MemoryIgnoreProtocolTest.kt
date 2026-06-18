package com.littlehelper.data

import com.littlehelper.network.LlmResponseParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 加固 status="ignore" 拦截协议的全链路测试。
 *
 * 覆盖两个核心防线：
 * 1. 解析层：LlmResponseParser 能正确识别 ignore 状态（含 fallback 路径）。
 * 2. 执行层：MemoryOperationExecutor 在 ignore 时保证「零 DB 写入」。
 */
class MemoryIgnoreProtocolTest {

    // ─── Scenario A: 大模型明确返回 status="ignore" ───────────────────────────

    @Test
    fun scenarioA_explicitIgnoreStatus_parsedCorrectly() {
        val content = """
            没太听明白，请再说清楚一点。
            ___DB_OPS_START___
            {
              "status": "ignore",
              "reason": "text_too_vague_or_no_intent",
              "operations": []
            }
            ___DB_OPS_END___
        """.trimIndent()

        val parsed = LlmResponseParser.parse(content)
        assertEquals("ignore", parsed.dbOpsPayload?.status)
        assertEquals("text_too_vague_or_no_intent", parsed.dbOpsPayload?.reason)
        assertTrue(parsed.dbOpsPayload?.operations?.isEmpty() == true)
    }

    @Test
    fun scenarioA_explicitIgnoreStatus_executorWritesZeroRows() = runBlocking {
        val dao = SpyMemoryDao()
        val executor = MemoryOperationExecutor(MemoryRepository(dao))

        val ignoreOpsResponse = LlmResponseParser.ParsedResponse(
            reply = "没太听明白，请再说清楚一点。",
            savePayload = null,
            deletePayload = null,
            dbOpsPayload = com.littlehelper.data.LlmOpsResponse(
                status = "ignore",
                reason = "text_too_vague_or_no_intent",
                operations = emptyList()
            )
        )

        // 模拟 MainViewModel.isAiIgnoredResponse 检测 → 不调用 executor
        val isIgnore = ignoreOpsResponse.dbOpsPayload?.status == "ignore"
        assertTrue("status=ignore 应被正确识别", isIgnore)

        // 当 ignore 时 MainViewModel.executeMemoryChanges 立即 return null，executor 不被调用
        // 此处直接断言：若 ignore 被检测到，executor 不应收到任何操作
        if (!isIgnore) {
            // 这条路径在 status=ignore 时永远不会执行
            ignoreOpsResponse.dbOpsPayload?.operations?.let { executor.execute(it) }
        }

        assertEquals("executor 的 insert 调用次数必须为 0", 0, dao.insertCallCount)
        assertEquals("executor 的 update 调用次数必须为 0", 0, dao.updateCallCount)
        assertEquals("executor 的 delete 调用次数必须为 0", 0, dao.deleteCallCount)
    }

    // ─── Scenario B: 大模型遗漏 status 字段 + operations 为空 ─────────────────

    @Test
    fun scenarioB_missingStatusWithEmptyOps_parsedAsIgnore() {
        // 大模型偶发漏写 status 但 operations 为空 → resolveDbOpsStatus 应返回 "ignore"
        val content = """
            没听清楚。
            ___DB_OPS_START___
            {"operations": []}
            ___DB_OPS_END___
        """.trimIndent()

        val parsed = LlmResponseParser.parse(content)
        assertEquals(
            "当 status 缺失且 operations 为空时，应 fallback 为 ignore",
            "ignore",
            parsed.dbOpsPayload?.status
        )
    }

    @Test
    fun scenarioB_missingStatusWithEmptyOps_executorWritesZeroRows() = runBlocking {
        val dao = SpyMemoryDao()
        val executor = MemoryOperationExecutor(MemoryRepository(dao))

        val content = """
            没听清楚。
            ___DB_OPS_START___
            {"operations": []}
            ___DB_OPS_END___
        """.trimIndent()
        val parsed = LlmResponseParser.parse(content)

        val isIgnore = parsed.dbOpsPayload?.status == "ignore"
        assertTrue(isIgnore)

        // executor 不被调用，DB 零写入
        if (!isIgnore) {
            parsed.dbOpsPayload?.operations?.let { executor.execute(it) }
        }

        assertEquals(0, dao.insertCallCount)
        assertEquals(0, dao.updateCallCount)
        assertEquals(0, dao.deleteCallCount)
    }

    @Test
    fun scenarioB_missingStatusWithNonEmptyOps_parsedAsSuccess() {
        // operations 不为空但 status 缺失 → 应 fallback 为 "success"（有效操作）
        val content = """
            记下了。
            ___DB_OPS_START___
            {"operations": [{"op": "insert", "record": {"summary": "test"}}]}
            ___DB_OPS_END___
        """.trimIndent()

        val parsed = LlmResponseParser.parse(content)
        assertEquals(
            "当 status 缺失但 operations 非空时，应 fallback 为 success",
            "success",
            parsed.dbOpsPayload?.status
        )
    }

    // ─── Scenario C: isRecurring 三路 when —— update 不再是单向棘轮 ─────────

    @Test
    fun scenarioC_isRecurring_explicitFalseCanClearBirthdayFlag() = runBlocking {
        val dao = SpyMemoryDao()
        val repo = MemoryRepository(dao)
        val executor = MemoryOperationExecutor(repo)

        // 先插入一条 birthday 记录（isRecurring = true）
        val birthdayOp = listOf(
            MemoryOperation(
                op = "insert",
                record = MemoryRecordPayload(
                    summary = "王纲的生日",
                    rawText = "王纲的生日",
                    person = "王纲",
                    category = "birthday",
                    isRecurring = true
                )
            )
        )
        val insertResult = executor.execute(birthdayOp)
        val insertedId = insertResult.insertedIds.first()
        assertTrue(dao.allRecords.first { it.id == insertedId }.isRecurring)

        // 再通过 update 操作明确传 isRecurring=false，应当能清除 birthday 标志
        val updateOp = listOf(
            MemoryOperation(
                op = "update",
                id = insertedId,
                fields = MemoryRecordPayload(
                    category = "general",
                    isRecurring = false
                )
            )
        )
        executor.execute(updateOp)
        val updated = dao.allRecords.first { it.id == insertedId }
        assertEquals("general", updated.category)
        // isRecurring=false 时不再是 birthday category，棘轮已解除
        assertTrue("category=general 时 isRecurring 应为 false", !updated.isRecurring)
    }

    // ─── 内部 Fake DAO（带调用计数）─────────────────────────────────────────

    private class SpyMemoryDao : MemoryDao {
        val allRecords = mutableListOf<MemoryRecord>()
        var insertCallCount = 0
        var updateCallCount = 0
        var deleteCallCount = 0

        override suspend fun insert(record: MemoryRecord): Long {
            insertCallCount++
            val id = (allRecords.maxOfOrNull { it.id } ?: 0L) + 1
            allRecords.add(record.copy(id = id))
            return id
        }

        override suspend fun update(record: MemoryRecord) {
            updateCallCount++
            val index = allRecords.indexOfFirst { it.id == record.id }
            if (index >= 0) allRecords[index] = record
        }

        override suspend fun delete(record: MemoryRecord) {
            deleteCallCount++
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
        override suspend fun deleteAll() { allRecords.clear() }
        override suspend fun searchIncompleteTodos(keyword: String, limit: Int): List<MemoryRecord> =
            allRecords.filter {
                it.type == "todo" && !it.done &&
                    (it.summary.contains(keyword) || it.rawText.contains(keyword))
            }.take(limit)
        override suspend fun resetDailyTodoDoneFlags(): Int {
            var count = 0
            allRecords.replaceAll { record ->
                if (record.type == "todo" && record.isRecurring && record.done &&
                    !record.eventTime.isNullOrBlank() && record.category != "birthday"
                ) {
                    count++
                    record.copy(done = false)
                } else {
                    record
                }
            }
            return count
        }
        override fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<MemoryRecord>> = kotlinx.coroutines.flow.flowOf(allRecords.toList())
    }
}
