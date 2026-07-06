package com.littlehelper.shell.modal

object ModalSlotIds {
    const val FREE = "_free"
}

data class ModalSlot(
    val id: String,
    val url: String,
    val title: String? = null,
)

data class ModalSlotState(
    val slotMap: Map<String, ModalSlot> = emptyMap(),
    /** 最近使用，左端为最新。 */
    val mruList: List<String> = emptyList(),
    val activeId: String? = null,
) {
    val visibleTabIds: List<String> get() = mruList.take(ModalSlotReducer.MAX_VISIBLE_TABS)
    val overflowTabIds: List<String> get() = mruList.drop(ModalSlotReducer.MAX_VISIBLE_TABS)
    val isEmpty: Boolean get() = slotMap.isEmpty()
}
