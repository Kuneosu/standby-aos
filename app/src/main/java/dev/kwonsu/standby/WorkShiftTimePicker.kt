package dev.kwonsu.standby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkShiftTimePicker(openedAt: Long, busy: Boolean, onDismiss: () -> Unit, editing: Boolean = false, onConfirm: (Long) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val opened = remember(openedAt) { Instant.ofEpochMilli(openedAt).atZone(zone) }
    val state = rememberTimePickerState(opened.hour, opened.minute, is24Hour = true)
    val selected = opened.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
    val end = Instant.ofEpochMilli(workShiftDeadline(selected, zone)).atZone(zone)
    BackHandler { if (!busy) onDismiss() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF10191C))
        .clickable(remember { MutableInteractionSource() }, indication = null) {}.testTag("work_shift_time_picker")) {
        val horizontal = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (editing) "출근 시각 변경" else "출근한 시각을 선택하세요", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(opened.format(DateTimeFormatter.ofPattern("M월 d일")) + " · 퇴근 " + end.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Layout(content = { Box { TimePicker(state, layoutType = if (horizontal) TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical) } },
                modifier = Modifier.fillMaxWidth().weight(1f)) { measurables, constraints ->
                val picker = measurables.single().measure(Constraints())
                val scale = minOf(constraints.maxWidth.toFloat() / picker.width, constraints.maxHeight.toFloat() / picker.height, 1.4f)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    picker.placeWithLayer((constraints.maxWidth - picker.width) / 2, (constraints.maxHeight - picker.height) / 2) {
                        scaleX = scale; scaleY = scale
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss, enabled = !busy, modifier = Modifier.testTag("cancel_shift_time")) { Text("취소") }
                Button({ onConfirm(selected) }, enabled = !busy, modifier = Modifier.testTag("confirm_shift_time")) {
                    Text((if (editing) "%02d:%02d로 변경" else "%02d:%02d 출근").format(state.hour, state.minute))
                }
            }
        }
    }
}
