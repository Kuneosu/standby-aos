package dev.kwonsu.standby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
internal fun SceneLayerList(scene: StandbyScene, selected: String?, sharedIds: Set<String>, enabled: Boolean,
    onSelect: (String) -> Unit, editableIds: Set<String>? = null,
) {
    val labels = scene.elements.groupBy { it.kind }.flatMap { (kind, items) ->
        items.mapIndexed { index, e -> e.id to (kind.label + if (items.size > 1) " ${index + 1}" else "") }
    }.toMap()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("화면 앞쪽에 있는 요소부터 표시합니다.", style = MaterialTheme.typography.bodySmall)
        scene.elements.asReversed().filter { editableIds == null || it.id in editableIds }.forEach { element ->
            OutlinedCard(onClick = { onSelect(element.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("layer_pick_${element.id}"),
                colors = CardDefaults.outlinedCardColors(containerColor = if (selected == element.id)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.getValue(element.id), Modifier.weight(1f))
                    Text(when {
                        element.id in sharedIds -> "공통 배치"
                        element.positionLocked -> "고정"
                        selected == element.id -> "선택됨"
                        else -> ""
                    }, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (scene.elements.isEmpty()) Text("요소 탭에서 먼저 요소를 추가해 주세요.")
    }
}

@Composable
internal fun FullscreenSceneEditor(scene: StandbyScene, selectedId: String?, busy: Boolean,
    onSelect: (String?) -> Unit, onChange: (SceneElement) -> Unit, onClose: () -> Unit, onSave: () -> Unit,
    alignment: Boolean, geometryReadOnlyIds: Set<String> = emptySet(), editableIds: Set<String>? = null,
    styleEditable: Boolean = true, saveLabel: String = "저장", saveTag: String = "save_scene", message: String = "",
    onEditShared: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val power by remember(context) { powerStates(context) }.collectAsStateWithLifecycle(initialValue = readPower(context))
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var cleanView by rememberSaveable { mutableStateOf(false) }
    var toolsAtTop by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableFloatStateOf(0.5f) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    BackHandler { if (panel != null) panel = null else if (cleanView) cleanView = false else onClose() }
    LandscapeFrame(FoldInfo(), DevicePosture.LANDSCAPE, view.display?.rotation ?: 0) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("fullscreen_editor")) {
            val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val canvasHeightDp = maxHeight.value
            val selected = scene.elements.firstOrNull { it.id == selectedId }
            SceneCanvas(scene, power, now, Modifier.fillMaxSize(), editing = !cleanView && !busy,
                selectedId = selectedId, onSelect = { onSelect(it) }, onChange = onChange,
                alignmentAssist = alignment, geometryReadOnlyIds = geometryReadOnlyIds, editableIds = editableIds,
                brightness = brightness, onBrightness = { brightness = it })
            if (cleanView) TextButton({ cleanView = false }, Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(18.dp)).testTag("show_fullscreen_tools")) {
                Text("편집 도구", color = Color.White)
            } else Surface(Modifier.align(if (toolsAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(8.dp).testTag("fullscreen_toolbar"), shape = RoundedCornerShape(18.dp), color = Color(0xED182326),
                contentColor = Color.White, shadowElevation = 6.dp) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClose, enabled = !busy, modifier = Modifier.testTag("exit_fullscreen")) { Text("닫기") }
                    TextButton({ panel = "layers" }, modifier = Modifier.testTag("fullscreen_layers")) { Text("요소 목록") }
                    Text(selected?.kind?.label ?: "선택 없음", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                    TextButton({ panel = "adjust" }, enabled = selected != null && !busy, modifier = Modifier.testTag("fullscreen_adjust")) { Text("조정") }
                    TextButton({ onSelect(null) }, enabled = selected != null, modifier = Modifier.testTag("clear_fullscreen_selection")) { Text("선택 해제") }
                    TextButton({ cleanView = true }, modifier = Modifier.testTag("fullscreen_clean_view")) { Text("화면만 보기") }
                    TextButton({ toolsAtTop = !toolsAtTop }, modifier = Modifier.testTag("move_fullscreen_tools")) { Text(if (toolsAtTop) "도구 아래로" else "도구 위로") }
                    Button(onSave, enabled = !busy, modifier = Modifier.testTag(saveTag)) { Text(saveLabel) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (message.isNotBlank() && !cleanView) Text(message,
                Modifier.align(if (toolsAtTop) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(8.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(8.dp),
                color = Color.White, style = MaterialTheme.typography.bodySmall)
            panel?.let { currentPanel ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { panel = null })
                Surface(Modifier.align(Alignment.CenterEnd).padding(12.dp).width(minOf(340.dp, maxWidth * 0.55f))
                    .fillMaxHeight().testTag("fullscreen_panel"), shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (currentPanel == "layers") "요소 선택" else "${selected?.kind?.label.orEmpty()} 조정",
                                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton({ panel = null }, modifier = Modifier.testTag("close_fullscreen_panel")) { Text("닫기") }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (currentPanel == "layers") SceneLayerList(scene, selectedId, geometryReadOnlyIds, !busy,
                                onSelect = { onSelect(it); panel = null }, editableIds = editableIds)
                            else selected?.let { element ->
                                val shared = element.id in geometryReadOnlyIds
                                if (shared) {
                                    Text("공통 배치에 연결된 요소입니다. 위치와 크기는 공통 배치에서 조정하세요.", style = MaterialTheme.typography.bodySmall)
                                    onEditShared?.let { edit -> OutlinedButton(edit, enabled = !busy,
                                        modifier = Modifier.testTag("fullscreen_edit_shared")) { Text("공통 배치 편집") } }
                                }
                                if (styleEditable && element.hasTextStyle) ElementTextControls(element, onChange, aspect, enabled = !busy)
                                PlacementControls(element, onChange, readOnly = shared || busy, font = scene.font, canvasAspect = aspect, canvasHeightDp = canvasHeightDp, now = now, power = power)
                            }
                        }
                    }
                }
            }
        }
    }
}
