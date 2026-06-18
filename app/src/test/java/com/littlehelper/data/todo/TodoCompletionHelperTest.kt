package com.littlehelper.data.todo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoCompletionHelperTest {

    @Test
    fun looksLikeCompletion_afterReminder() {
        assertTrue(TodoCompletionHelper.looksLikeCompletionUtterance("好的已经吃完了"))
        assertTrue(TodoCompletionHelper.looksLikeCompletionUtterance("吃过药了"))
        assertTrue(TodoCompletionHelper.looksLikeCompletionUtterance("快递拿回来了"))
    }

    @Test
    fun looksLikeCompletion_unrelated() {
        assertFalse(TodoCompletionHelper.looksLikeCompletionUtterance("明天早上八点去医院"))
        assertFalse(TodoCompletionHelper.looksLikeCompletionUtterance("协和医院在哪里"))
    }
}
