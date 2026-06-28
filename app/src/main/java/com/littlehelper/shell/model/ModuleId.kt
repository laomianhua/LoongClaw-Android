package com.littlehelper.shell.model

/** 多模态展示区模块槽位标识，与 Gateway `targetModule` 对齐。 */
enum class ModuleId(val wireValue: String) {
    NONE("NONE"),
    WHITEBOARD("WHITEBOARD"),
    STOCK("STOCK"),
    WEB("WEB");

    companion object {
        fun fromWire(value: String?): ModuleId =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
                ?: NONE
    }
}
