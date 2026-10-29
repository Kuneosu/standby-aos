package dev.kwonsu.standby

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PresetControls(library: PresetLibrary, busy: Boolean, dirty: Boolean, onAction: (PresetAction) -> Unit,
    useSharedLayout: Boolean = false, canCapture: Boolean = true, onEditSharedLayout: () -> Unit = {}, selectedPresetId: String? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamed by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<PresetAction?>(null) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("공통 배치", style = MaterialTheme.typography.titleMedium)
            Text("현재 화면의 요소 중 함께 배치할 대상을 고르세요. 위치·크기·고정 여부를 공유하고 색상·폰트·미디어·배경은 각 프리셋에 보관합니다.", style = MaterialTheme.typography.bodySmall)
            Text("연결된 프리셋에 없는 요소는 기준 요소의 설정으로 자동 추가됩니다.", style = MaterialTheme.typography.bodySmall)
            if (library.sharedLayout == null) {
                Button(onEditSharedLayout, enabled = !busy && canCapture,
                    modifier = Modifier.testTag("capture_shared_layout")) { Text("공통 배치 만들기") }
            } else {
                Text("공통 요소 ${library.sharedLayout.placements.size}개 · 연결된 프리셋 ${library.presets.count { it.useSharedLayout }}개", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onEditSharedLayout, enabled = !busy, modifier = Modifier.testTag("edit_shared_layout")) { Text("공통 배치 편집") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("현재 편집 화면에도 사용", Modifier.weight(1f))
                    Switch(useSharedLayout, { onAction(PresetAction.UseSharedLayout(null, it)) }, enabled = !busy,
                        modifier = Modifier.testTag("current_shared_layout"))
                }
            }
            Text("연결을 끄면 각 프리셋에 보관한 원래 배치로 돌아갑니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("지금 편집한 화면을 이름 붙여 보관하세요. 불러온 프리셋을 수정한 뒤 상단 저장을 누르면 프리셋에도 반영할지 선택할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().testTag("preset_name"), label = { Text("새 프리셋 이름") }, singleLine = true)
    Button(onClick = { onAction(PresetAction.Save(name)); name = "" },
        enabled = !busy && name.isNotBlank() && library.presets.size < PresetLibrary.MAX_PRESETS,
        modifier = Modifier.testTag("save_preset")) { Text("현재 화면을 프리셋으로 저장") }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("프리셋 순환", style = MaterialTheme.typography.titleMedium)
            Text("새 스탠바이 진입마다 다음 화면 · 위아래 스와이프로 다음 화면", style = MaterialTheme.typography.bodySmall)
        }
        Switch(library.cycleEnabled, { onAction(PresetAction.Enable(it)) }, enabled = !busy, modifier = Modifier.testTag("cycle_enabled"))
    }
    Text("아래 목록에 직접 추가한 프리셋만 순서대로 표시됩니다. 목록이 비어 있거나 순환을 끄면 현재 저장된 디자인을 표시합니다.", style = MaterialTheme.typography.bodySmall)
    if (library.cycleIds.isEmpty()) Text("순환 목록이 비어 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    library.cycleIds.forEachIndexed { index, id ->
        val preset = library.presets.first { it.id == id }
        OutlinedCard(Modifier.fillMaxWidth().testTag("cycle_$id")) {
            Column(Modifier.padding(12.dp)) {
                Text("${index + 1}. ${preset.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onAction(PresetAction.Move(id, -1)) }, enabled = !busy && index > 0, modifier = Modifier.testTag("cycle_up_$id")) { Text("위로") }
                    TextButton({ onAction(PresetAction.Move(id, 1)) }, enabled = !busy && index < library.cycleIds.lastIndex, modifier = Modifier.testTag("cycle_down_$id")) { Text("아래로") }
                    TextButton({ onAction(PresetAction.Include(id, false)) }, enabled = !busy, modifier = Modifier.testTag("cycle_remove_$id")) { Text("목록에서 빼기") }
                }
            }
        }
    }
    HorizontalDivider()
    Text("저장한 프리셋 ${library.presets.size}/${PresetLibrary.MAX_PRESETS}", style = MaterialTheme.typography.titleMedium)
    library.presets.forEach { preset ->
        Card(Modifier.fillMaxWidth().testTag("preset_${preset.id}")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (preset.id == selectedPresetId) Text("선택됨", Modifier.testTag("selected_preset_${preset.id}"),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                Text("${preset.scene.font.label} · 요소 ${preset.scene.elements.size}개", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("공통 배치 사용", Modifier.weight(1f))
                    Switch(preset.useSharedLayout, { onAction(PresetAction.UseSharedLayout(preset.id, it)) },
                        enabled = !busy && (library.sharedLayout != null || preset.useSharedLayout),
                        modifier = Modifier.testTag("shared_preset_${preset.id}"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ val action = PresetAction.Load(preset.id); if (dirty) confirmation = action else onAction(action) }, enabled = !busy,
                        modifier = Modifier.testTag("load_preset_${preset.id}")) { Text("불러오기") }
                    TextButton({ renameId = preset.id; renamed = preset.name }, enabled = !busy) { Text("이름") }
                    TextButton({ confirmation = PresetAction.Save(preset.name, preset.id) }, enabled = !busy,
                        modifier = Modifier.testTag("overwrite_preset_${preset.id}")) { Text("덮어쓰기") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onAction(PresetAction.Include(preset.id, true)) }, enabled = !busy && preset.id !in library.cycleIds,
                        modifier = Modifier.testTag("include_preset_${preset.id}")) { Text(if (preset.id in library.cycleIds) "순환에 등록됨" else "순환에 추가") }
                    TextButton({ confirmation = PresetAction.Delete(preset.id) }, enabled = !busy,
                        modifier = Modifier.testTag("delete_preset_${preset.id}")) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (renameId != null) AlertDialog(onDismissRequest = { renameId = null }, title = { Text("프리셋 이름") },
        text = { OutlinedTextField(renamed, { renamed = it.take(40) }, singleLine = true, modifier = Modifier.testTag("rename_preset_name")) },
        confirmButton = { TextButton({ onAction(PresetAction.Rename(renameId!!, renamed)); renameId = null }, enabled = renamed.isNotBlank() && !busy) { Text("이름 저장") } },
        dismissButton = { TextButton({ renameId = null }) { Text("취소") } })
    confirmation?.let { action ->
        val message = when (action) {
            is PresetAction.Load -> "저장하지 않은 편집 내용을 선택한 프리셋으로 바꿀까요?"
            is PresetAction.Delete -> "이 프리셋을 삭제하고 순환 목록에서도 뺄까요? 현재 편집 화면은 유지됩니다."
            else -> "현재 편집한 화면으로 이 프리셋을 덮어쓸까요?"
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("프리셋 변경") }, text = { Text(message) },
            confirmButton = { TextButton({ onAction(action); confirmation = null }, enabled = !busy, modifier = Modifier.testTag("confirm_preset_action")) { Text("확인") } },
            dismissButton = { TextButton({ confirmation = null }) { Text("취소") } })
    }
}
