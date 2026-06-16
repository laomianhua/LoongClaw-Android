package com.littlehelper.network

import com.littlehelper.ChatMessage
import com.littlehelper.FollowUpContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveConfirmationHelperTest {

    @Test
    fun isShortAffirmation_recognizesCommonPhrases() {
        assertTrue(SaveConfirmationHelper.isShortAffirmation("是的"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("好的"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("对"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("记上吧"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("要"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("嗯"))
        assertTrue(SaveConfirmationHelper.isShortAffirmation("是的。"))
    }

    @Test
    fun isShortAffirmation_rejectsLongOrSubstantiveUtterances() {
        assertFalse(SaveConfirmationHelper.isShortAffirmation("后天去医院"))
        assertFalse(SaveConfirmationHelper.isShortAffirmation("下午两点和李总开会"))
        assertFalse(SaveConfirmationHelper.isShortAffirmation(""))
    }

    @Test
    fun findPriorUserIntent_returnsUserBeforeSaveFollowUpAssistant() {
        val messages = listOf(
            ChatMessage.assistant("您好，请按住说话。"),
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("好的")
        )

        assertEquals("后天去医院", SaveConfirmationHelper.findPriorUserIntent(messages))
    }

    @Test
    fun findPriorUserIntent_skipsStatusMessages() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("正在思考…"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("是的")
        )

        assertEquals("后天去医院", SaveConfirmationHelper.findPriorUserIntent(messages))
    }

    @Test
    fun findPriorUserIntent_returnsNullWhenNoSaveInvitation() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("好的，已记下。"),
            ChatMessage.user("好的")
        )

        assertNull(SaveConfirmationHelper.findPriorUserIntent(messages))
    }

    @Test
    fun stitchSaveConfirmation_rewritesLastUserForApiOnly() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("好的")
        )

        val stitched = SaveConfirmationHelper.stitchSaveConfirmation(
            messages,
            FollowUpContext.SAVE
        )

        assertEquals("好的", messages.last().text)
        assertEquals(
            "[系统强制指令]：用户已明确对你的上一次询问点击了确认。请不要有任何废话和寒暄，立刻根据上文内容（如『我后天上午...』）生成包含 1~N 个 insert 的 `DB_OPS` 结构化 JSON 块执行写库！禁止回复『没有任何记录』或『目前还没有内容被记录』！\n是的，请帮我记下上文提到的：后天去医院",
            stitched.last { it.role == com.littlehelper.ChatRole.USER }.text
        )
    }

    @Test
    fun stitchSaveConfirmation_noOpWhenNotSaveContext() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("好的")
        )

        val result = SaveConfirmationHelper.stitchSaveConfirmation(
            messages,
            FollowUpContext.NONE
        )

        assertEquals(messages, result)
    }

    @Test
    fun stitchSaveConfirmation_noOpWhenNotAffirmation() {
        val messages = listOf(
            ChatMessage.user("后天去医院"),
            ChatMessage.assistant("需要我现在帮您记下吗？"),
            ChatMessage.user("下午两点")
        )

        val result = SaveConfirmationHelper.stitchSaveConfirmation(
            messages,
            FollowUpContext.SAVE
        )

        assertEquals(messages, result)
    }
}
