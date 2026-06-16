package com.littlehelper.network



import com.littlehelper.FollowUpContext

import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue

import org.junit.Test



class LlmResponseValidatorTest {



    @Test

    fun needsDbOpsSelfCorrection_falseWhenIgnoreStatus() {

        val content = """

            没听懂您想记什么，请您再清楚地说一遍好吗？

            ___DB_OPS_START___

            {"status":"ignore","reason":"text_too_vague_or_no_intent","operations":[]}

            ___DB_OPS_END___

        """.trimIndent()

        val response = LlmResponseParser.parse(content)

        assertFalse(

            LlmResponseValidator.needsDbOpsSelfCorrection(

                response,

                FollowUpContext.NONE,

                "嗯嗯啊啊"

            )

        )

        assertFalse(LlmResponseValidator.hasActionableDbOps(response))

        assertTrue(LlmResponseValidator.isIgnoredDbOps(response))

    }



    @Test

    fun needsDbOpsSelfCorrection_whenSaveTurnWithoutOps_evenWithoutPromisePhrase() {

        val response = LlmResponseParser.parse("好的，没问题。")

        assertTrue(

            LlmResponseValidator.needsDbOpsSelfCorrection(

                response,

                FollowUpContext.NONE,

                "明天上午10点提醒我与八达通联系"

            )

        )

    }



    @Test

    fun needsDbOpsSelfCorrection_whenYiJiXiaButMissingOps() {

        val response = LlmResponseParser.parse(

            "好的，已记下：明天上午10点提醒您与八达通联系。"

        )

        assertTrue(LlmResponseValidator.needsDbOpsSelfCorrection(response))

    }



    @Test

    fun needsDbOpsSelfCorrection_falseWhenDbOpsPresent() {

        val content = """

            记下了。

            ___DB_OPS_START___

            {"status":"success","operations":[{"op":"insert","record":{"summary":"test"}}]}

            ___DB_OPS_END___

        """.trimIndent()

        val response = LlmResponseParser.parse(content)

        assertFalse(LlmResponseValidator.needsDbOpsSelfCorrection(response))

    }



    @Test

    fun needsDbOpsSelfCorrection_falseWhenLegacySaveBlockPresent() {

        val content = """

            记下了。

            ___SAVE_START___

            {"summary":"test"}

            ___SAVE_END___

        """.trimIndent()

        val response = LlmResponseParser.parse(content)

        assertFalse(LlmResponseValidator.needsDbOpsSelfCorrection(response))

    }



    @Test

    fun needsDbOpsSelfCorrection_falseWhenInvitesSaveFollowUp() {

        val response = LlmResponseParser.parse("需要我现在帮您记下吗？")

        assertFalse(

            LlmResponseValidator.needsDbOpsSelfCorrection(

                response,

                FollowUpContext.NONE,

                "后天去医院"

            )

        )

    }



    @Test

    fun needsSaveConfirmEmptyReplyCorrection_whenAffirmationAndEmptyReply() {

        val response = LlmResponseParser.parse("目前我这里还没有记下任何信息。")

        assertTrue(

            LlmResponseValidator.needsSaveConfirmEmptyReplyCorrection(

                response,

                FollowUpContext.SAVE,

                "好的"

            )

        )

        assertTrue(

            LlmResponseValidator.needsDbOpsSelfCorrection(

                response,

                FollowUpContext.SAVE,

                "好的"

            )

        )

    }



    @Test

    fun needsSaveConfirmEmptyReplyCorrection_falseWhenNotSaveContext() {

        val response = LlmResponseParser.parse("目前我这里还没有记下任何信息。")

        assertFalse(

            LlmResponseValidator.needsSaveConfirmEmptyReplyCorrection(

                response,

                FollowUpContext.NONE,

                "好的"

            )

        )

    }



    @Test

    fun needsDeleteWithoutOpsCorrection_whenUserDeleteAndVerbalOnly() {

        val response = LlmResponseParser.parse(

            "好的，我这就帮您把两条「一分钟后提醒」的记录都删掉。"

        )

        assertTrue(

            LlmResponseValidator.needsDeleteWithoutOpsCorrection(

                response,

                "把一分钟后提醒的记录都删掉"

            )

        )

    }



    @Test

    fun needsDeleteWithoutOpsCorrection_whenDeleteWithoutPromisePhrase() {

        val response = LlmResponseParser.parse("好的，这就处理。")

        assertTrue(

            LlmResponseValidator.needsDeleteWithoutOpsCorrection(

                response,

                "把一分钟后提醒的记录都删掉"

            )

        )

    }



    @Test

    fun needsDeleteWithoutOpsCorrection_falseWhenUserCancels() {

        val response = LlmResponseParser.parse("好的。")

        assertFalse(

            LlmResponseValidator.needsDeleteWithoutOpsCorrection(

                response,

                "先不用删除"

            )

        )

    }



    @Test

    fun needsDeleteWithoutOpsCorrection_falseWhenDbOpsPresent() {

        val content = """

            已删除。

            ___DB_OPS_START___

            {"status":"success","operations":[{"op":"delete","id":3},{"op":"delete","id":5}]}

            ___DB_OPS_END___

        """.trimIndent()

        val response = LlmResponseParser.parse(content)

        assertFalse(

            LlmResponseValidator.needsDeleteWithoutOpsCorrection(

                response,

                "把一分钟后提醒的记录都删掉"

            )

        )

    }



    @Test

    fun replyClaimsNothingRecorded_detectsEmptySaveMarkers() {

        assertTrue(LlmResponseValidator.replyClaimsNothingRecorded("目前还没有内容被记录。"))

        assertTrue(LlmResponseValidator.replyClaimsNothingRecorded("没有任何记录哦。"))

        assertFalse(LlmResponseValidator.replyClaimsNothingRecorded("需要我现在帮您记下吗？"))

    }

}


