package com.littlehelper.shell.transport

/** OpenClaw Gateway v4 RPC 方法名（req.method）。 */
object OpenClawGatewayMethods {
    const val CONNECT = "connect"
    /** 发消息（隐式创建 session）；与 PC 客户端一致。 */
    const val CHAT_SEND = "chat.send"
    /** 订阅 session 消息流（chat.delta / session.message 推送必需）。 */
    const val SESSIONS_MESSAGES_SUBSCRIBE = "sessions.messages.subscribe"
    const val TALK_SESSION_CREATE = "talk.session.create"
    const val TALK_SESSION_APPEND_AUDIO = "talk.session.appendAudio"
    const val TALK_SESSION_START_TURN = "talk.session.startTurn"
    const val TALK_SESSION_END_TURN = "talk.session.endTurn"
    const val TALK_SESSION_CANCEL_TURN = "talk.session.cancelTurn"
    const val TALK_SESSION_CLOSE = "talk.session.close"
}
