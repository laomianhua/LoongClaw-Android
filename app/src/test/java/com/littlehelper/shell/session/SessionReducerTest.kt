package com.littlehelper.shell.session

import com.littlehelper.ChatRole
import com.littlehelper.PanelState
import com.littlehelper.shell.model.ClawIntent
import com.littlehelper.shell.model.ClawIntentPreload
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ClawSessionResponse
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.PanelCommand
import com.littlehelper.shell.model.ShellUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    @Test
    fun chatDelta_appendDelta_accumulatesStreamingText() {
        var state = ShellUiState()
        state = SessionReducer.reduce(
            state,
            ClawSessionEvent.ChatDelta("s1", "t1", ChatRole.ASSISTANT, "你", appendDelta = false)
        )
        state = SessionReducer.reduce(
            state,
            ClawSessionEvent.ChatDelta("s1", "t1", ChatRole.ASSISTANT, "好", appendDelta = true)
        )

        assertEquals("你好", state.messages.single().text)
    }

    @Test
    fun chatDelta_userEcho_skipsWhenOptimisticBubbleExists() {
        val initial = ShellUiState(
            messages = listOf(
                com.littlehelper.ChatMessage(
                    id = "user-local",
                    role = ChatRole.USER,
                    text = "打开文件管理"
                )
            )
        )
        val event = ClawSessionEvent.ChatDelta(
            sessionId = "s1",
            turnId = "m1",
            role = ChatRole.USER,
            text = "打开文件管理"
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertEquals("打开文件管理", next.messages.single().text)
        assertFalse(next.messages.single().isPartial)
    }

    @Test
    fun chatDelta_appendsStreamingAssistantBubble() {
        val initial = ShellUiState()
        val event = ClawSessionEvent.ChatDelta(
            sessionId = "s1",
            turnId = "t1",
            role = ChatRole.ASSISTANT,
            text = "你好"
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertEquals("你好", next.messages.first().text)
        assertTrue(next.messages.first().isPartial)
    }

    @Test
    fun chatFinal_skipsDuplicateUserEcho() {
        val initial = ShellUiState(
            messages = listOf(
                com.littlehelper.ChatMessage(
                    id = "user-local",
                    role = ChatRole.USER,
                    text = "你好"
                )
            )
        )
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "m1",
            role = ChatRole.USER,
            text = "你好"
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertEquals("你好", next.messages.first().text)
    }

    @Test
    fun chatFinal_skipsDuplicateUserEcho_whenGatewayEchoHasUploadMarker() {
        val initial = ShellUiState(
            messages = listOf(
                com.littlehelper.ChatMessage(
                    id = "user-local",
                    role = ChatRole.USER,
                    text = "帮我分析这张图"
                )
            )
        )
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "m1",
            role = ChatRole.USER,
            text = "帮我分析这张图 [upload:abc123:photo.jpg]"
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertEquals("帮我分析这张图", next.messages.first().text)
    }

    @Test
    fun chatFinal_userUploadOnly_stripsMarkerForDisplay() {
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "m1",
            role = ChatRole.USER,
            text = "[upload:abc123:report.pdf]"
        )

        val next = SessionReducer.reduce(ShellUiState(), event)

        assertEquals(1, next.messages.size)
        assertEquals("report.pdf", next.messages.first().text)
    }

    @Test
    fun intentPreload_whiteboard_switchesSlotBeforePayload() {
        val initial = ShellUiState()
        val event = ClawSessionEvent.IntentPreload(
            ClawIntentPreload(
                sessionId = "s1",
                turnId = "t1",
                targetModule = ModuleId.WHITEBOARD,
                drawerState = PanelCommand.EXPANDED
            )
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(ModuleId.WHITEBOARD, next.activeModule)
        assertEquals(ModuleLoadState.PRELOADING, next.moduleLoadState)
        assertTrue(next.modulePayload is ModulePayload.Empty)
    }

    @Test
    fun intentFinal_whiteboard_appliesComponentPayload() {
        val initial = ShellUiState(
            activeModule = ModuleId.WHITEBOARD,
            moduleLoadState = ModuleLoadState.PRELOADING
        )
        val event = ClawSessionEvent.IntentFinal(
            ClawSessionResponse(
                sessionId = "s1",
                turnId = "t1",
                textContent = "已生成看板",
                intent = ClawIntent(
                    action = "RENDER",
                    targetModule = ModuleId.WHITEBOARD,
                    drawerState = PanelCommand.EXPANDED,
                    payload = ModulePayload.Whiteboard(
                        components = listOf(
                            com.littlehelper.shell.model.UiComponentDto(
                                id = "t1",
                                type = "title",
                                text = "今日概览"
                            )
                        )
                    )
                )
            )
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(ModuleLoadState.READY, next.moduleLoadState)
        val board = next.modulePayload as ModulePayload.Whiteboard
        assertEquals(1, board.components.size)
        assertEquals("今日概览", board.components.first().text)
    }

    @Test
    fun chatFinal_assistantModalMessage_opensCanvasWithBlocks() {
        val initial = ShellUiState(sessionId = "s1")
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "t-modal",
            role = ChatRole.ASSISTANT,
            text = com.littlehelper.shell.demo.HoldingsModalDemo.AGENT_RESPONSE
        )

        val next = SessionReducer.reduce(initial, event)

        assertTrue(next.modalState.isOpen)
        assertEquals(2, next.modalState.blocks.size)
        assertEquals(PanelState.EXPANDED, next.panelState)
        assertEquals(ModuleId.WHITEBOARD, next.activeModule)
        assertTrue(next.messages.single().text.contains("196.8 万"))
        assertTrue(next.messages.single().text.contains("===MODAL===").not())
    }

    @Test
    fun chatFinal_modalUpdate_mergesExistingBlock() {
        var state = SessionReducer.reduce(
            ShellUiState(sessionId = "s1"),
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = com.littlehelper.shell.demo.HoldingsModalDemo.AGENT_RESPONSE
            )
        )
        state = SessionReducer.reduce(
            state,
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t2",
                role = ChatRole.ASSISTANT,
                text = com.littlehelper.shell.demo.HoldingsModalDemo.UPDATE_AGENT_RESPONSE
            )
        )

        assertTrue(state.modalState.isOpen)
        assertEquals(2, state.modalState.blocks.size)
        val chart = state.modalState.blocks.first { it.id == "daily-chart" }
        assertEquals("chart/line", chart.type)
    }

    @Test
    fun userPanelCollapse_preservesModalBlocks() {
        val opened = SessionReducer.reduce(
            ShellUiState(sessionId = "s1"),
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = com.littlehelper.shell.demo.HoldingsModalDemo.AGENT_RESPONSE
            )
        )
        val collapsed = SessionReducer.applyUserPanelOverride(opened, PanelState.COLLAPSED)
        assertTrue(collapsed.modalState.isOpen)
        assertEquals(2, collapsed.modalState.blocks.size)
        assertEquals(PanelState.COLLAPSED, collapsed.panelState)

        val expanded = SessionReducer.applyUserPanelOverride(collapsed, PanelState.EXPANDED)
        assertTrue(expanded.modalState.isOpen)
        assertEquals(2, expanded.modalState.blocks.size)
    }

    @Test
    fun chatDelta_appendDelta_usesRawStreamNotChatPreview() {
        val head = """
===CHAT===
好的，以下是今天的持仓情况。

===MODAL===
{"action":"open","blocks":[{"id":"b1","type":"table","data":{}
        """.trimIndent()
        val tail = "}]}\n===END==="
        var state = ShellUiState(sessionId = "s1")
        state = SessionReducer.reduce(
            state,
            ClawSessionEvent.ChatDelta(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = head,
                appendDelta = false
            )
        )
        assertEquals("好的，以下是今天的持仓情况。", state.messages.single().text)
        state = SessionReducer.reduce(
            state,
            ClawSessionEvent.ChatDelta(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = tail,
                appendDelta = true
            )
        )
        assertTrue(state.modalState.isOpen)
        assertEquals(1, state.modalState.blocks.size)
    }

    @Test
    fun chatFinal_duplicateAssistantText_isDeduped() {
        val initial = ShellUiState(
            messages = listOf(
                com.littlehelper.ChatMessage(
                    id = "final-t1-ASSISTANT",
                    role = ChatRole.ASSISTANT,
                    text = "你好，已为你查询。"
                )
            )
        )
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "t2",
            role = ChatRole.ASSISTANT,
            text = "你好，已为你查询。"
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertEquals("你好，已为你查询。", next.messages.single().text)
    }

    @Test
    fun chatFinal_duplicateAssistantText_withModal_stillOpensCanvas() {
        val initial = ShellUiState(
            messages = listOf(
                com.littlehelper.ChatMessage(
                    id = "final-t1-ASSISTANT",
                    role = ChatRole.ASSISTANT,
                    text = "好的，已为你打开。"
                )
            )
        )
        val modalWire = """
===CHAT===
好的，已为你打开。

===MODAL===
{"action":"open","blocks":[{"id":"map","type":"webview","data":{"url":"/__openclaw__/canvas/map.littlehelper.html"}}]}
===END===
        """.trimIndent()
        val event = ClawSessionEvent.ChatFinal(
            sessionId = "s1",
            turnId = "t2",
            role = ChatRole.ASSISTANT,
            text = modalWire
        )

        val next = SessionReducer.reduce(initial, event)

        assertEquals(1, next.messages.size)
        assertTrue(next.modalState.isOpen)
        assertEquals(PanelState.EXPANDED, next.panelState)
    }

    @Test
    fun chatDelta_payloadModalWithDeltaText_opensCanvas() {
        val json = """
            {"type":"event","event":"chat.delta","payload":{
              "messageId":"m-map",
              "message":{"role":"assistant","content":[{"type":"text","text":"地图已就绪"}]},
              "deltaText":"地图已就绪",
              "modal":{"action":"open","blocks":[{"id":"map","type":"webview","data":{"url":"/__openclaw__/canvas/map.littlehelper.html"}}]}
            }}
        """.trimIndent()
        val event = com.littlehelper.shell.transport.GatewayEventMapper
            .mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatDelta

        val next = SessionReducer.reduce(ShellUiState(sessionId = "s1"), event)

        assertTrue(event.text.contains("===MODAL==="))
        assertTrue(next.modalState.isOpen)
        assertEquals(PanelState.EXPANDED, next.panelState)
    }

    @Test
    fun mergeDeltaText_modalFrameReplacesStreamedMarkdown() {
        val table = """
| 基金 | 金额 |
| 纳指 | 35.2万 |
        """.trimIndent()
        val modalWire = """
===CHAT===
好的，以下是今天的持仓情况。

===MODAL===
{"action":"open","blocks":[{"id":"holdings-table","type":"table","data":{}}]}
===END===
        """.trimIndent()
        val state = ShellUiState(streamingAssistantRaw = table)
        val merged = SessionReducer.mergeDeltaText(
            state,
            ClawSessionEvent.ChatDelta(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = modalWire,
                appendDelta = false
            ),
            existingIndex = 0
        )
        assertEquals(modalWire, merged)
        assertTrue(com.littlehelper.shell.parser.MessageBlockParser.chatDisplayText(merged).contains("持仓"))
        assertTrue(com.littlehelper.shell.parser.MessageBlockParser.chatDisplayText(merged).contains("|").not())
    }

    @Test
    fun chatDelta_streamedMarkdownThenPayloadModal_clearsTableFromChat() {
        val table = "| 基金 | 金额 |\\n| 纳指 | 35.2万 |"
        var state = SessionReducer.reduce(
            ShellUiState(sessionId = "s1"),
            ClawSessionEvent.ChatDelta(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = "| 基金 | 金额 |\n| 纳指 | 35.2万 |",
                appendDelta = false
            )
        )
        assertTrue(state.messages.single().text.contains("|"))

        val json = """
            {"type":"event","event":"chat.delta","payload":{
              "messageId":"t1",
              "message":{"role":"assistant","content":[{"type":"text","text":"好的，以下是今天的持仓情况。"}]},
              "deltaText":"$table",
              "modal":{"action":"open","blocks":[{"id":"holdings-table","type":"table","data":{}}]}
            }}
        """.trimIndent()
        val event = com.littlehelper.shell.transport.GatewayEventMapper
            .mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatDelta
        state = SessionReducer.reduce(state, event)

        assertTrue(state.modalState.isOpen)
        assertTrue(state.messages.single().text.contains("持仓"))
        assertTrue(state.messages.single().text.contains("|").not())
    }

    @Test
    fun chatFinal_sameTurnId_isIdempotent() {
        val initial = SessionReducer.reduce(
            ShellUiState(sessionId = "s1"),
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = "第一次"
            )
        )
        val again = SessionReducer.reduce(
            initial,
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = "第一次"
            )
        )

        assertEquals(1, again.messages.size)
    }

    @Test
    fun chatFinal_invalidModal_setsParseWarning() {
        val raw = """
===CHAT===
仅聊天

===MODAL===
{ broken json
===END===
        """.trimIndent()
        val next = SessionReducer.reduce(
            ShellUiState(sessionId = "s1"),
            ClawSessionEvent.ChatFinal(
                sessionId = "s1",
                turnId = "t-bad",
                role = ChatRole.ASSISTANT,
                text = raw
            )
        )

        assertEquals("仅聊天", next.messages.single().text)
        assertEquals("白板内容解析失败，已仅显示文字回复", next.modalParseWarning)
        assertTrue(next.modalState.isOpen.not())
    }

    @Test
    fun mergeDeltaText_supportsCumulativeSnapshot() {
        val state = ShellUiState(streamingAssistantRaw = "===CHAT===\nhel")
        val merged = SessionReducer.mergeDeltaText(
            state,
            ClawSessionEvent.ChatDelta(
                sessionId = "s1",
                turnId = "t1",
                role = ChatRole.ASSISTANT,
                text = "===CHAT===\nhello",
                appendDelta = true
            ),
            existingIndex = 0
        )
        assertEquals("===CHAT===\nhello", merged)
    }

    @Test
    fun sessionError_pairingRequired_clearsSilentReconnectAndMarksDegraded() {
        val initial = ShellUiState(
            connectionState = com.littlehelper.shell.model.ConnectionState.CONNECTING,
            silentReconnectActive = true,
        )
        val next = SessionReducer.reduce(
            initial,
            ClawSessionEvent.SessionError(
                message = "设备待配对",
                detail = "请到 Control UI 批准",
                pairingRequired = true,
                failureKind = com.littlehelper.shell.transport.ConnectFailureKind.PAIRING_REQUIRED,
                gatewayCode = "PAIRING_REQUIRED",
                userAction = "批准后点上方重试",
            ),
        )
        assertEquals(com.littlehelper.shell.model.ConnectionState.DEGRADED, next.connectionState)
        assertFalse(next.silentReconnectActive)
        assertTrue(next.pairingRequired)
        assertEquals("PAIRING_REQUIRED", next.connectGatewayCode)
    }
}
