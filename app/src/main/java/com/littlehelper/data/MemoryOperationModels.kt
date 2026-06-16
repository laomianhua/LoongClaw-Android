package com.littlehelper.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LlmOpsResponse(
    val status: String = "success",
    val reason: String? = null,
    val operations: List<MemoryOperation>? = null
)

/** @deprecated Use [LlmOpsResponse] */
typealias MemoryOpsPayload = LlmOpsResponse

@Keep
data class MemoryOperation(
    val op: String,
    val id: Long? = null,
    val match: MemoryMatch? = null,
    val record: MemoryRecordPayload? = null,
    val fields: MemoryRecordPayload? = null
)

@Keep
data class MemoryMatch(
    val id: Long? = null,
    val person: String? = null,
    @SerializedName("person_pinyin")
    val personPinyin: String? = null,
    val category: String? = null
)

@Keep
data class MemoryRecordPayload(
    val summary: String? = null,
    @SerializedName("raw_text")
    val rawText: String? = null,
    val person: String? = null,
    val category: String? = null,
    @SerializedName("event_date")
    val eventDate: String? = null,
    @SerializedName("formatted_date_for_alarm")
    val formattedDateForAlarm: String? = null,
    @SerializedName("event_time")
    val eventTime: String? = null,
    @SerializedName("is_recurring")
    val isRecurring: Boolean? = null,
    val tags: List<String>? = null,
    @SerializedName("importance_level")
    val importanceLevel: String? = null,
    val type: String? = null
)
