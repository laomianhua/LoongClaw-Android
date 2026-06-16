package com.littlehelper

import com.littlehelper.data.MemoryRecord
import com.littlehelper.data.NameMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun namesLikelySame_distinguishesHanAndHang() {
        assertFalse(NameMatcher.namesLikelySame("夏子涵", "夏子杭"))
        assertTrue(NameMatcher.namesLikelySame("夏子航", "夏子杭"))
        assertFalse(NameMatcher.namesLikelySame("夏子涵", "李子涵"))
    }

    @Test
    fun extractPersonNames_findsNameNearBirthday() {
        val names = NameMatcher.extractPersonNames("夏子涵的生日是哪天")
        assertTrue(names.contains("夏子涵"))
    }

    @Test
    fun recordMentionsName_matchesHangHomophoneNotHan() {
        val record = MemoryRecord(
            rawText = "夏子杭生日6月8号",
            summary = "夏子杭生日6月8号",
            category = "birthday",
            person = "夏子杭",
            personPinyin = "xiazihang",
            eventDate = "6月8号"
        )
        assertFalse(NameMatcher.recordMentionsName(record, "夏子涵"))
        assertTrue(NameMatcher.recordMentionsName(record, "夏子航"))
    }

    @Test
    fun findHomophoneMatches_returnsMatchingRecordForHang() {
        val record = MemoryRecord(
            id = 1,
            rawText = "夏子杭生日6月8号",
            summary = "夏子杭生日6月8号",
            category = "birthday",
            person = "夏子杭",
            personPinyin = "xiazihang",
            eventDate = "6月8号"
        )
        val matches = NameMatcher.findHomophoneMatches(listOf(record), listOf("夏子航"))
        assertEquals(1, matches.size)
        assertEquals(1L, matches.first().id)
    }

    @Test
    fun findHomophoneMatches_doesNotMatchHanWhenStoredHang() {
        val record = MemoryRecord(
            id = 1,
            rawText = "夏子杭生日6月8号",
            summary = "夏子杭生日6月8号",
            category = "birthday",
            person = "夏子杭",
            personPinyin = "xiazihang",
            eventDate = "6月8号"
        )
        val matches = NameMatcher.findHomophoneMatches(listOf(record), listOf("夏子涵"))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun resolvePersonMatches_returnsAllWhenMultiplePinyinMatches() {
        val gang = MemoryRecord(
            id = 1,
            rawText = "王纲生日1月1号",
            summary = "王纲生日1月1号",
            category = "birthday",
            person = "王纲",
            personPinyin = "wanggang",
            eventDate = "1月1号"
        )
        val gang2 = MemoryRecord(
            id = 2,
            rawText = "王刚生日1月6号",
            summary = "王刚生日1月6号",
            category = "birthday",
            person = "王刚",
            personPinyin = "wanggang",
            eventDate = "1月6号"
        )
        val matches = NameMatcher.resolvePersonMatches(listOf(gang, gang2), listOf("王刚")) { true }
        assertEquals(2, matches.size)
    }

    @Test
    fun isSameStoredPerson_distinguishesHomophoneNames() {
        val existing = MemoryRecord(
            rawText = "王缸",
            summary = "王缸生日1月1号",
            category = "birthday",
            person = "王缸"
        )
        val updated = MemoryRecord(
            rawText = "王刚",
            summary = "王刚生日1月5号",
            category = "birthday",
            person = "王刚"
        )
        assertFalse(NameMatcher.isSameStoredPerson(existing, updated))
    }

    @Test
    fun isSeparatePersonClarification_detectsAnotherPerson() {
        assertTrue(NameMatcher.isSeparatePersonClarification("是另外一个王刚"))
        assertTrue(NameMatcher.isSeparatePersonClarification("", "和之前那位王缸不是同一个人"))
    }
}
