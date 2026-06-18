package com.littlehelper.data.todo

import com.google.gson.Gson
import com.littlehelper.data.MemoryRecord

object TodoContextBuilder {

    private val gson = Gson()

    data class TodoCandidate(
        val id: Long,
        val title: String
    )

    fun formatCandidates(records: List<MemoryRecord>): String {
        val items = records.map { TodoCandidate(id = it.id, title = it.summary) }
        return gson.toJson(items)
    }

    fun buildDisambiguationPrompt(records: List<MemoryRecord>): String {
        val lines = records.mapIndexed { index, record ->
            "${index + 1}. ${record.summary}"
        }
        return buildString {
            append("我找到 ${records.size} 条相关的待办任务：\n")
            lines.forEach { append(it).append('\n') }
            append("请问是哪一条？")
        }
    }
}
