package com.littlehelper.data



import com.littlehelper.MemoryCategory



/** 执行 AI 下发的结构化数据库操作（insert / update / delete）。 */

class MemoryOperationExecutor(private val repository: MemoryRepository) {



    data class ExecutionResult(

        val applied: Int = 0,

        val insertedIds: List<Long> = emptyList(),

        val updatedIds: List<Long> = emptyList(),

        val deletedIds: List<Long> = emptyList(),

        val errors: List<String> = emptyList(),

        /** 含 clear_all 操作，需用户确认后再执行，本次不写入数据库。 */

        val pendingClearAll: Boolean = false

    )



    suspend fun execute(operations: List<MemoryOperation>): ExecutionResult {

        if (operations.isEmpty()) return ExecutionResult()

        if (operations.any { it.normalizedOp() == "clear_all" }) {

            return ExecutionResult(pendingClearAll = true)

        }

        var applied = 0

        val insertedIds = mutableListOf<Long>()

        val updatedIds = mutableListOf<Long>()

        val deletedIds = mutableListOf<Long>()

        val errors = mutableListOf<String>()



        operations.forEachIndexed { index, operation ->

            runCatching {

                when (operation.normalizedOp()) {

                    "insert" -> insertedIds += executeInsert(operation)

                    "update" -> updatedIds += executeUpdate(operation)

                    "delete" -> deletedIds += executeDelete(operation)

                    else -> error("不支持的操作：${operation.op}")

                }

            }.onSuccess {

                applied++

            }.onFailure { e ->

                errors += "第${index + 1}步失败：${e.message ?: "未知错误"}"

            }

        }



        return ExecutionResult(

            applied = applied,

            insertedIds = insertedIds,

            updatedIds = updatedIds,

            deletedIds = deletedIds,

            errors = errors

        )

    }



    private suspend fun executeInsert(operation: MemoryOperation): Long {

        val payload = operation.record ?: error("insert 缺少 record")

        val record = payload.toMemoryRecord()

        return repository.save(record)

    }



    private suspend fun executeUpdate(operation: MemoryOperation): Long {

        val existing = resolveSingleTarget(operation) ?: error("update 找不到唯一目标记录")

        val fields = operation.fields ?: operation.record ?: error("update 缺少 fields")

        val merged = mergeRecord(existing, fields)

        repository.update(merged)

        return merged.id

    }



    private suspend fun executeDelete(operation: MemoryOperation): Long {

        val target = resolveSingleTarget(operation) ?: error("delete 找不到唯一目标记录")

        val id = target.id

        repository.delete(target)

        return id

    }



    private suspend fun resolveSingleTarget(operation: MemoryOperation): MemoryRecord? {

        operation.id?.let { id ->

            return repository.getById(id) ?: error("找不到 id=$id 的记录")

        }

        operation.match?.id?.let { id ->

            return repository.getById(id) ?: error("找不到 id=$id 的记录")

        }



        val match = operation.match ?: error("update/delete 必须提供 id 或 match")

        val candidates = repository.getAll().filter { record -> recordMatches(record, match) }

        return when (candidates.size) {

            0 -> null

            1 -> candidates.first()

            else -> error("match 匹配到 ${candidates.size} 条记录，请用 id 指定")

        }

    }



    private fun recordMatches(record: MemoryRecord, match: MemoryMatch): Boolean {

        match.id?.let { if (record.id != it) return false }

        match.category?.let { if (record.category != it) return false }

        match.person?.let { person ->

            val stored = record.person?.takeIf { it.isNotBlank() }

                ?: NameMatcher.extractPersonNames(record.summary).firstOrNull()

            if (stored != person) return false

        }

        match.personPinyin?.let { pinyin ->

            val storedPinyin = record.personPinyin?.takeIf { it.isNotBlank() }

                ?: record.person?.let { PinyinHelper.toPinyinKey(it) }

            if (storedPinyin != pinyin) return false

        }

        return true

    }



