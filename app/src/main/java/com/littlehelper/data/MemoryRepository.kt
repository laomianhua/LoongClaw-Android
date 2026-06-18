package com.littlehelper.data

import com.littlehelper.util.DebugLog

import com.littlehelper.ImportanceLevel
import com.littlehelper.MemoryCategory
import com.littlehelper.RecordType



class MemoryRepository(private val dao: MemoryDao) {



    suspend fun save(record: MemoryRecord): Long {
        val prepared = record.prepareForInsert()
        DebugLog.d("DB_OP", "本地触发了落库动作，内容为: ${prepared.summary}")
        return dao.insert(prepared)
    }



    suspend fun update(record: MemoryRecord) = dao.update(record.prepareForUpdate())



    suspend fun delete(record: MemoryRecord) = dao.delete(record)



    suspend fun getLatest(): MemoryRecord? = dao.getLatest()



    /**

     * 按删除指令查找记录。使用 target/tags 的字面匹配，避免同音字误删（如删「夏子涵」不删「夏子杭」）。

     */

    suspend fun findRecordsForDelete(target: String, tags: List<String>): List<MemoryRecord> {

        val terms = buildDeleteTerms(target, tags)



        if (terms.isEmpty()) return emptyList()



        val wantsBirthday = target.contains("生日") || tags.any { it.contains("生日") }



        return dao.getAll().filter { record ->

            val blob = "${record.summary} ${record.rawText} ${record.person.orEmpty()}"

            val termMatched = terms.any { blob.contains(it) }

            if (!termMatched) return@filter false

            if (wantsBirthday) {

                blob.contains("生日") || record.category == MemoryCategory.BIRTHDAY.value

            } else {

                true

            }

        }

    }



    companion object {

        private val DELETE_STOP_WORDS = setOf("删除", "去掉", "不要", "记录", "信息", "那条", "刚才")



        fun buildDeleteTerms(target: String, tags: List<String>): List<String> {

            val terms = linkedSetOf<String>()

            tags.filter { it.isNotBlank() && it.length >= 2 }.forEach { terms.add(it.trim()) }

            NameMatcher.extractPersonNames(target).forEach { terms.add(it) }



            val cleaned = target

                .replace(Regex("(帮我|请|把|将|删除|删掉|去掉|取消|不要|这条|那条|记录|信息)"), "")

                .trim()

            if (cleaned.length >= 2) terms.add(cleaned)



            if (target.contains("咖啡") || tags.any { it.contains("咖啡") }) terms.add("咖啡")

            if (target.contains("停车") || tags.any { it.contains("停车") }) terms.add("停车")

            if (target.contains("生日") || tags.any { it.contains("生日") }) terms.add("生日")



            return terms

                .filter { it.length >= 2 }

                .filter { it !in DELETE_STOP_WORDS }

                .toList()

        }



        /** 写入 SQLite 前归一化字段，并根据 [MemoryRecord.person] 自动填充 [MemoryRecord.personPinyin]。 */

        internal fun MemoryRecord.prepareForInsert(): MemoryRecord {

            return normalizeFields().copy(createdAt = System.currentTimeMillis())

        }



        internal fun MemoryRecord.prepareForUpdate(): MemoryRecord = normalizeFields()



        private fun MemoryRecord.normalizeFields(): MemoryRecord {

            val personName = person?.trim()?.takeIf { it.isNotEmpty() }

            val categoryValue = MemoryCategory.normalize(category)

            val recurring = isRecurring || categoryValue == MemoryCategory.BIRTHDAY.value

            return copy(

                rawText = rawText.trim(),

                summary = summary.trim(),

                category = categoryValue,

                person = personName,

                personPinyin = personName?.let { PinyinHelper.toPinyinKey(it) },

                isRecurring = recurring,

                importanceLevel = ImportanceLevel.normalize(importanceLevel),

                type = RecordType.normalize(type)

            )

        }

    }



    suspend fun getAll(): List<MemoryRecord> = dao.getAll()

    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<MemoryRecord>> = dao.getAllFlow()



    suspend fun getById(id: Long): MemoryRecord? = dao.getById(id)



    suspend fun getRecentSummaries(limit: Int = 50): List<MemoryRecord> =
        dao.getAll().take(limit)

    suspend fun getByIds(ids: List<Long>): List<MemoryRecord> =
        ids.mapNotNull { getById(it) }

    suspend fun searchForQuery(

        category: MemoryCategory?,

        keywords: List<String>,

        question: String = "",

        allowBroadFallback: Boolean = true

    ): List<MemoryRecord> {

        val results = linkedSetOf<MemoryRecord>()



        if (category != null && category != MemoryCategory.GENERAL) {

            results.addAll(dao.getByCategory(category.value, limit = 10))

        }



        keywords.filter { it.isNotBlank() }.forEach { keyword ->

            results.addAll(dao.searchByKeyword(keyword, limit = 10))

        }



        val queryNames = (keywords + NameMatcher.extractPersonNames(question))

            .flatMap { listOf(it) + NameMatcher.extractPersonNames(it) }

            .distinct()

        if (queryNames.isNotEmpty()) {

            val homophoneMatches = NameMatcher.findHomophoneMatches(dao.getAll(), queryNames)

            results.addAll(homophoneMatches)

        }



        if (results.isEmpty() && allowBroadFallback) {

            results.addAll(dao.getAll().take(15))

        }



        return results

            .distinctBy { it.id }

            .sortedByDescending { it.createdAt }

            .take(15)

    }



    suspend fun getUpcomingReminders(): List<MemoryRecord> =

        dao.getWithEventDate(limit = 100)



    suspend fun clearAllRecords() {

        dao.deleteAll()

    }

    suspend fun searchIncompleteTodos(keyword: String, limit: Int = 20): List<MemoryRecord> {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return emptyList()
        return dao.searchIncompleteTodos(normalized, limit)
    }

}


