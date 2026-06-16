package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryNormalizationTest {

    @Test
    fun prepareForInsert_autoFillsPinyinAndCreatedAt() {
        val record = MemoryRecord(
            rawText = "王刚生日6月8号",
            summary = "王刚生日6月8号",
            category = "birthday",
            person = "王刚"
        )
        val prepared = with(MemoryRepository) { record.prepareForInsert() }
        assertTrue(prepared.createdAt > 0)
        assertEquals("wanggang", prepared.personPinyin)
        assertEquals("birthday", prepared.category)
        assertTrue(prepared.isRecurring)
    }

    @Test
    fun prepareForInsert_normalizesInvalidCategory() {
        val record = MemoryRecord(
            rawText = "test",
            summary = "test",
            category = "unknown_type",
            person = "李华"
        )
        val prepared = with(MemoryRepository) { record.prepareForInsert() }
        assertEquals("general", prepared.category)
        assertEquals("lihua", prepared.personPinyin)
    }
}

