package com.littlehelper.shell.transport

/**
 * App 客户端类型标识。
 *
 * - HTTP 上传（`:18889/upload`）multipart 字段 `client`
 * - WebSocket `connect` 的 `params.client` 嵌套对象（`platform=android` 等）由 [OpenClawConnectHandshake] 构建
 *
 * **不可**注入 WebSocket `sessions.send` 等 RPC 的 `params`——Gateway schema 不允许多余字段，会导致发消息后断连。
 */
object GatewayClientIdentity {
    const val CLIENT = "android-app"
}
