package com.pos.kiosk.core.session

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

val LocalUserInteraction = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Dialogs are separate windows, so their touches never reach MainActivity.dispatchTouchEvent.
 * Put this on a dialog's root so choosing options doesn't trip the idle timeout.
 *
 * Reports presses only (like MainActivity's ACTION_DOWN), never moves: a scroll gesture would
 * otherwise write the idle clock on every frame.
 */
fun Modifier.reportUserInteraction(): Modifier = this then ReportUserInteractionElement

private data object ReportUserInteractionElement : ModifierNodeElement<ReportUserInteractionNode>() {
    override fun create() = ReportUserInteractionNode()
    override fun update(node: ReportUserInteractionNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "reportUserInteraction"
    }
}

private class ReportUserInteractionNode : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press) {
            currentValueOf(LocalUserInteraction)()
        }
    }

    override fun onCancelPointerInput() = Unit
}
