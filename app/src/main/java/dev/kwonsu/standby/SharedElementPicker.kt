package dev.kwonsu.standby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class SharedElementDrag(val slot: LayoutSlot, val included: Boolean, val label: String, val pointer: Offset)

/** 두 영역 사이의 이동만 선택에 반영한다. 영역 밖에 놓거나 취소하면 기존 선택을 유지한다. */
@Composable
fun SharedElementPicker(layout: SharedLayout, reference: StandbyScene, enabled: Boolean, onChange: (SharedLayout) -> Unit) {
    val current by rememberUpdatedState(layout)
    val change by rememberUpdatedState(onChange)
    val entries = reference.layoutSlots().toMap()
    val selected = layout.placements.map { it.slot }.toSet()
    var drag by remember { mutableStateOf<SharedElementDrag?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var localBounds by remember { mutableStateOf(Rect.Zero) }
    var sharedBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val ghostWidth = with(density) { 156.dp.toPx() }
    val toggle: (LayoutSlot, Boolean) -> Unit = { slot, included ->
        if (enabled && (!included || current.placements.size < StandbyScene.MAX_ELEMENTS))
            change(current.withSelection(reference, slot, included))
    }
    val finishDrag: () -> Unit = {
        drag?.let { item ->
            if (enabled && !item.included && sharedBounds.contains(item.pointer)) toggle(item.slot, true)
            else if (enabled && item.included && localBounds.contains(item.pointer)) toggle(item.slot, false)
        }
        drag = null
    }
    val finish by rememberUpdatedState(finishDrag)
    val move by rememberUpdatedState(toggle)
    LaunchedEffect(enabled) { if (!enabled) drag = null }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("shared_element_picker").onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        val pickerWidth = constraints.maxWidth.toFloat()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false, true).forEach { included ->
                val slots = if (included) layout.placements.map { it.slot } else entries.keys.filterNot { it in selected }
                val bounds = if (included) sharedBounds else localBounds
                val over = drag?.let { it.included != included && bounds.contains(it.pointer) } == true
                OutlinedCard(Modifier.weight(1f).fillMaxHeight().testTag(if (included) "shared_selected_zone" else "shared_available_zone")
                    .onGloballyPositioned { if (included) sharedBounds = it.boundsInRoot() else localBounds = it.boundsInRoot() },
                    border = BorderStroke(if (over) 2.dp else 1.dp, if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (over) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (included) "공통 배치 요소" else "현재 화면 요소", style = MaterialTheme.typography.titleSmall)
                        Text("${slots.size}개", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (slots.isEmpty()) Text(if (included) "함께 배치할 요소를\n이곳으로 옮기세요." else "공유하지 않는 요소가\n여기에 표시됩니다.",
                                Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            slots.forEach { slot -> key(slot) {
                                var itemBounds by remember { mutableStateOf(Rect.Zero) }
                                val repeated = reference.elements.count { it.kind == slot.kind } > 1 || slot.occurrence > 0
                                val label = slot.kind.label + if (repeated) " ${slot.occurrence + 1}" else ""
                                val tag = "${slot.kind}_${slot.occurrence}"
                                val canMove = enabled && (included || selected.size < StandbyScene.MAX_ELEMENTS)
                                Surface(Modifier.fillMaxWidth().testTag("shared_item_${if (included) "selected" else "available"}_$tag")
                                    .onGloballyPositioned { itemBounds = it.boundsInRoot() }
                                    .pointerInput(slot, included, canMove) {
                                        if (canMove) detectDragGesturesAfterLongPress(
                                            onDragStart = { drag = SharedElementDrag(slot, included, label, itemBounds.topLeft + it) },
                                            onDrag = { event, amount -> event.consume(); drag = drag?.let { it.copy(pointer = it.pointer + amount) } },
                                            onDragEnd = { finish() }, onDragCancel = { drag = null })
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (drag?.slot == slot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
                                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (slot !in entries) Text("현재 화면에 없음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton({ move(slot, !included) }, enabled = canMove,
                                            modifier = Modifier.align(Alignment.End).testTag("shared_toggle_$tag")
                                                .semantics { contentDescription = "$label ${if (included) "공통 배치에서 제외" else "공통 배치에 추가"}" },
                                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (included) "← 제외" else "추가 →") }
                                    }
                                }
                            } }
                        }
                    }
                }
            }
        }
        drag?.let { item ->
            Surface(Modifier.offset {
                IntOffset((item.pointer.x - origin.x - ghostWidth / 2).coerceIn(0f, (pickerWidth - ghostWidth).coerceAtLeast(0f)).roundToInt(),
                    (item.pointer.y - origin.y - 24.dp.toPx()).roundToInt())
            }.width(156.dp).testTag("shared_drag_preview"), shadowElevation = 8.dp, shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Text(item.label, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
