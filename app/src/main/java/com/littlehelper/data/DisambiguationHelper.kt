package com.littlehelper.data



import com.littlehelper.MemoryCategory



object DisambiguationHelper {

    private val chineseOrdinals = mapOf(

        "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5,

        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10

    )



    /** 用户问「有哪些记录」等时的编号列表（App 本地生成，不交给 AI 自由发挥）。 */

    fun buildAllRecordsAnswer(records: List<MemoryRecord>): String {

        if (records.isEmpty()) return "您的记事本里目前还没有任何记录。"

        val lines = records.mapIndexed { index, record ->

            "${index + 1}. ${formatRecordLine(record, question = "")}"

        }

        return buildString {

            append("您的记事本里共有 ${records.size} 条记录：")

            lines.forEach { line ->

                append('\n')

                append(line)

            }

        }

    }

    fun buildIncompleteTodosAnswer(records: List<MemoryRecord>): String {
        if (records.isEmpty()) return "您目前还没有未完成的待办任务。"
        val lines = records.mapIndexed { index, record ->
            "${index + 1}. ${formatRecordLine(record, question = "")}"
        }
        return buildString {
            append("您现在有 ${records.size} 条待办：")
            lines.forEach { line ->
                append('\n')
                append(line)
            }
        }
    }



    fun buildChoicePrompt(records: List<MemoryRecord>, question: String): String {

        require(records.isNotEmpty())

        val lines = records.mapIndexed { index, record ->

            "${index + 1}. ${formatRecordLine(record, question)}"

        }

        return buildString {

            if (records.size == 1) {

                append("您说的名字和我记录的字不一样，但我找到一条读音相近的记录，是这位吗？")

            } else {

                append("我找到${records.size}条读音相近的记录，请问是第几个？")

            }

            lines.forEach { line ->

                append('\n')

                append(line)

            }

            if (records.size == 1) {

                append("\n请说「是」或「第一个」确认。")

            }

        }

    }



    fun formatRecordLine(record: MemoryRecord, question: String): String {
        val explicitPerson = record.person?.takeIf { it.isNotBlank() }
        val inferredPerson = explicitPerson
            ?: NameMatcher.extractPersonNames(record.summary).firstOrNull()
            ?: NameMatcher.extractPersonNames(record.rawText).firstOrNull()
        return when {
            question.contains("生日") && !record.eventDate.isNullOrBlank() && inferredPerson != null ->
                "$inferredPerson，生日${record.eventDate}"
            record.category == MemoryCategory.BIRTHDAY.value &&
                !record.eventDate.isNullOrBlank() &&
                inferredPerson != null ->
                "$inferredPerson，生日${record.eventDate}"
            explicitPerson != null -> "$explicitPerson，${record.summary}"
            else -> record.summary
        }
    }



    fun displayPersonName(record: MemoryRecord): String {

        return record.person?.takeIf { it.isNotBlank() }

            ?: NameMatcher.extractPersonNames(record.summary).firstOrNull()

            ?: NameMatcher.extractPersonNames(record.rawText).firstOrNull()

            ?: "未命名"

    }



    fun answerForRecord(record: MemoryRecord, question: String): String {

        val name = displayPersonName(record)

        return when {

            question.contains("生日") && !record.eventDate.isNullOrBlank() ->

                "${name}的生日是${record.eventDate}。"

            record.category == MemoryCategory.BIRTHDAY.value && !record.eventDate.isNullOrBlank() ->

                "${name}的生日是${record.eventDate}。"

            else -> record.summary

        }

    }



    /** @return 0-based index, or null if not recognized */

    fun parseChoiceIndex(text: String, optionCount: Int): Int? {

        if (optionCount <= 0) return null

        val normalized = text.trim()

            .replace(Regex("[，。！？、\\s]"), "")

        if (normalized.isEmpty()) return null



        Regex("(\\d+)").find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->

            if (number in 1..optionCount) return number - 1

        }



        Regex("第([一二两三四五六七八九十\\d]+)个?").find(normalized)?.groupValues?.get(1)?.let { token ->

            parseOrdinalToken(token)?.let { number ->

                if (number in 1..optionCount) return number - 1

            }

        }



        if (normalized in setOf("这个", "就这个", "选这个", "是", "对", "是的", "没错")) return 0



        return null

    }



    private fun parseOrdinalToken(token: String): Int? {

        token.toIntOrNull()?.let { return it }

        if (token.length == 1) return chineseOrdinals[token]

        if (token.startsWith("十")) {

            val tail = token.removePrefix("十")

            return if (tail.isEmpty()) 10 else 10 + (chineseOrdinals[tail] ?: return null)

        }

        if (token.endsWith("十")) {

            val head = token.removeSuffix("十")

            return (chineseOrdinals[head] ?: return null) * 10

        }

        return null

    }

}



object RecordListQueryHelper {

    private val listAllMarkers = listOf(

        "哪些记录",

        "什么记录",

        "哪些内容",

        "什么内容",

        "几条记录",

        "多少条记录",

        "记了什么",

        "记下来什么",

        "记录了什么",

        "列出记录",

        "全部记录"

    )

    private val memoryQueryMarkers = listOf(
        "记在哪儿",
        "记在哪里",
        "记在啥地方",
        "刚才记",
        "哪条记",
        "有什么记",
        "有啥记",
        "回忆一下",
        "查一下记",
        "找一下记"
    )

    private val listQueryHints = listOf("哪些", "什么", "有哪些", "多少", "几条", "有没有")

    /** 端侧显式短语：命中则走 queryMemory，绝不进入秘书 SAVE / insert 流程。 */
    fun isMemoryQueryRequest(question: String): Boolean {
        val normalized = question.trim()
        if (normalized.isEmpty()) return false
        if (isListAllRecordsQuestion(normalized)) return true
        if (memoryQueryMarkers.any { normalized.contains(it) }) return true
        if (isTodoListQuestion(normalized)) return true
        if (normalized.contains("记录") && listQueryHints.any { normalized.contains(it) }) return true
        return false
    }

    fun isTodoListQuestion(question: String): Boolean {
        val normalized = question.trim()
        if (normalized.isEmpty()) return false
        val mentionsTodo = normalized.contains("待办") || normalized.contains("代办")
        if (!mentionsTodo) return false
        // 「代办/待办记录有哪些」按列举全部记录处理，不单列 type=todo
        if (normalized.contains("记录") && listQueryHints.any { normalized.contains(it) }) return false
        return listQueryHints.any { normalized.contains(it) }
    }

    fun isListAllRecordsQuestion(question: String): Boolean {

        val normalized = question.trim()

        if (normalized.isEmpty()) return false

        return listAllMarkers.any { normalized.contains(it) }

    }

}


