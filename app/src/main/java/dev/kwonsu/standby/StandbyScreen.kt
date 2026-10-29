package dev.kwonsu.standby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun FoldTheme(night: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = if (night) Color(0xFFFF5C47) else Color(0xFFA6E8CB),
        onPrimary = Color(0xFF10251B), background = Color(0xFF050708), surface = Color(0xFF101619),
        onSurface = if (night) Color(0xFFCF5548) else Color(0xFFE4EAE7),
        onSurfaceVariant = if (night) Color(0xFFAF5145) else Color(0xFF9DAAA4),
        secondaryContainer = Color(0xFF213B33), onSecondaryContainer = Color(0xFFB8ECD7),
    ), content = content)
}

@Composable
fun StandbyScreen(
    fold: FoldInfo, power: PowerState, night: Boolean, source: String, locked: Boolean,
    events: List<DiagnosticEvent>, onNightChange: () -> Unit, onExit: () -> Unit,
    scene: StandbyScene = StandbyScene(),
    brightness: Float = 0.5f, onBrightness: (Float) -> Unit = {}, onBrightnessFinished: () -> Unit = {},
    canCycle: Boolean = false, onNextPreset: () -> Unit = {},
    notificationFeed: NotificationFeedState? = null,
    onNotificationDismiss: (String) -> Unit = NotificationRuntime::dismiss,
    onNotificationsDismiss: (Set<String>) -> Unit = NotificationRuntime::dismissMany,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var diagnostic by rememberSaveable { mutableStateOf(false) }
    val runtimeNotifications by NotificationRuntime.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (notificationFeed == null) NotificationRuntime.refreshAccess(context) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(60_000 - System.currentTimeMillis() % 60_000) }
    }
    Surface(color = Color.Black) {
        val stage: @Composable (WorkShiftDisplay?, Boolean, () -> Unit, () -> Unit) -> Unit = { work, busy, clockIn, editClockIn ->
        StandbyStage(scene, power, now, night, brightness, onBrightness, onBrightnessFinished, canCycle, onNextPreset,
            onNight = onNightChange, onDiagnostics = { diagnostic = true }, onExit = onExit,
            notifications = notificationFeed ?: runtimeNotifications, modifier = Modifier.fillMaxSize().onSizeChanged {
            Diagnostics.record("canvas_size", "${it.width}x${it.height}px · 가로 시계 캔버스 · ${fold.description}")
        }, onNotificationDismiss = onNotificationDismiss, onNotificationsDismiss = onNotificationsDismiss,
            workShift = work, workShiftBusy = busy, onClockIn = clockIn, onEditClockIn = editClockIn)
        }
        if (scene.elements.any { it.kind == ElementKind.WORK_SHIFT }) WorkShiftHost(night, content = stage)
        else stage(null, false, {}, {})
    }
    if (diagnostic) AlertDialog(onDismissRequest = { diagnostic = false }, title = { Text("최근 진단") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${fold.description}\n경로: $source · 잠금: $locked\n배터리 온도: ${power.temperature}°C")
            events.take(6).forEach { Text("${it.event}\n${it.details}", style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(onClick = { diagnostic = false }) { Text("진단 닫기") } })
}
