package com.littlehelper.ui.layout

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.littlehelper.PanelState
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.modal.ModalState
import com.littlehelper.shell.modules.ModuleHost
import com.littlehelper.ui.theme.AppColors
import kotlinx.coroutines.launch

/** COLLAPSED 抽屉固定高度。 */
object PanelLayout {
    val openClawCollapsedHeight = 28.dp
    const val animationDurationMillis = 300
    const val modalAnimationDurationMillis = 180
    val heightAnimationSpec = tween<Dp>(
        durationMillis = animationDurationMillis,
        easing = FastOutSlowInEasing
    )
    val modalHeightAnimationSpec = tween<Dp>(
        durationMillis = modalAnimationDurationMillis,
        easing = FastOutLinearInEasing
    )
    val modalContentSizeAnimationSpec = tween<IntSize>(
        durationMillis = modalAnimationDurationMillis,
        easing = FastOutLinearInEasing
    )
    val modalScrollAnimationSpec = tween<Float>(
        durationMillis = modalAnimationDurationMillis,
        easing = FastOutLinearInEasing
    )
    val contentSizeAnimationSpec = tween<IntSize>(
        durationMillis = animationDurationMillis,
        easing = FastOutSlowInEasing
    )
    val scrollAnimationSpec = tween<Float>(
        durationMillis = animationDurationMillis,
        easing = FastOutSlowInEasing
    )

    fun collapsedHeightFor(@Suppress("UNUSED_PARAMETER") shellMode: com.littlehelper.shell.model.ShellMode): Dp =
        openClawCollapsedHeight
}

private val panelShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

@Composable
fun MultiFunctionPanel(
    panelHeight: Dp,
    panelState: PanelState,
    activeModule: ModuleId,
    moduleLoadState: ModuleLoadState,
    modulePayload: ModulePayload,
    modalState: ModalState,
    modalSlots: com.littlehelper.shell.modal.ModalSlotState = com.littlehelper.shell.modal.ModalSlotState(),
    onSelectModalTab: (String) -> Unit = {},
    onCloseModalTab: (String) -> Unit = {},
    onOpenCanvasAmap: () -> Unit = {},
    gatewayBaseUrl: String = "",
    onRequestExpand: () -> Unit,
    onRequestCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(panelShape)
            .background(color = AppColors.panelSurface)
            .border(
                width = 0.5.dp,
                color = AppColors.panelBorder,
                shape = panelShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (panelState == PanelState.COLLAPSED) onRequestExpand()
                    else onRequestCollapse()
                }
                .pointerInput(panelState) {
                    detectVerticalDragGestures { _, dragAmount ->
                        scope.launch {
                            if (dragAmount < -12f) onRequestExpand()
                            else if (dragAmount > 12f) onRequestCollapse()
                        }
                    }
                }
                .padding(top = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(AppColors.handle, RoundedCornerShape(2.dp))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (panelState == PanelState.EXPANDED) Modifier.weight(1f)
                    else Modifier.height(0.dp)
                )
                .clip(panelShape)
        ) {
            ModuleHost(
                activeModule = activeModule,
                moduleLoadState = moduleLoadState,
                modulePayload = modulePayload,
                modalState = modalState,
                modalSlots = modalSlots,
                onSelectModalTab = onSelectModalTab,
                onCloseModalTab = onCloseModalTab,
                onOpenCanvasAmap = onOpenCanvasAmap,
                gatewayBaseUrl = gatewayBaseUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
