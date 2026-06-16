package com.littlehelper.network

import com.littlehelper.ChatMessage
import com.littlehelper.FollowUpContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryBuilderTest {

    @Test
    fun toApiMessages_includesUserAndAssistantTurns() {
        val messages = listOf(
            ChatMessage.assistant("您好，请按住说话。"),
            ChatMessage.user("夏子涵的生日是6月8号"),
            ChatMessage.assistant("是杭州的杭不是涵养的涵吗？"),
            ChatMessage.user("是杭州的杭不是涵养的涵")
        )

        val apiMessages = ChatHistoryBuilder.toApiMessages(messages)
        assertEquals(4, apiMessages.size)
        assertEquals("assistant", apiMessages[0].role)
        assertEquals("user", apiMessages[1].role)
        assertEquals("夏子涵的生日是6月8号", apiMessages[1].content)
    }

    @Test
    fun toApiMessages_excludesPartialAndStatusMessages() {
        val messages = listOf(
            ChatMessage.user("记一下", isPartial = true),
            ChatMessage.assistant("正在思考…"),
            ChatMessage.user("明天和李总谈项目")
        )

        val apiMessages = ChatHistoryBuilder.toApiMessages(messages)
        assertEquals(1, apiMessages.size)
        assertEquals("明天和李总谈项目", apiMessages[0].content)
    }

    @Test
    fun toApiMessages_stitchesSaveConfirmationWhenFollowUpContextSave() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("好的")
        )

        val apiMessages = ChatHistoryBuilder.toApiMessages(messages, FollowUpContext.SAVE)
        assertEquals(3, apiMessages.size)
        assertEquals(
            "[系统强制指令]：用户已明确对你的上一次询问点击了确认。请不要有任何废话和寒暄，立刻根据上文内容（如『我后天上午...』）生成包含 1~N 个 insert 的 `DB_OPS` 结构化 JSON 块执行写库！禁止回复『没有任何记录』或『目前还没有内容被记录』！\n是的，请帮我记下上文提到的：后天去医院",
            apiMessages.last().content
        )
    }

    @Test
    fun toApiMessages_doesNotStitchWithoutSaveFollowUpContext() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("好的")
        )

        val apiMessages = ChatHistoryBuilder.toApiMessages(messages, FollowUpContext.NONE)
        assertEquals("好的", apiMessages.last().content)
    }
}
