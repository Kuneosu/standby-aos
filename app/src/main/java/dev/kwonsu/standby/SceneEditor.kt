package dev.kwonsu.standby

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun SceneEditor(state: EditorState, onChange: ((StandbyScene) -> StandbyScene) -> Unit, onImport: (Uri, Boolean) -> Unit,
    onSave: () -> Unit, onSaveExit: () -> Unit, onDiscard: () -> Unit, onExit: () -> Unit,
    onPreset: (PresetAction) -> Unit = {},
    onSavePreset: (Boolean) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val scene = state.displayScene
    var selected by rememberSaveable { mutableStateOf<String?>("clock") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var canvasHeightDp by remember { mutableFloatStateOf(300f) }
    var ratio by rememberSaveable { mutableFloatStateOf(2.45f) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var alignment by rememberSaveable { mutableStateOf(true) }
    var previewBrightness by rememberSaveable { mutableFloatStateOf(0.5f) }
    var exitDialog by rememberSaveable { mutableStateOf(false) }
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    var sharedEditor by rememberSaveable { mutableStateOf(false) }
    var sharedFullscreen by rememberSaveable { mutableStateOf(false) }
    var layersDialog by rememberSaveable { mutableStateOf(false) }
    var pendingSharedSave by remember { mutableStateOf<SharedLayout?>(null) }
    var presetSaveExit by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val fullscreenChange by rememberUpdatedState(onFullscreenChange)
    LaunchedEffect(preview, sharedFullscreen) { fullscreenChange(preview || sharedFullscreen) }
    val requestSave: (Boolean) -> Unit = { leave ->
        exitDialog = false
        if (state.hasPresetChanges) presetSaveExit = leave else if (leave) onSaveExit() else onSave()
    }
    LaunchedEffect(state.busy, state.library.sharedLayout, pendingSharedSave) {
        if (!state.busy && pendingSharedSave != null && state.library.sharedLayout == pendingSharedSave) {
            sharedEditor = false; sharedFullscreen = false; pendingSharedSave = null
        }
    }
    var knownElements by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(state.loading, scene.elements.map { it.id }) {
        if (!state.loading) {
            knownElements?.let { previous ->
                scene.elements.firstOrNull { it.id !in previous && it.kind == ElementKind.GIF && it.sharedSlot == null }
                    ?.let { selected = it.id; tab = 2 }
            }
            knownElements = scene.elements.map { it.id }.toSet()
        }
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onImport(it, false) } }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onImport(it, true) } }
    val exit = { if (!state.busy) { if (state.dirty) exitDialog = true else onExit() } }
    BackHandler(onBack = exit)
    // 장면을 캡처한 로컬 함수 참조를 재사용하면 다른 요소의 이전 상태를 복원할 수 있다.
    val changeElement: (SceneElement) -> Unit = { element ->
        onChange { current -> current.copy(elements = current.elements.map {
            if (it.id == element.id) it.applyEdit(if (it.id in state.sharedElementIds) SharedPlacement.from(LayoutSlot(it.kind), it).applyTo(element) else element) else it
        }) }
    }

    Surface(Modifier.fillMaxSize()) {
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (preview) FullscreenSceneEditor(scene, selected, state.busy,
            onSelect = { selected = it }, onChange = changeElement, onClose = { preview = false }, onSave = { requestSave(false) },
            alignment = alignment, geometryReadOnlyIds = state.sharedElementIds, message = state.message,
            onEditShared = { preview = false; sharedEditor = true; sharedFullscreen = true })
        else Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = exit, enabled = !state.busy) { Text("돌아가기") }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("나만의 스탠바이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text((if (state.dirty) "저장 전 변경 사항" else "저장된 디자인") + if (state.useSharedLayout) " · 공통 배치" else "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.selectedPreset?.let { Text("선택: ${it.name}", Modifier.testTag("selected_preset_name"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
                Button(onClick = { requestSave(false) }, enabled = !state.busy, modifier = Modifier.testTag("save_scene")) { Text("저장") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.message.isNotEmpty()) Text(state.message, Modifier.padding(vertical = 4.dp).testTag("editor_message"),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 700.dp && maxWidth > maxHeight
                val previewHeight = if (wide) (maxHeight - 100.dp).coerceIn(120.dp, 360.dp) else minOf(280.dp, maxHeight * 0.42f)
                val previewContent: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("커버" to 2.45f, "16:9" to (16f / 9), "내부" to 1.2f).forEach { (label, value) ->
                                    FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(label) })
                                }
                            }
                            TextButton(onClick = { preview = true }, modifier = Modifier.testTag("open_fullscreen")) { Text("전체 화면 편집") }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val canvasWidth = minOf(maxWidth, previewHeight * ratio)
                            Box(Modifier.size(canvasWidth, canvasWidth / ratio).onSizeChanged { canvasHeightDp = it.height / density.density }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
                            SceneCanvas(scene, PowerState(percent = 82, plugged = 1, charging = true), now, Modifier.fillMaxSize(),
                                editing = !state.busy, selectedId = selected, alignmentAssist = alignment,
                                brightness = previewBrightness, onBrightness = { previewBrightness = it },
                                geometryReadOnlyIds = state.sharedElementIds,
                                onSelect = { selected = it; tab = 2 }, onChange = changeElement)
                            }
                        }
                        Text("요소를 끌어 이동 · 네 모서리로 크기 조절 · 겹친 요소는 목록에서 선택", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(alignment, { alignment = !alignment }, label = { Text("중앙 정렬 가이드") }, modifier = Modifier.testTag("alignment_assist"))
                            TextButton({ layersDialog = true }, modifier = Modifier.testTag("open_layers")) { Text("요소 목록") }
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    // 다른 탭의 긴 목록에서 내려간 위치가 요소 선택 목록을 가리지 않게 한다.
                    val controlsScroll = key(tab) { rememberScrollState() }
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("폰트·색상", "배경", "요소", "프리셋").forEachIndexed { index, title ->
                                FilterChip(tab == index, { tab = index }, label = { Text(title) }, modifier = Modifier.testTag("tab_$index"))
                            }
                        }
                        Column(Modifier.weight(1f).verticalScroll(controlsScroll).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            when (tab) {
                                3 -> PresetControls(state.library, state.busy, state.dirty, onPreset,
                                    useSharedLayout = state.useSharedLayout, canCapture = scene.elements.any { it.kind in sharedLayoutKinds },
                                    onEditSharedLayout = { sharedEditor = true }, selectedPresetId = state.selectedPreset?.id)
                                0 -> {
                                    EditorLabel("글꼴")
                                    ClockFont.entries.forEach { font ->
                                        OutlinedCard(onClick = { if (!state.busy) onChange { it.copy(font = font) } }, Modifier.fillMaxWidth().testTag("font_${font.name}"),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (scene.font == font) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(font.label, Modifier.weight(1f), fontFamily = font.family())
                                                Text("12:48", fontFamily = font.family(), style = MaterialTheme.typography.headlineSmall)
                                            }
                                        }
                                    }
                                    EditorLabel("기본 글자색")
                                    ColorControl(scene.textColor, "기본 글자색") { color -> onChange { it.copy(textColor = color) } }
                                    Text("요소별 색상은 ‘요소’에서 따로 지정할 수 있습니다. 야간 모드를 켜면 붉은색으로 표시됩니다.", style = MaterialTheme.typography.bodySmall)
                                }
                                1 -> {
                                    Choices(BackgroundKind.entries, scene.background.kind, { it.label }) { kind -> onChange { it.copy(background = it.background.copy(kind = kind)) } }
                                    when (scene.background.kind) {
                                        BackgroundKind.SOLID -> ColorControl(scene.background.color, "배경색") { color -> onChange { it.copy(background = it.background.copy(color = color)) } }
                                        BackgroundKind.PRESET -> {
                                            BackdropPreset.entries.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                pair.forEach { preset ->
                                                    Column(Modifier.weight(1f).clickable { onChange { it.copy(background = it.background.copy(preset = preset)) } }
                                                        .border(if (scene.background.preset == preset) 2.dp else 1.dp,
                                                            if (scene.background.preset == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                            RoundedCornerShape(8.dp)).padding(8.dp)) {
                                                        SceneBackdrop(BackgroundSpec(kind = BackgroundKind.PRESET, preset = preset), Modifier.height(64.dp))
                                                        Text(preset.label, Modifier.padding(top = 6.dp))
                                                    }
                                                }
                                            } }
                                        }
                                        BackgroundKind.PHOTO -> {
                                            Button(onClick = { photoPicker.launch(arrayOf("image/*")) }, enabled = !state.busy,
                                                modifier = Modifier.testTag("pick_photo")) { Text(if (scene.background.media == null) "사진 선택" else "사진 바꾸기") }
                                            Text("선택한 사진을 앱 안에 보관합니다. 최대 20MB.", style = MaterialTheme.typography.bodySmall)
                                            Choices(PhotoFit.entries, scene.background.fit, { it.label }) { fit -> onChange { it.copy(background = it.background.copy(fit = fit)) } }
                                            ValueSlider("사진 확대", scene.background.zoom, 0.5f..3f, "%.2f배".format(scene.background.zoom)) { value -> onChange { it.copy(background = it.background.copy(zoom = value)) } }
                                            ValueSlider("사진 좌우", scene.background.panX, -1f..1f) { value -> onChange { it.copy(background = it.background.copy(panX = value)) } }
                                            ValueSlider("사진 상하", scene.background.panY, -1f..1f) { value -> onChange { it.copy(background = it.background.copy(panY = value)) } }
                                            ValueSlider("사진 가로 비율", scene.background.widthScale, 0.5f..2f, "%.2f배".format(scene.background.widthScale)) { value -> onChange { it.copy(background = it.background.copy(widthScale = value)) } }
                                            ValueSlider("사진 세로 비율", scene.background.heightScale, 0.5f..2f, "%.2f배".format(scene.background.heightScale)) { value -> onChange { it.copy(background = it.background.copy(heightScale = value)) } }
                                            TextButton(onClick = { onChange { it.copy(background = it.background.copy(zoom = 1f, panX = 0f, panY = 0f, widthScale = 1f, heightScale = 1f)) } }) { Text("사진 배치 초기화") }
                                            EditorLabel("사진 바깥 여백 색")
                                            ColorControl(scene.background.color, "여백 색") { color -> onChange { it.copy(background = it.background.copy(color = color)) } }
                                        }
                                    }
                                    if (scene.background.kind != BackgroundKind.SOLID) ValueSlider("배경 어둡게", scene.background.dim, 0f..0.9f) { value -> onChange { it.copy(background = it.background.copy(dim = value)) } }
                                }
                                2 -> {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        EditorLabel("요소 ${scene.elements.size}개")
                                        Spacer(Modifier.weight(1f))
                                        Button(onClick = { gifPicker.launch(arrayOf("image/gif")) },
                                            enabled = !state.busy && scene.elements.count { it.sharedSlot == null } < StandbyScene.MAX_ELEMENTS && scene.elements.size < StandbyScene.MAX_STORED_ELEMENTS,
                                            modifier = Modifier.testTag("add_GIF")) { Text("+ GIF") }
                                        TextButton(onClick = { resetDialog = true }) { Text("기본 배치") }
                                    }
                                    Text("목록의 뒤에 있는 요소가 앞에 표시됩니다. 버튼도 제거할 수 있으며, 잠금 해제 시 자동 종료는 유지됩니다.", style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ElementKind.entries.filter { it != ElementKind.GIF }.forEach { kind -> OutlinedButton(onClick = {
                                            val item = newElement(UUID.randomUUID().toString(), kind); selected = item.id; onChange { it.copy(elements = it.elements + item) }
                                        }, enabled = !state.busy && scene.elements.count { it.sharedSlot == null } < StandbyScene.MAX_ELEMENTS && scene.elements.size < StandbyScene.MAX_STORED_ELEMENTS,
                                            modifier = Modifier.testTag("add_${kind.name}")) { Text("+ ${kind.label}") } }
                                    }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        scene.elements.forEachIndexed { index, e -> FilterChip(e.id == selected, { selected = e.id },
                                            label = { Text("${index + 1} ${e.kind.label}") }, modifier = Modifier.testTag("select_${e.id}")) }
                                    }
                                    scene.elements.firstOrNull { it.id == selected }?.let { e ->
                                        if (e.id in state.sharedElementIds) {
                                            Text("위치·크기·고정 여부는 공통 배치를 따릅니다. 색상 등은 여기서 바꾸고, 제거하려면 공통 배치에서 제외하거나 연결을 끄세요.", style = MaterialTheme.typography.bodySmall)
                                            OutlinedButton({ sharedEditor = true }, enabled = !state.busy, modifier = Modifier.testTag("edit_shared_from_element")) { Text("공통 배치 편집") }
                                        }
                                        ElementControls(e, scene.textColor, changeElement,
                                            canvasAspect = ratio, font = scene.font, canvasHeightDp = canvasHeightDp,
                                            placementReadOnly = e.id in state.sharedElementIds,
                                            canRemove = e.id !in state.sharedElementIds,
                                            onRemove = { onChange { it.copy(elements = it.elements.filterNot { item -> item.id == e.id }) }; selected = null },
                                            onLayer = { front ->
                                                onChange { current ->
                                                    val list = current.elements.toMutableList()
                                                    val from = list.indexOfFirst { it.id == e.id }
                                                    if (from < 0) current else {
                                                        val to = (from + if (front) 1 else -1).coerceIn(0, list.lastIndex)
                                                        val item = list.removeAt(from); list.add(to, item)
                                                        current.copy(elements = list)
                                                    }
                                                }
                                            })
                                    } ?: Text("미리보기나 요소 목록에서 편집할 대상을 선택하세요.")
                                }
                            }
                        }
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1.2f)) { previewContent() }
                    Box(Modifier.weight(1f)) { controls() }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewContent()
                    Box(Modifier.weight(1f)) { controls() }
                }
            }
        }
    }
    if (sharedEditor && !state.loading) {
        SharedLayoutEditor(state.library.sharedLayout ?: SharedLayout(), state.scene, state.library.presets.count { it.useSharedLayout }, state.busy,
            message = if (pendingSharedSave != null) state.message else "",
            onSave = { layout -> pendingSharedSave = layout; onPreset(PresetAction.SaveSharedLayout(layout)) },
            onDismiss = { sharedEditor = false; sharedFullscreen = false; pendingSharedSave = null },
            fullscreen = sharedFullscreen, onFullscreenChange = { sharedFullscreen = it })
    }
    if (layersDialog && !preview) AlertDialog(onDismissRequest = { layersDialog = false }, title = { Text("요소 선택") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            SceneLayerList(scene, selected, state.sharedElementIds, !state.busy,
                onSelect = { selected = it; tab = 2; layersDialog = false })
        } }, confirmButton = { TextButton({ layersDialog = false }) { Text("닫기") } })
    if (exitDialog) AlertDialog(onDismissRequest = { exitDialog = false }, title = { Text("변경 사항을 저장할까요?") },
        text = { Text("저장하면 스탠바이 시계에 적용됩니다. ‘버리기’를 누르면 마지막 저장 상태로 돌아갑니다.") },
        confirmButton = { TextButton(onClick = { requestSave(true) }, enabled = !state.busy) { Text("저장 후 나가기") } },
        dismissButton = { Row { TextButton(onClick = { exitDialog = false }) { Text("계속 편집") }; TextButton(onClick = onDiscard, enabled = !state.busy) { Text("버리기") } } })
    presetSaveExit?.let { leave ->
        AlertDialog(onDismissRequest = { presetSaveExit = null }, modifier = Modifier.testTag("save_preset_dialog"),
            title = { Text("프리셋에도 반영할까요?") },
            text = { Text("현재 화면을 저장하면서 선택한 ‘${state.selectedPreset?.name.orEmpty()}’ 프리셋도 수정한 내용으로 바꿀 수 있습니다.") },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ presetSaveExit = null; onSavePreset(leave) }, enabled = !state.busy && state.selectedPreset != null,
                        modifier = Modifier.fillMaxWidth().testTag("save_with_preset")) { Text("프리셋에도 반영") }
                    OutlinedButton({ presetSaveExit = null; if (leave) onSaveExit() else onSave() }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("save_current_only")) { Text("현재 화면만 저장") }
                    TextButton({ presetSaveExit = null }, Modifier.fillMaxWidth().testTag("cancel_preset_save")) { Text("취소") }
                }
            })
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("요소를 기본 배치로 되돌릴까요?") },
        text = { Text("추가한 GIF와 요소 배치는 편집 화면에서 제거됩니다. 저장 전까지 실제 시계는 바뀌지 않습니다.") },
        confirmButton = { TextButton(onClick = { onChange { it.copy(elements = defaultElements()) }; selected = "clock"; resetDialog = false }) { Text("기본 배치 적용") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("취소") } })
}

