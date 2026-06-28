package com.littlehelper.shell.transport

import com.littlehelper.ChatRole
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayEventMapperTest {

    @Test
    fun chatDelta_mapsDeltaTextWithAppend() {
        val json = """
            {"type":"event","event":"chat.delta","payload":{
              "messageId":"m1","role":"assistant","deltaText":"你好"
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatDelta

        assertEquals("m1", event.turnId)
        assertEquals(ChatRole.ASSISTANT, event.role)
        assertEquals("你好", event.text)
        assertTrue(event.appendDelta)
    }

    @Test
    fun sessionMessage_mapsNestedGatewayEnvelope() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "sessionKey":"agent:main:main",
              "messageId":"m-user-1",
              "message":{"role":"user","content":"来自 Gateway 的测试"}
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatFinal

        assertEquals("m-user-1", event.turnId)
        assertEquals(ChatRole.USER, event.role)
        assertEquals("来自 Gateway 的测试", event.text)
    }

    @Test
    fun sessionMessage_mapsAssistantContentBlocks() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "messageId":"m-asst-1",
              "message":{"role":"assistant","content":[
                {"type":"thinking","thinking":"internal"},
                {"type":"text","text":"可见回复"}
              ]}
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatFinal

        assertEquals("m-asst-1", event.turnId)
        assertEquals("可见回复", event.text)
    }

    @Test
    fun sessionMessage_skipsThinkingOnlyPayload() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "messageId":"m-think-1",
              "message":{"role":"assistant","content":[
                {"type":"thinking","thinking":"only thinking"}
              ]}
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.AssistantThinking

        assertEquals("m-think-1", event.turnId)
    }

    @Test
    fun sessionMessage_mapsChatFinal() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "id":"m2","role":"user","text":"主聊天一句话","final":true
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatFinal

        assertEquals("m2", event.turnId)
        assertEquals(ChatRole.USER, event.role)
        assertEquals("主聊天一句话", event.text)
    }

    @Test
    fun sessionOperation_mapsIntentPreloadWhenModulePresent() {
        val json = """
            {"type":"event","event":"session.operation","payload":{
              "turnId":"t1","module":"WHITEBOARD","drawerState":"EXPANDED"
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.IntentPreload

        assertEquals(ModuleId.WHITEBOARD, event.preload.targetModule)
        assertEquals("t1", event.preload.turnId)
    }

    @Test
    fun sessionMessage_mapsAssistantModalFromDeltaText() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "messageId":"m-modal-dt",
              "message":{"role":"assistant","content":[]},
              "deltaText":"===CHAT===\n已打开。\n\n===MODAL===\n{\"action\":\"open\",\"blocks\":[{\"id\":\"w\",\"type\":\"webview\",\"data\":{\"url\":\"/__openclaw__/canvas/webview_spec_test.html\",\"fillHeight\":true}}]}\n===END==="
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatFinal

        assertTrue(event.text.contains("===MODAL==="))
        val next = com.littlehelper.shell.session.SessionReducer.reduce(
            com.littlehelper.shell.model.ShellUiState(sessionId = "s1"),
            event
        )
        assertTrue(next.modalState.isOpen)
    }

    @Test
    fun sessionMessage_prefersWireModalOverShortDeltaText() {
        val json = """
            {"type":"event","event":"session.message","payload":{
              "messageId":"m-modal-mix",
              "deltaText":"WebView 能力测试页面，加载看看？",
              "message":{"role":"assistant","content":[
                {"type":"text","text":"===CHAT===\nWebView 能力测试页面，加载看看？\n\n===MODAL===\n{\"action\":\"open\",\"blocks\":[{\"id\":\"w\",\"type\":\"webview\",\"data\":{\"url\":\"/__openclaw__/canvas/webview_spec_test.html\",\"fillHeight\":true}}]}\n===END==="}
              ]}
            }}
        """.trimIndent()

        val event = GatewayEventMapper.mapGatewayMessage(json, "s1") as ClawSessionEvent.ChatFinal

        assertTrue(event.text.contains("===MODAL==="))
    }

    @Test
    fun tick_isIgnored() {
        val json = """{"type":"event","event":"tick","payload":{}}"""
        assertEquals(null, GatewayEventMapper.mapGatewayMessage(json, "s1"))
    }
}
