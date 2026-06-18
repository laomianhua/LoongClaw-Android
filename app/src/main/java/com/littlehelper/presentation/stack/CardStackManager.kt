package com.littlehelper.presentation.stack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 多层抽屉堆栈管理器：控制标签切换与可扩展卡片注册。
 * 未来追加「股市」「航班」等模块时，扩展 [DrawerCard] 并调用 [addCardToStack]。
 */
class CardStackManager(
    initialCard: DrawerCard = DrawerCard.NOTEBOOK,
    cards: MutableList<DrawerCard> = mutableListOf(DrawerCard.NOTEBOOK, DrawerCard.MAP)
) {
    private val _cards = cards

    val registeredCards: List<DrawerCard> get() = _cards.toList()

    var activeCard by mutableStateOf(initialCard)
        private set

    fun selectCard(card: DrawerCard) {
        if (card in _cards) {
            activeCard = card
        }
    }

    fun switchToCard(card: DrawerCard) = selectCard(card)

    fun addCardToStack(card: DrawerCard) {
        if (card !in _cards) {
            _cards.add(card)
        }
    }
}
