package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModalHistorySwipeGestureTest {

    @Test
    fun swipeLeft_whenHorizontalDominates() {
        assertEquals(+1, resolveModalHistorySwipeDelta(totalX = -80f, totalY = 20f))
    }

    @Test
    fun swipeRight_whenHorizontalDominates() {
        assertEquals(-1, resolveModalHistorySwipeDelta(totalX = 80f, totalY = -15f))
    }

    @Test
    fun noSwipe_whenVerticalScrollDominates() {
        assertNull(resolveModalHistorySwipeDelta(totalX = -60f, totalY = 200f))
        assertNull(resolveModalHistorySwipeDelta(totalX = 55f, totalY = -180f))
    }

    @Test
    fun noSwipe_whenHorizontalDistanceTooSmall() {
        assertNull(resolveModalHistorySwipeDelta(totalX = -40f, totalY = 5f))
    }

    @Test
    fun noSwipe_whenDiagonalButNotHorizontalEnough() {
        assertNull(resolveModalHistorySwipeDelta(totalX = -70f, totalY = -60f))
    }
}
