package com.littlehelper.network

import com.littlehelper.domain.todo.NotebookAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoProtocolParserTest {

    @Test
    fun parse_queryTodoAction_withPayload() {
        val content = """
            好的，正在帮您查看您的吃药任务...
            ___DB_OPS_START___
            {
              "status": "success",
              "intent_route": "NOTEBOOK",
              "action": "QUERY_TODO",
              "payload": { "query_keyword": "吃药" },
              "operations": []
            }
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        val dbOps = result.dbOpsPayload
        assertNotNull(dbOps)
        assertEquals(NotebookAction.QUERY_TODO, dbOps?.action)
        assertEquals("success", dbOps?.status)
        assertEquals("吃药", dbOps?.todoPayload?.queryKeyword)
        assertTrue(LlmResponseValidator.hasActionableDbOps(result))
    }

    @Test
    fun parse_updateTodoStatusAction_withTodoId() {
        val content = """
            好勒，已经记为做完啦！
            ___DB_OPS_START___
            {
              "status": "success",
              "intent_route": "NOTEBOOK",
              "action": "UPDATE_TODO_STATUS",
              "payload": { "todo_id": 101, "status": "COMPLETED" },
              "operations": []
            }
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        val dbOps = result.dbOpsPayload
        assertNotNull(dbOps)
        assertEquals(101L, dbOps?.todoPayload?.todoId)
        assertEquals("COMPLETED", dbOps?.todoPayload?.status)
    }
}