    private fun mergeRecord(existing: MemoryRecord, fields: MemoryRecordPayload): MemoryRecord {

        val summary = fields.summary?.trim()?.takeIf { it.isNotEmpty() } ?: existing.summary

        val rawText = fields.rawText?.trim()?.takeIf { it.isNotEmpty() } ?: existing.rawText

        val personName = fields.person?.trim()?.takeIf { it.isNotEmpty() } ?: existing.person

        val category = MemoryCategory.normalize(

            fields.category?.trim()?.takeIf { it.isNotEmpty() } ?: existing.category

        )

        val recurring = when {
            fields.isRecurring == true -> true
            category == MemoryCategory.BIRTHDAY.value -> true
            fields.isRecurring == false -> false
            else -> existing.isRecurring
        }

        val importanceLevel = com.littlehelper.ImportanceLevel.normalize(
            fields.importanceLevel?.trim()?.takeIf { it.isNotEmpty() } ?: existing.importanceLevel
        )

        val type = com.littlehelper.RecordType.normalize(
            fields.type?.trim()?.takeIf { it.isNotEmpty() } ?: existing.type
        )

        return existing.copy(

            summary = summary,

            rawText = rawText,

            category = category,

            person = personName,

            eventDate = fields.eventDate ?: existing.eventDate,

            formattedDateForAlarm = fields.formattedDateForAlarm ?: existing.formattedDateForAlarm,

            eventTime = fields.eventTime ?: existing.eventTime,

            isRecurring = recurring,

            importanceLevel = importanceLevel,

            type = type

        )

    }



    private fun MemoryRecordPayload.toMemoryRecord(): MemoryRecord {

        val summaryText = summary?.trim().orEmpty().ifBlank { rawText?.trim().orEmpty() }

        val raw = rawText?.trim().orEmpty().ifBlank { summaryText }

        val tagsList = tags.orEmpty().filter { it.isNotBlank() }

        val personName = person?.trim()?.takeIf { it.isNotEmpty() }

            ?: NameMatcher.extractPersonNames(summaryText).firstOrNull()

        val inferredCategory = inferCategory(summaryText, tagsList)

        val aiCategoryRaw = category?.trim()?.takeIf { it.isNotEmpty() }

        val categoryValue = when {
            aiCategoryRaw == null -> MemoryCategory.normalize(inferredCategory.value)
            MemoryCategory.fromValue(aiCategoryRaw) == MemoryCategory.GENERAL &&
                inferredCategory != MemoryCategory.GENERAL -> MemoryCategory.normalize(inferredCategory.value)
            else -> MemoryCategory.normalize(aiCategoryRaw)
        }

        val eventDateValue = eventDate?.trim()?.takeIf { it.isNotEmpty() }

            ?: extractEventDate(summaryText)

        val formattedDateValue = formattedDateForAlarm?.trim()?.takeIf { it.isNotEmpty() }

        val importanceLevelValue = com.littlehelper.ImportanceLevel.normalize(
            importanceLevel?.trim()?.takeIf { it.isNotEmpty() }
        )

        val typeValue = com.littlehelper.RecordType.normalize(
            type?.trim()?.takeIf { it.isNotEmpty() }
        )

        return MemoryRecord(

            rawText = raw,

            summary = summaryText,

            category = categoryValue,

            person = personName,

            eventDate = eventDateValue,

            formattedDateForAlarm = formattedDateValue,

            eventTime = eventTime?.trim()?.takeIf { it.isNotEmpty() },

            isRecurring = (isRecurring ?: false) || categoryValue == MemoryCategory.BIRTHDAY.value,

            importanceLevel = importanceLevelValue,

            type = typeValue

        )

    }



    private fun inferCategory(summary: String, tags: List<String>): MemoryCategory {

        val text = summary + tags.joinToString("")

        return when {

            text.contains("生日") -> MemoryCategory.BIRTHDAY

            text.contains("停车") || text.contains("车位") || (text.contains("车") && text.contains("停")) -> MemoryCategory.PARKING

            text.contains("放") && (text.contains("哪") || text.contains("位置")) -> MemoryCategory.ITEM_PLACE

            else -> MemoryCategory.GENERAL

        }

    }



    private fun extractEventDate(text: String): String? {

        return Regex("""(\d{1,2}月\d{1,2}[日号]?)""").find(text)?.groupValues?.get(1)

    }



    private fun MemoryOperation.normalizedOp(): String = op.trim().lowercase()

}


