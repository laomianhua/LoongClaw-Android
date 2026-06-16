package com.littlehelper.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordListQueryHelperTest {

    @Test
    fun isListAllRecordsQuestion_recognizesCommonPhrases() {
        assertTrue(RecordListQueryHelper.isListAllRecordsQuestion("你现在有哪些记录"))
        assertTrue(RecordListQueryHelper.isListAllRecordsQuestion("有几条记录"))
        assertTrue(RecordListQueryHelper.isListAllRecordsQuestion("都记了什么"))
    }

    @Test
    fun isListAllRecordsQuestion_falseForSpecificQuery() {
        assertFalse(RecordListQueryHelper.isListAllRecordsQuestion("王刚生日是哪天"))
        assertFalse(RecordListQueryHelper.isListAllRecordsQuestion("明天上午有什么安排"))
    }
}
