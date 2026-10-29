package dev.kwonsu.standby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalDateTime

private val sharedLayoutSaver = Saver<SharedLayout, String>(save = { SharedLayoutCodec.encode(it) }, restore = { SharedLayoutCodec.decode(it) })

@Composable
fun SharedLayoutEditor(initial: SharedLayout, reference: StandbyScene, linkedCount: Int, busy: Boolean, message: String,
    onSave: (SharedLayout) -> Unit, onDismiss: () -> Unit,
    fullscreen: Boolean = false, onFullscreenChange: (Boolean) -> Unit = {},
) {
    var layout by rememberSaveable(stateSaver = sharedLayoutSaver) { mutableStateOf(initial) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var canvasHeightDp by remember { mutableFloatStateOf(300f) }
    val preview = layout.previewScene(reference)
    val targets = layout.previewTargets(reference)
    val activeId = selected?.takeIf { it in targets } ?: if (fullscreen) null else targets.keys.firstOrNull()
    val exit = { if (!busy) { if (layout != initial) confirmExit = true else onDismiss() } }
    val change: (SceneElement) -> Unit = { if (!busy) layout = layout.updateElement(reference, it) }

    Dialog(onDismissRequest = exit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = exit)
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow, fullscreen) {
            dialogWindow?.let { window -> WindowCompat.getInsetsController(window, window.decorView).apply {
                if (fullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else show(WindowInsetsCompat.Type.systemBars())
            } }
        }
        Surface(Modifier.fillMaxSize().testTag("shared_editor")) {
            if (fullscreen) FullscreenSceneEditor(preview, activeId, busy, onSelect = { selected = it }, onChange = change,
                onClose = { onFullscreenChange(false) }, onSave = { onSave(layout.normalized()) }, alignment = true,
                editableIds = targets.keys, styleEditable = false, saveLabel = "공통 저장", saveTag = "save_shared_layout",
                message = message.ifBlank { "공통 배치 · 저장하면 연결된 프리셋 ${linkedCount}개에 적용됩니다." })
            else Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(exit, enabled = !busy, modifier = Modifier.testTag("cancel_shared_layout")) { Text("닫기") }
                    Text("공통 배치 편집", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Button({ onSave(layout.normalized()) }, enabled = !busy && layout != initial,
                        modifier = Modifier.testTag("save_shared_layout")) { Text("공통 저장") }
                }
                Text("저장하면 연결된 프리셋 ${linkedCount}개에 적용됩니다. 각 프리셋의 원래 배치는 보관합니다.", style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotBlank() && !busy) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, label = { Text("공유할 요소") }, modifier = Modifier.testTag("shared_tab_elements"))
                    FilterChip(tab == 1, { tab = 1 }, label = { Text("배치 조정") }, modifier = Modifier.testTag("shared_tab_placement"))
                }
                if (tab == 0) {
                    Text("요소를 길게 눌러 반대쪽으로 옮기거나 추가·제외를 누르세요. 화면에서 요소를 삭제하지는 않습니다.", style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f)) { SharedElementPicker(layout, reference, !busy) { layout = it } }
                    Text("같은 종류는 번호로 대응합니다. 연결된 프리셋에 없는 요소는 자동 추가되며, 기존 요소의 색상·내용은 유지합니다.", style = MaterialTheme.typography.bodySmall)
                } else BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 700.dp && maxWidth > maxHeight
                    val canvas: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ selected = activeId; onFullscreenChange(true) }, modifier = Modifier.testTag("shared_open_fullscreen")) {
                                Text("전체 화면 편집")
                            }
                            Box(Modifier.fillMaxWidth().aspectRatio(2.45f).onSizeChanged { canvasHeightDp = it.height / density.density }.testTag("shared_canvas")) {
                                SceneCanvas(preview, PowerState(percent = 82, plugged = 1, charging = true), LocalDateTime.now(), Modifier.fillMaxSize(),
                                    editing = !busy, editableIds = targets.keys, selectedId = activeId, onSelect = { selected = it }, onChange = change)
                            }
                            Text("공통 요소를 끌어 이동하고 네 모서리로 크기를 조절하세요. 선택하지 않은 요소는 배치 확인용입니다.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val controls: @Composable () -> Unit = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                targets.forEach { (id, placement) ->
                                    FilterChip(activeId == id, { selected = id }, label = {
                                        Text(placement.slot.kind.label + if (placement.slot.occurrence > 0) " ${placement.slot.occurrence + 1}" else "")
                                    }, modifier = Modifier.testTag("shared_select_${placement.slot.kind}_${placement.slot.occurrence}"))
                                }
                            }
                            preview.elements.firstOrNull { it.id == activeId }?.let { element ->
                                PlacementControls(element, change, readOnly = busy, tagPrefix = "shared_", font = preview.font, canvasHeightDp = canvasHeightDp)
                            }
                            Text("이 화면에서는 위치·크기·고정 여부만 바뀝니다. 같은 종류가 여러 개면 요소 목록에 나타나는 순서대로 연결됩니다.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1.2f)) { canvas() }
                        Box(Modifier.weight(1f)) { controls() }
                    } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        canvas()
                        Box(Modifier.weight(1f)) { controls() }
                    }
                }
            }
        }
        if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("공통 배치 편집을 닫을까요?") },
            text = { Text("저장하지 않은 공통 배치 변경만 버립니다. 기존 프리셋은 바뀌지 않습니다.") },
            confirmButton = { TextButton(onDismiss, modifier = Modifier.testTag("discard_shared_layout")) { Text("변경 버리기") } },
            dismissButton = { TextButton({ confirmExit = false }) { Text("계속 편집") } })
    }
}
