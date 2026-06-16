package com.littlehelper.data

object NameMatcher {
    private val personNamePattern = Regex("[\\u4e00-\\u9fff]{2,4}")
    private val birthdayContextPattern = Regex("([\\u4e00-\\u9fff]{2,4})(?:的)?生日|生日(?:是)?([\\u4e00-\\u9fff]{2,4})")
    private val trailingParticlePattern = Regex("[的吗呢吧啊呀]$")

    fun extractPersonNames(text: String): List<String> {
        val names = linkedSetOf<String>()
        birthdayContextPattern.findAll(text).forEach { match ->
            match.groupValues.drop(1)
                .map { normalizeName(it) }
                .filter { it.length >= 2 }
                .forEach { names.add(it) }
        }
        personNamePattern.findAll(text).forEach { match ->
            val candidate = normalizeName(match.value)
            if (candidate.length in 2..4 && !isCommonPhrase(candidate)) {
                names.add(candidate)
            }
        }
        return names.toList()
    }

    private fun normalizeName(name: String): String {
        return name.trim().replace(trailingParticlePattern, "")
    }

    /**
     * 同音匹配：全名拼音相同才视为同一人（「涵 han」与「杭/航 hang」不会混）。
     */
    fun namesLikelySame(queryName: String, storedName: String): Boolean {
        val query = queryName.trim()
        val stored = storedName.trim()
        if (query.isEmpty() || stored.isEmpty()) return false
        if (query == stored) return true
        return PinyinHelper.samePinyin(query, stored)
    }

    fun recordMentionsNameExact(record: MemoryRecord, queryName: String): Boolean {
        val query = queryName.trim()
        if (query.isEmpty()) return false
        record.person?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it == query) return true }
        val fields = listOfNotNull(record.summary, record.rawText)
        return fields.any { field ->
            extractPersonNames(field).any { it == query }
        }
    }

    /**
     * 有多条同音记录时返回全部供用户选择；仅一条时返回该条。
     */
    fun resolvePersonMatches(
        records: List<MemoryRecord>,
        queryNames: List<String>,
        filter: (MemoryRecord) -> Boolean = { true }
    ): List<MemoryRecord> {
        if (queryNames.isEmpty()) return emptyList()
        val pool = records.filter(filter)
        return pool.filter { record ->
            queryNames.any { queryName -> recordMentionsName(record, queryName) }
        }.distinctBy { it.id }
    }

    fun isSeparatePersonClarification(vararg texts: String): Boolean {
        val markers = listOf(
            "另外一个", "另一位", "另一个", "不是同一个", "不是同一个人", "还有一", "另外的", "不同人",
            "新增", "再加", "添加一条", "再记一条", "新建", "增加一条"
        )
        return texts.any { text ->
            markers.any { marker -> text.contains(marker) }
        }
    }

    fun isSameStoredPerson(existing: MemoryRecord, updated: MemoryRecord): Boolean {
        val existingPerson = existing.person?.trim().orEmpty()
        val updatedPerson = updated.person?.trim().orEmpty()
        if (existingPerson.isNotEmpty() && updatedPerson.isNotEmpty()) {
            return existingPerson == updatedPerson
        }

        val existingName = existingPerson.ifBlank {
            extractPersonNames(existing.summary + existing.rawText).firstOrNull().orEmpty()
        }
        val updatedName = updatedPerson.ifBlank {
            extractPersonNames(updated.summary + updated.rawText).firstOrNull().orEmpty()
        }
        if (existingName.isNotEmpty() && updatedName.isNotEmpty()) {
            return existingName == updatedName
        }
        return existingPerson.isEmpty() && updatedPerson.isEmpty() &&
            existingName.isEmpty() && updatedName.isEmpty()
    }

    fun recordMentionsName(record: MemoryRecord, queryName: String): Boolean {
        val canonicalPerson = record.person?.takeIf { it.isNotBlank() }
        if (canonicalPerson != null && namesLikelySame(queryName, canonicalPerson)) {
            return true
        }
        if (!record.personPinyin.isNullOrBlank()) {
            val queryKey = PinyinHelper.toPinyinKey(queryName)
            if (queryKey.isNotEmpty() && queryKey == record.personPinyin) {
                return true
            }
        }

        val fields = listOfNotNull(
            record.summary,
            record.rawText
        )
        return fields.any { field ->
            extractPersonNames(field).any { storedName ->
                namesLikelySame(queryName, storedName)
            }
        }
    }

    fun findHomophoneMatches(records: List<MemoryRecord>, queryNames: List<String>): List<MemoryRecord> {
        if (queryNames.isEmpty()) return emptyList()
        return records.filter { record ->
            queryNames.any { queryName -> recordMentionsName(record, queryName) }
        }
    }

    private fun isCommonPhrase(text: String): Boolean {
        return text in COMMON_PHRASES
    }

    private val COMMON_PHRASES = setOf(
        "生日", "哪天", "几号", "记录", "记下", "停车", "位置", "明天", "今天", "后天"
    )
}