@Composable
private fun ElementControls(e: SceneElement, defaultColor: Long, onChange: (SceneElement) -> Unit, onRemove: () -> Unit, onLayer: (Boolean) -> Unit,
    placementReadOnly: Boolean = false, canRemove: Boolean = true, canvasAspect: Float = 2.45f, font: ClockFont = ClockFont.SYSTEM, canvasHeightDp: Float = 300f,
) {
    EditorLabel(e.kind.label)
    PlacementControls(e, onChange, readOnly = placementReadOnly, font = font, canvasAspect = canvasAspect, canvasHeightDp = canvasHeightDp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onLayer(false) }) { Text("뒤로") }
        OutlinedButton(onClick = { onLayer(true) }) { Text("앞으로") }
        TextButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.testTag("remove_element")) { Text("제거", color = MaterialTheme.colorScheme.error) }
    }
    ElementStyleControls(e, defaultColor, onChange, canvasAspect)
}

@Composable
internal fun PlacementControls(source: SceneElement, onChange: (SceneElement) -> Unit, readOnly: Boolean = false, tagPrefix: String = "",
    font: ClockFont = ClockFont.SYSTEM, canvasAspect: Float = 2.45f, canvasHeightDp: Float = 300f,
    now: LocalDateTime = LocalDateTime.now(), power: PowerState = PowerState(percent = 82, plugged = 1, charging = true),
) {
    val sizer = rememberContentSizer(font, canvasAspect, now, power, canvasHeightDp)
    val e = sizer.resolve(source).element
    val enabled = !e.positionLocked && !readOnly
    if (e.hasTextStyle) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("내용에 맞춘 영역", Modifier.weight(1f))
            Switch(e.contentSize != null, { tight -> onChange(if (tight) sizer.fit(e) else e.copy(contentSize = null)) },
                enabled = enabled, modifier = Modifier.testTag("${tagPrefix}fit_content"))
        }
        if (e.contentSize != null) {
            ValueSlider("내용 크기", e.contentSize, 0.02f..1.5f, enabled = enabled, tagPrefix = tagPrefix) {
                onChange(e.copy(contentSize = it))
            }
            Text("글자와 내부 여백을 합친 크기에 선택 영역을 맞춥니다. 모서리를 끌면 비율을 유지하며 크기가 바뀝니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("위치·크기 고정", Modifier.weight(1f))
        Switch(e.positionLocked, { onChange(e.copy(positionLocked = it)) }, enabled = !readOnly, modifier = Modifier.testTag("${tagPrefix}position_locked"))
    }
    ValueSlider("가로 위치", e.x, 0f..(1f - e.width).coerceAtLeast(0.001f), enabled = enabled, tagPrefix = tagPrefix) {
        onChange(e.copy(x = it, horizontalAnchor = CanvasAnchor.FREE))
    }
    ValueSlider("세로 위치", e.y, 0f..(1f - e.height).coerceAtLeast(0.001f), enabled = enabled, tagPrefix = tagPrefix) {
        onChange(e.copy(y = it, verticalAnchor = CanvasAnchor.FREE))
    }
    if (e.contentSize == null) {
        ValueSlider("너비", e.width, 0.06f..1f, enabled = enabled, tagPrefix = tagPrefix) { onChange(e.copy(width = it)) }
        ValueSlider("높이", e.height, 0.06f..1f, enabled = enabled, tagPrefix = tagPrefix) { onChange(e.copy(height = it)) }
    }
    Text("화면 내 배치", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("왼쪽 끝" to CanvasAnchor.START, "가운데" to CanvasAnchor.CENTER, "오른쪽 끝" to CanvasAnchor.END).forEachIndexed { index, (label, anchor) ->
            OutlinedButton({ onChange(sizer.fit(e).copy(horizontalAnchor = anchor).anchored()) }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).testTag("${tagPrefix}place_horizontal_$index")) { Text(label, maxLines = 1) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("위쪽 끝" to CanvasAnchor.START, "중간" to CanvasAnchor.CENTER, "아래쪽 끝" to CanvasAnchor.END).forEachIndexed { index, (label, anchor) ->
            OutlinedButton({ onChange(sizer.fit(e).copy(verticalAnchor = anchor).anchored()) }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).testTag("${tagPrefix}place_vertical_$index")) { Text(label, maxLines = 1) }
        }
    }
    InternalPaddingControl("좌우 내부 여백 (%)", "${tagPrefix}horizontal", e.horizontalPadding, enabled) { onChange(sizer.resolve(e.copy(horizontalPadding = it)).element) }
    InternalPaddingControl("위아래 내부 여백 (%)", "${tagPrefix}vertical", e.verticalPadding, enabled) { onChange(sizer.resolve(e.copy(verticalPadding = it)).element) }
    Text("테두리와 내용 사이에 적용됩니다. 좌우는 영역 너비, 위아래는 영역 높이의 지정 비율만큼 양쪽에 각각 띄웁니다. 내용 맞춤에서는 글자 크기를 유지하며 영역이 커집니다.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InternalPaddingControl(label: String, tag: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    var input by remember(value) { mutableStateOf("%.2f".format(java.util.Locale.US, value * 100f).trimEnd('0').trimEnd('.')) }
    val number = input.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..40f }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val apply = { number?.let { onChange(it / 100f); keyboard?.hide(); focus.clearFocus() }; Unit }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it.take(6) }, Modifier.weight(1f).testTag("padding_$tag"), enabled = enabled,
            label = { Text(label) }, singleLine = true, isError = number == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }))
        OutlinedButton(apply, enabled = enabled && number != null, modifier = Modifier.testTag("apply_padding_$tag")) { Text("여백 적용") }
    }
}

