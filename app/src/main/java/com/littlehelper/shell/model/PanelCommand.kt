package com.littlehelper.shell.model

import com.littlehelper.PanelState

/** Gateway 下发的抽屉 UI 指令，一次性消费。 */
enum class PanelCommand(val wireValue: String) {
    EXPANDED("EXPANDED"),
    COLLAPSED("COLLAPSED");

    fun toPanelState(): PanelState = when (this) {
        EXPANDED -> PanelState.EXPANDED
        COLLAPSED -> PanelState.COLLAPSED
    }

    companion object {
        fun fromWire(value: String?): PanelCommand? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}
