package com.littlehelper.network



import com.littlehelper.FollowUpContext

import com.littlehelper.data.DeleteRequestHelper



/** 校验秘书 SAVE 轮是否遗漏可执行的 DB_OPS。 */

object LlmResponseValidator {

    fun isIgnoredDbOps(response: LlmResponseParser.ParsedResponse): Boolean {
        return response.dbOpsPayload?.status == "ignore"
    }

    fun hasActionableDbOps(response: LlmResponseParser.ParsedResponse): Boolean {
        if (isIgnoredDbOps(response)) return false
        val payload = response.dbOpsPayload ?: return false
        if (!payload.operations.isNullOrEmpty()) return true
        if (com.littlehelper.domain.todo.NotebookAction.isTodoAction(payload.action) &&
            payload.todoPayload != null
        ) {
            return true
        }
        return com.littlehelper.domain.map.IntentRoute.fromWire(payload.intentRoute) ==
            com.littlehelper.domain.map.IntentRoute.MAP &&
            !payload.action.isNullOrBlank()
    }



    private val emptyRecordReplyMarkers = listOf(

        "还没记下",

        "没有任何记录",

        "没有内容被记录",

        "目前还没有内容被记录",

        "还没有记下任何信息",

        "还没有记下任何内容"

    )



    /** AI 仍在合法追问、尚未到写库步骤 → 允许本轮无 DB_OPS。 */
    fun isLegitimateFollowUpWithoutOps(reply: String): Boolean {
        return AssistantFollowUpDetector.invitesFollowUp(reply.trim())
    }

    /** 记下确认轮：用户短肯定 + 无 DB_OPS + 回复声称未记下 → 需后台纠错。 */
    fun needsSaveConfirmEmptyReplyCorrection(

        response: LlmResponseParser.ParsedResponse,

        followUpContext: FollowUpContext,

        lastUserText: String?

    ): Boolean {

        if (followUpContext != FollowUpContext.SAVE) return false

        if (hasActionableDbOps(response)) return false

        if (response.savePayload != null || response.deletePayload != null) return false

        if (!SaveConfirmationHelper.isShortAffirmation(lastUserText.orEmpty())) return false

        return replyClaimsNothingRecorded(response.reply)

    }



    fun replyClaimsNothingRecorded(reply: String): Boolean {

        val normalized = reply.trim()

        if (normalized.isEmpty()) return false

        return emptyRecordReplyMarkers.any { normalized.contains(it) }

    }



    /** 用户要求删除，但 AI 未输出 DB_OPS（且不是在追问删哪条）。 */

    fun needsDeleteWithoutOpsCorrection(

        response: LlmResponseParser.ParsedResponse,

        lastUserText: String?

    ): Boolean {

        val userText = lastUserText.orEmpty()
        if (DeleteRequestHelper.isDeleteCancellation(userText)) return false
        if (!DeleteRequestHelper.isDeleteRequest(userText)) return false

        if (hasActionableDbOps(response)) return false

        if (response.savePayload != null || response.deletePayload != null) return false

        if (isLegitimateFollowUpWithoutOps(response.reply)) return false

        return true

    }



    /**

     * SAVE 秘书轮默认规则：无 DB_OPS 即需纠错，不依赖 AI 用了哪种「已记下」措辞。

     * 例外：合法追问（缺字段、同音歧义等）。

     */

    fun needsSaveTurnWithoutOpsCorrection(

        response: LlmResponseParser.ParsedResponse,

        lastUserText: String?

    ): Boolean {

        if (DeleteRequestHelper.isDeleteRequest(lastUserText.orEmpty())) return false

        if (hasActionableDbOps(response)) return false

        if (response.savePayload != null || response.deletePayload != null) return false

        if (isLegitimateFollowUpWithoutOps(response.reply)) return false

        return true

    }



    /** 秘书轮未输出可执行 DB_OPS 时需后台纠错（不依赖口头承诺词表）。 */

    fun needsDbOpsSelfCorrection(

        response: LlmResponseParser.ParsedResponse,

        followUpContext: FollowUpContext = FollowUpContext.NONE,

        lastUserText: String? = null

    ): Boolean {

        if (isIgnoredDbOps(response)) return false

        if (hasActionableDbOps(response)) return false

        if (response.savePayload != null || response.deletePayload != null) return false

        if (needsSaveConfirmEmptyReplyCorrection(response, followUpContext, lastUserText)) return true

        if (needsDeleteWithoutOpsCorrection(response, lastUserText)) return true

        return needsSaveTurnWithoutOpsCorrection(response, lastUserText)

    }

}