@Composable
internal fun ElementTextControls(e: SceneElement, onChange: (SceneElement) -> Unit, canvasAspect: Float, enabled: Boolean = true) {
    EditorLabel("글자 정렬")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ElementTextAlignment.entries.forEach { align ->
            FilterChip(e.textAlignment == align, { onChange(e.copy(textAlignment = align)) }, enabled = enabled,
                label = { Text(align.label) }, modifier = Modifier.testTag("text_align_${align.name}"))
        }
    }
    if (e.contentSize != null) {
        Text("크기는 ‘내용 크기’ 또는 모서리 손잡이로 조절하세요.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("크기 자동 맞춤", Modifier.weight(1f))
        Switch(e.textSize == null, { automatic -> onChange(e.copy(textSize = if (automatic) null else e.automaticTextSize(canvasAspect))) },
            enabled = enabled, modifier = Modifier.testTag("automatic_text_size"))
    }
    e.textSize?.let { size ->
        ValueSlider("글자 크기", size, 0.02f..0.65f, enabled = enabled) { onChange(e.copy(textSize = it)) }
        Text("너비를 바꿔도 지정한 글자 크기를 유지합니다. 영역이 좁으면 글자가 잘리지 않도록 줄입니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ElementStyleControls(e: SceneElement, defaultColor: Long, onChange: (SceneElement) -> Unit, canvasAspect: Float) {
    if (e.hasTextStyle) ElementTextControls(e, onChange, canvasAspect)
    if (e.kind == ElementKind.WORK_SHIFT) {
        EditorLabel("게이지 방향")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeOrientation.entries.forEach { direction -> FilterChip(e.gaugeOrientation == direction,
                { onChange(e.withGaugeOrientation(direction)) }, label = { Text(direction.label) }, modifier = Modifier.testTag("gauge_direction_${direction.name}")) }
        }
        Text("출근 시각은 실제 스탠바이의 출근 버튼에서 선택합니다. 잘못 입력했다면 표시된 게이지를 길게 눌러 변경하세요. 100%가 되면 폭죽 후 자동 퇴근하며, 100% 도달 시각부터 12시간 동안 출퇴근 게이지만 숨깁니다. 시계와 배경은 계속 표시합니다.", style = MaterialTheme.typography.bodySmall)
    }
    if (e.kind == ElementKind.GIF) {
        EditorLabel("GIF 재생")
        ValueSlider("재생 속도", e.speed, 0.25f..3f, "%.2f배".format(e.speed)) { onChange(e.copy(speed = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("반복 재생"); Text(if (e.loop) "계속 반복" else "한 번 재생 후 마지막 프레임 유지", style = MaterialTheme.typography.bodySmall) }
            Switch(e.loop, { onChange(e.copy(loop = it)) }, modifier = Modifier.testTag("gif_loop"))
        }
        Text("속도·반복 설정을 바꾸거나 시계를 다시 열면 처음부터 재생합니다.", style = MaterialTheme.typography.bodySmall)
        Choices(PhotoFit.entries, e.fit, { it.label }) { onChange(e.copy(fit = it)) }
    } else if (e.kind == ElementKind.WORK_SHIFT) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("기본 청록색 사용", Modifier.weight(1f))
            Switch(e.color == null, { onChange(e.copy(color = if (it) null else DEFAULT_WORK_SHIFT_COLOR)) },
                modifier = Modifier.testTag("work_shift_default_color"))
        }
        ColorControl(e.color ?: DEFAULT_WORK_SHIFT_COLOR, "게이지 색상") { onChange(e.copy(color = it)) }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("기본 글자색 사용", Modifier.weight(1f))
            Switch(e.color == null, { onChange(e.copy(color = if (it) null else defaultColor)) })
        }
        if (e.color != null) ColorControl(e.color, "요소 글자색") { onChange(e.copy(color = it)) }
    }
    ValueSlider("불투명도", e.opacity, 0.1f..1f) { onChange(e.copy(opacity = it)) }
}

