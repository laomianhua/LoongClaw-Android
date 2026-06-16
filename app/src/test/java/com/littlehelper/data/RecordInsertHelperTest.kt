package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordInsertHelperTest {

    @Test
    fun userRequestsNewRecord_detectsMarkers() {
        assertTrue(RecordInsertHelper.userRequestsNewRecord("我想新增一条王刚的生日记录"))
        assertFalse(RecordInsertHelper.userRequestsNewRecord("王刚的生日是2月6号"))
    }

    @Test
    fun buildInsertOperation_extractsPersonAndDate() {
        val op = RecordInsertHelper.buildInsertOperation("新增一条王纲的生日1月1号")
        assertNotNull(op)
        assertEquals("insert", op?.op)
        assertEquals("王纲", op?.record?.person)
        assertEquals("1月1号", op?.record?.eventDate)
    }

    @Test
    fun isExactDuplicate_matchesPersonAndDate() {
        val records = listOf(
            MemoryRecord(
                rawText = "王刚2月6号",
                summary = "王刚生日2月6号",
                category = "birthday",
                person = "王刚",
                eventDate = "2月6号"
            )
        )
        assertTrue(RecordInsertHelper.isExactDuplicate(records, "王刚", "2月6号"))
        assertFalse(RecordInsertHelper.isExactDuplicate(records, "王纲", "1月1号"))
    }
}
