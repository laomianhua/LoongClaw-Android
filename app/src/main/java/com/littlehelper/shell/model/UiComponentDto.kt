package com.littlehelper.shell.model

/**
 * OpenClaw 白板原子组件 DTO（Gateway JSON → 领域层）。
 * [type] 约定：text / icon / metric / chart / divider / row …
 */
data class UiComponentDto(
    val id: String,
    val type: String,
    val text: String? = null,
    val icon: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<UiComponentDto> = emptyList()
)
