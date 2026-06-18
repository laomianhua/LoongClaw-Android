package com.littlehelper.domain.todo

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** 记事本模块内待办相关 action 常量。 */
object NotebookAction {
    const val QUERY_TODO = "QUERY_TODO"
    const val UPDATE_TODO_STATUS = "UPDATE_TODO_STATUS"

    fun isTodoAction(action: String?): Boolean =
        action.equals(QUERY_TODO, ignoreCase = true) ||
            action.equals(UPDATE_TODO_STATUS, ignoreCase = true)
}

object TodoStatus {
    const val COMPLETED = "COMPLETED"
}

@Keep
data class TodoActionPayload(
    @SerializedName("query_keyword")
    val queryKeyword: String? = null,
    @SerializedName("todo_id")
    val todoId: Long? = null,
    @SerializedName("todo_keyword")
    val todoKeyword: String? = null,
    val status: String? = null
)
