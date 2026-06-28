package com.littlehelper.shell.modules

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

internal const val MODAL_HISTORY_SWIPE_THRESHOLD_PX = 48f
internal const val MODAL_HISTORY_HORIZONTAL_DOMINANCE_RATIO = 1.5f

/**
 * 解析白板历史横向翻页手势。
 *
 * @return +1 切到较新的一页（左滑），-1 切到较旧的一页（右滑），null 不翻页。
 */
internal fun resolveModalHistorySwipeDelta(
    totalX: Float,
    totalY: Float,
    swipeThresholdPx: Float = MODAL_HISTORY_SWIPE_THRESHOLD_PX,
    horizontalDominanceRatio: Float = MODAL_HISTORY_HORIZONTAL_DOMINANCE_RATIO,
): Int? {
    if (abs(totalX) < swipeThresholdPx) return null
    if (abs(totalX) <= abs(totalY) * horizontalDominanceRatio) return null
    return when {
        totalX <= -swipeThresholdPx -> +1
        totalX >= swipeThresholdPx -> -1
        else -> null
    }
}

@Composable
internal fun Modifier.modalHistorySwipeNavigation(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    val latestOnSwipeLeft = rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight = rememberUpdatedState(onSwipeRight)
    return pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalX = 0f
            var totalY = 0f
            var horizontalLocked: Boolean? = null

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.positionChange()
                if (delta == androidx.compose.ui.geometry.Offset.Zero) continue

                totalX += delta.x
                totalY += delta.y

                if (horizontalLocked == null) {
                    val slop = viewConfiguration.touchSlop
                    if (abs(totalX) > slop || abs(totalY) > slop) {
                        horizontalLocked = abs(totalX) > abs(totalY)
                        if (horizontalLocked == false) {
                            return@awaitEachGesture
                        }
                    }
                }

                if (horizontalLocked == true) {
                    change.consume()
                }
            }

            when (resolveModalHistorySwipeDelta(totalX, totalY)) {
                +1 -> latestOnSwipeLeft.value.invoke()
                -1 -> latestOnSwipeRight.value.invoke()
            }
        }
    }
}
