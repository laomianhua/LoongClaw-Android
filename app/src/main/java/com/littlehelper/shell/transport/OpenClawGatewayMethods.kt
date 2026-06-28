package com.littlehelper.shell.transport

/** OpenClaw Gateway v4 RPC 方法名（req.method）。 */
object OpenClawGatewayMethods {
    const val CONNECT = "connect"
    /** 订阅 session 索引变更（新增/删除），params 可空 `{}`。 */
    const val SESSIONS_SUBSCRIBE = "sessions.subscribe"
    /** 订阅某个 session 的消息流（session.message / session.operation / session.tool）。 */
    const val SESSIONS_MESSAGES_SUBSCRIBE = "sessions.messages.subscribe"
    const val SESSIONS_SEND = "sessions.send"
    const val SESSIONS_PATCH = "sessions.patch"
    const val SESSIONS_CREATE = "sessions.create"
    const val TALK_SESSION_CREATE = "talk.session.create"
    const val TALK_SESSION_APPEND_AUDIO = "talk.session.appendAudio"
    const val TALK_SESSION_START_TURN = "talk.session.startTurn"
    const val TALK_SESSION_END_TURN = "talk.session.endTurn"
    const val TALK_SESSION_CANCEL_TURN = "talk.session.cancelTurn"
    const val TALK_SESSION_CLOSE = "talk.session.close"
}
