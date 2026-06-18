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

    @Test
    fun isMemoryQueryRequest_catchesTodoListWithRecords() {
        assertTrue(RecordListQueryHelper.isMemoryQueryRequest("我现在要代办的记录有哪些"))
        assertTrue(RecordListQueryHelper.isMemoryQueryRequest("我刚才记的王医生诊所在哪儿"))
    }

    @Test
    fun isMemoryQueryRequest_falseForNewSaveUtterance() {
        assertFalse(RecordListQueryHelper.isMemoryQueryRequest("我今天下午四点先去天安门后去美术馆"))
    }

    @Test
    fun isTodoListQuestion_distinguishesPureTodoList() {
        assertTrue(RecordListQueryHelper.isTodoListQuestion("我有哪些待办"))
        assertFalse(RecordListQueryHelper.isTodoListQuestion("我现在要代办的记录有哪些"))
    }
}
