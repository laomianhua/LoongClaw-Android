package com.littlehelper.network

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val thinking: ThinkingConfig = ThinkingConfig()
)

@Keep
data class ThinkingConfig(
    val type: String = "disabled"
)

@Keep
data class ChatMessage(
    val role: String,
    val content: String
)

@Keep
data class ChatCompletionResponse(
    val choices: List<ChatChoice>?
)

@Keep
data class ChatChoice(
    val message: ChatMessage?
)

@Keep
data class ParsedMemoryPayload(
    val category: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val person: String? = null,
    val item: String? = null,
    val location: String? = null,
    @SerializedName("event_date")
    val eventDate: String? = null,
    @SerializedName("event_time")
    val eventTime: String? = null,
    @SerializedName("is_recurring")
    val isRecurring: Boolean = false,
    val keywords: List<String>? = null,
    @SerializedName("reminder_text")
    val reminderText: String? = null
)

@Keep
data class QueryPlanPayload(
    val category: String? = null,
    val keywords: List<String>? = null,
    @SerializedName("prefer_latest")
    val preferLatest: Boolean = false,
    @SerializedName("answer_hint")
    val answerHint: String? = null
)

@Keep
data class CorrectionPayload(
    @SerializedName("corrected_raw_text")
    val correctedRawText: String? = null,
    val category: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val person: String? = null,
    val item: String? = null,
    val location: String? = null,
    @SerializedName("event_date")
    val eventDate: String? = null,
    @SerializedName("event_time")
    val eventTime: String? = null,
    @SerializedName("is_recurring")
    val isRecurring: Boolean = false,
    val keywords: List<String>? = null,
    @SerializedName("reminder_text")
    val reminderText: String? = null
) {
    fun toMemoryPayload(): ParsedMemoryPayload {
        return ParsedMemoryPayload(
            category = category,
            title = title,
            summary = summary,
            person = person,
            item = item,
            location = location,
            eventDate = eventDate,
            eventTime = eventTime,
            isRecurring = isRecurring,
            keywords = keywords,
            reminderText = reminderText
        )
    }
}

data class CorrectionResult(
    val correctedRawText: String,
    val payload: ParsedMemoryPayload
)

@Keep
data class SavePayload(
    val summary: String? = null,
    @SerializedName("raw_text")
    val rawText: String? = null,
    val tags: List<String>? = null,
    @SerializedName("has_todo")
    val hasTodo: Boolean = false,
    val person: String? = null
)

@Keep
data class DeletePayload(
    /** 要删除的内容描述，如「夏子涵的生日」。 */
    val target: String? = null,
    val tags: List<String>? = null,
    val reason: String? = null
)
