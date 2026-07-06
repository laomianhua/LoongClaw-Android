package com.littlehelper.shell.modal

import com.littlehelper.shell.modules.ModalCanvasUrlResolver
import com.littlehelper.shell.transport.GatewayCanvasAuth

object ModalSlotReducer {

    const val MAX_VISIBLE_TABS = 6

    fun findPrimaryWebViewBlock(blocks: List<ModalBlock>): ModalBlock? =
        blocks.firstOrNull { block ->
            block.type.equals("webview", ignoreCase = true) && block.id.isNotBlank()
        }

    /** 当前 MODAL 仅含 table/markdown/chart 等原生块，不含 webview。 */
    fun isNativeOnlyModal(blocks: List<ModalBlock>): Boolean =
        blocks.isNotEmpty() && findPrimaryWebViewBlock(blocks) == null

    fun buildSlot(
        block: ModalBlock,
        loadRevision: Long,
        gatewayBaseUrl: String,
    ): ModalSlot? {
        val resolved = ModalCanvasUrlResolver.resolve(block.data.get("url")?.asString, gatewayBaseUrl)
            ?: return null
        val url = ModalCanvasUrlResolver.appendLoadRevision(
            GatewayCanvasAuth.prepareCanvasLoadUrl(resolved),
            loadRevision
        )
        return ModalSlot(
            id = block.id,
            url = url,
            title = block.title
        )
    }

    fun openOrUpdate(
        slots: ModalSlotState,
        blocks: List<ModalBlock>,
        loadRevision: Long,
        gatewayBaseUrl: String,
    ): ModalSlotState {
        val block = findPrimaryWebViewBlock(blocks) ?: return slots
        val slot = buildSlot(block, loadRevision, gatewayBaseUrl) ?: return slots
        return upsertSlot(slots, slot)
    }

    fun selectTab(slots: ModalSlotState, id: String): ModalSlotState {
        if (!slots.slotMap.containsKey(id)) return slots
        return slots.copy(mruList = promoteMru(slots.mruList, id), activeId = id)
    }

    fun closeTab(slots: ModalSlotState, id: String?): ModalSlotState {
        val targetId = id?.takeIf { slots.slotMap.containsKey(it) }
            ?: slots.activeId
            ?: return slots
        if (!slots.slotMap.containsKey(targetId)) return slots

        val newMap = slots.slotMap - targetId
        val newMru = slots.mruList.filter { it != targetId }
        val newActive = when {
            newMru.isEmpty() -> null
            slots.activeId == targetId -> newMru.first()
            else -> slots.activeId
        }
        return slots.copy(slotMap = newMap, mruList = newMru, activeId = newActive)
    }

    private fun upsertSlot(slots: ModalSlotState, slot: ModalSlot): ModalSlotState {
        val newMap = slots.slotMap + (slot.id to slot)
        val newMru = promoteMru(slots.mruList, slot.id)
        return slots.copy(slotMap = newMap, mruList = newMru, activeId = slot.id)
    }

    private fun promoteMru(mruList: List<String>, id: String): List<String> =
        listOf(id) + mruList.filter { it != id }
}
