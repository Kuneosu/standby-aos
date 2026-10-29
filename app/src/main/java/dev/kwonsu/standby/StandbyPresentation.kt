package dev.kwonsu.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 기기 창과 별개로 검증할 수 있는 실제 프리셋 선택·밝기·스와이프 표시 흐름. */
@Composable
fun StandbyPresentation(
    settings: SettingsStore, entryId: String, fold: FoldInfo, power: PowerState, night: Boolean,
    source: String, locked: Boolean, events: List<DiagnosticEvent>, systemBrightness: Float,
    onNight: () -> Unit, onExit: () -> Unit, applyBrightness: (Float) -> Unit,
) {
    var selection by remember(entryId) { mutableStateOf<PlaybackSelection?>(null) }
    var switching by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var liveBrightness by remember(night) { mutableStateOf<Float?>(null) }
    val storedBrightness by settings.brightness.collectAsState(initial = BrightnessSettings())
    val scope = rememberCoroutineScope()
    LaunchedEffect(entryId) {
        selection = settings.selectForEntry(entryId)
        Diagnostics.record("preset_entry", "진입=$entryId; 프리셋=${selection?.presetId ?: "current"}")
    }
    LaunchedEffect(notice) { if (notice.isNotEmpty()) { delay(1600); notice = "" } }
    val configured = liveBrightness ?: if (night) storedBrightness.night else storedBrightness.day
    SideEffect { applyBrightness(configured ?: -1f) }
    Box(Modifier.fillMaxSize()) {
        selection?.let { chosen -> key(entryId, chosen.presetId) {
            StandbyScreen(fold, power, night, source, locked, events, onNight, onExit, chosen.scene,
                brightness = configured ?: systemBrightness,
                onBrightness = { liveBrightness = clampBrightness(it) },
                onBrightnessFinished = {
                    liveBrightness?.let { value -> scope.launch {
                        settings.setBrightness(value, night)
                        if (liveBrightness == value) liveBrightness = null
                    } }
                }, canCycle = chosen.cycleSize > 1,
                onNextPreset = {
                    if (!switching) {
                        switching = true
                        scope.launch {
                            try {
                                selection = settings.selectForEntry(entryId, advance = true)
                                notice = selection?.name.orEmpty()
                                Diagnostics.record("preset_swipe", "프리셋=${selection?.presetId ?: "current"}")
                            } finally { switching = false }
                        }
                    }
                })
        } }
        if (notice.isNotEmpty()) Text(notice, Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("preset_notice"), color = Color.White)
    }
}
