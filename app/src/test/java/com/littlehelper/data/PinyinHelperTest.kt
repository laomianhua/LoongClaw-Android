package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinHelperTest {

    @Test
    fun toPinyinKey_convertsChineseName() {
        assertEquals("xiazihang", PinyinHelper.toPinyinKey("夏子杭"))
        assertEquals("xiazihan", PinyinHelper.toPinyinKey("夏子涵"))
    }

    @Test
    fun samePinyin_hangVariantsMatchHanDoesNot() {
        assertTrue(PinyinHelper.samePinyin("夏子航", "夏子杭"))
        assertFalse(PinyinHelper.samePinyin("夏子涵", "夏子杭"))
    }

    @Test
    fun appendPinyinSearchTerms_includesKey() {
        val terms = PinyinHelper.appendPinyinSearchTerms("夏子杭")
        assertTrue(terms.contains("夏子杭"))
        assertTrue(terms.contains("xiazihang"))
    }
}
