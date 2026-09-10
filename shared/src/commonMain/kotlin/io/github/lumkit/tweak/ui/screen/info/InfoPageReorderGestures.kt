package io.github.lumkit.tweak.ui.screen.info

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import sh.calvin.reorderable.DragGestureDetector

internal class InfoItemLongPressHub {
    var show: (() -> Unit)? = null
    var dismiss: (() -> Unit)? = null

    fun emitShow() {
        show?.invoke()
    }

    fun emitDismiss() {
        dismiss?.invoke()
    }
}

internal val LocalInfoItemLongPress = staticCompositionLocalOf { InfoItemLongPressHub() }

/**
 * 长按先回调 [onLongPress]（用于弹出 Tooltip），手指再移动超过 [slopPx] 才开始拖动排序。
 */
internal class LongPressThenSlopDragDetector(
    private val slopPx: Float,
    private val onLongPress: () -> Unit,
) : DragGestureDetector {
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            onLongPress()

            var total = Offset.Zero
            var startChange: PointerInputChange? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pressed.id }
                if (change == null || !change.pressed) {
                    return@awaitEachGesture
                }
                total += change.positionChange()
                if (total.getDistance() >= slopPx) {
                    startChange = change
                    break
                }
            }

            val start = startChange ?: return@awaitEachGesture
            onDragStart(start.position)
            onDrag(start, total)
            start.consume()
            val finished = drag(start.id) { change ->
                onDrag(change, change.positionChange())
                change.consume()
            }
            if (finished) {
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    }
}
