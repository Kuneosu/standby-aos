package dev.kwonsu.standby

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun SwipeNotificationRow(entry: NotificationEntry, onDismiss: (String) -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val remove by rememberUpdatedState(onDismiss)
    val distance = min(width * 0.35f, with(LocalDensity.current) { 96.dp.toPx() })
    val offset by animateFloatAsState(if (removing) -width else drag,
        if (dragging) snap() else if (removing) tween(240, easing = NotificationMotion.Settle) else spring(dampingRatio = 1f, stiffness = 650f), label = "알림 제거",
        finishedListener = { if (removing) remove(entry.key) })
    Box(Modifier.fillMaxWidth().clipToBounds().onSizeChanged { width = it.width.toFloat() }
        .testTag("notification_${entry.key}").semantics {
            customActions = if (enabled) listOf(CustomAccessibilityAction("스탠바이 기록에서 제거") { remove(entry.key); true }) else emptyList()
        }.pointerInput(entry.key, distance, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var total = Offset.Zero
                var horizontal = false
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        total += change.positionChange()
                        if (!horizontal) {
                            if (abs(total.y) > viewConfiguration.touchSlop && abs(total.y) >= abs(total.x)) break
                            if (abs(total.x) > viewConfiguration.touchSlop) {
                                // 오른쪽 드래그는 부모 패널의 닫기 동작에 넘긴다.
                                if (total.x >= 0f) break
                                horizontal = true; dragging = true
                            }
                        }
                        if (horizontal) { change.consume(); drag = total.x.coerceIn(-width, 0f) }
                        if (!change.pressed) {
                            if (horizontal && -drag >= distance) removing = true
                            break
                        }
                    }
                } finally { dragging = false; if (!removing) drag = 0f }
            }
        }) {
        if (offset < -1f) Box(Modifier.matchParentSize().background(Color(0xFFB34D45).copy(alpha = (-offset / width * 0.28f).coerceIn(0f, 0.28f))), contentAlignment = Alignment.CenterEnd) {
            Text("지우기", Modifier.padding(end = 14.dp).graphicsLayer { alpha = (-offset / distance).coerceIn(0f, 1f) }, color = Color(0xFFFFC8C1), fontSize = 12.sp)
        }
        Box(Modifier.graphicsLayer { translationX = offset; alpha = 1f - (-offset / width).coerceIn(0f, 1f) * 0.65f }) { content() }
    }
}
