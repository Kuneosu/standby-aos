package dev.kwonsu.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

/** 겹친 내용 아래에 있는 요소도 선택 후에는 같은 자리에서 조작할 수 있다. */
@Composable
internal fun SceneElementEditor(element: SceneElement, selected: Boolean, readOnly: Boolean, pixels: IntSize,
    canvasWidth: Dp, canvasHeight: Dp, onSelect: (String) -> Unit, onChange: (SceneElement) -> Unit,
    align: (SceneElement) -> SceneElement, onGestureEnd: () -> Unit,
) {
    val current by rememberUpdatedState(element)
    val changeElement by rememberUpdatedState(onChange)
    val selectElement by rememberUpdatedState(onSelect)
    val alignElement by rememberUpdatedState(align)
    val endGesture by rememberUpdatedState(onGestureEnd)
    val accent = Color(0xFFA6E8CB)
    val width = canvasWidth * element.width
    val height = canvasHeight * element.height
    Box(Modifier.offset { IntOffset((element.x * pixels.width).roundToInt(), (element.y * pixels.height).roundToInt()) }
        .size(width, height)
        .pointerInput(element.id) { detectTapGestures { selectElement(element.id) } }
        .pointerInput(element.id, pixels, element.positionLocked, readOnly) {
            var origin = current
            var delta = Offset.Zero
            detectDragGestures(onDragStart = { selectElement(element.id); origin = current; delta = Offset.Zero },
                onDragEnd = { endGesture() }, onDragCancel = { endGesture() }) { change, drag ->
                if (!current.positionLocked && !readOnly) {
                    change.consume(); delta += drag
                    changeElement(alignElement(current.copy(x = origin.x + delta.x / pixels.width,
                        y = origin.y + delta.y / pixels.height,
                        horizontalAnchor = CanvasAnchor.FREE, verticalAnchor = CanvasAnchor.FREE).normalized()))
                }
            }
        }
        .drawBehind {
            // 밀착한 글자를 덮지 않도록 선택 테두리는 영역 바깥에 그린다.
            val stroke = (if (selected) 2.dp else 0.5.dp).toPx()
            drawRect(if (selected) accent else Color.White.copy(alpha = 0.28f),
                Offset(-stroke, -stroke), Size(size.width + stroke * 2, size.height + stroke * 2), style = Stroke(stroke))
        }
        .testTag("selection_${element.id}")) {
        if (selected && (element.positionLocked || readOnly)) Text(if (readOnly) "공통" else "고정",
            Modifier.align(Alignment.TopEnd).offset(y = (-22).dp).background(Color.Black.copy(alpha = 0.7f)).padding(3.dp), color = Color.White, fontSize = 10.sp)
        if (selected && !element.positionLocked && !readOnly) ResizeCorner.entries.forEach { corner ->
            val anchor = when (corner) {
                ResizeCorner.TOP_LEFT -> Alignment.TopStart
                ResizeCorner.TOP_RIGHT -> Alignment.TopEnd
                ResizeCorner.BOTTOM_LEFT -> Alignment.BottomStart
                ResizeCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
            }
            val handleSize = minOf(24.dp, width / 2, height / 2).coerceAtLeast(6.dp)
            val tag = if (corner == ResizeCorner.BOTTOM_RIGHT) "resize_${element.id}" else "resize_${corner.name.lowercase()}_${element.id}"
            Box(Modifier.align(anchor).size(handleSize)
                .pointerInput(element.id, pixels, corner) {
                    var origin = current
                    var delta = Offset.Zero
                    detectDragGestures(onDragStart = { origin = current; delta = Offset.Zero },
                        onDragEnd = { endGesture() }, onDragCancel = { endGesture() }) { change, drag ->
                        change.consume(); delta += drag
                        val resized = origin.resizedFrom(corner, delta.x / pixels.width, delta.y / pixels.height)
                        changeElement(current.copy(x = resized.x, y = resized.y, width = resized.width, height = resized.height,
                            contentSize = resized.contentSize, horizontalAnchor = resized.horizontalAnchor, verticalAnchor = resized.verticalAnchor))
                    }
                }.testTag(tag)) {
                // 손잡이의 터치 범위는 안쪽에 유지하고 표시만 바깥으로 옮긴다.
                val marker = minOf(10.dp, handleSize)
                Box(Modifier.align(anchor).offset(x = if (corner.left) -marker else marker, y = if (corner.top) -marker else marker).size(marker)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    .border(1.5.dp, accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}