@Composable
private fun EditorLabel(label: String) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

@Composable
private fun <T> Choices(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(value == selected, { onSelect(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String = "${(value * 100).roundToInt()}%", enabled: Boolean = true,
    tagPrefix: String = "", onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(display, color = MaterialTheme.colorScheme.primary) }
        Slider(value.coerceIn(range), onChange, valueRange = range, enabled = enabled, modifier = Modifier.testTag("${tagPrefix}slider_$label").semantics { contentDescription = label })
    }
}

private val swatches = listOf(0xFFFFFFFF, 0xFFA6E8CB, 0xFF97C5FF, 0xFFFFB6CA, 0xFFFFC978, 0xFFD0B0FF, 0xFFEB6255, 0xFF050708)

@Composable
private fun ColorControl(color: Long, label: String, onChange: (Long) -> Unit) {
    var hex by remember(color) { mutableStateOf("%06X".format(color and 0xFFFFFF)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val apply = { parseHexColor(hex)?.let { onChange(it); keyboard?.hide(); focus.clearFocus() }; Unit }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { swatch -> Box(Modifier.size(38.dp).border(if (swatch == color) 3.dp else 1.dp,
            if (swatch == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(5.dp).background(Color(swatch), CircleShape).clickable { onChange(swatch) }
            .semantics { contentDescription = "$label #%06X".format(swatch and 0xFFFFFF) }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(hex, { hex = it.take(7) }, Modifier.weight(1f).testTag("hex_$label"), label = { Text("$label · HEX") },
            prefix = { Text("#") }, singleLine = true, isError = parseHexColor(hex) == null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }))
        OutlinedButton(onClick = apply, enabled = parseHexColor(hex) != null) { Text("적용") }
    }
}
