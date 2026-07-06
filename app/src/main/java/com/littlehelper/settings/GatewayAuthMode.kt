package com.littlehelper.settings

/** 与 LoongClaw `AuthMode` 枚举值一致，便于跨端文档对照。 */
enum class GatewayAuthMode(val wire: Int) {
    TOKEN(0),
    PASSWORD(1),
    NONE(2);

    companion object {
        fun fromWire(wire: Int): GatewayAuthMode =
            entries.firstOrNull { it.wire == wire } ?: TOKEN
    }
}
