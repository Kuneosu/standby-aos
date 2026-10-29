package dev.kwonsu.standby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

internal val NotificationPanelProgress = SemanticsPropertyKey<Float>("NotificationPanelProgress")

@Composable
internal fun StandbyStage(scene: StandbyScene, power: PowerState, now: LocalDateTime, night: Boolean,
    brightness: Float, onBrightness: (Float) -> Unit, onBrightnessFinished: () -> Unit,
    canCycle: Boolean, onNextPreset: () -> Unit, onNight: () -> Unit, onDiagnostics: () -> Unit, onExit: () -> Unit,
    notifications: NotificationFeedState, modifier: Modifier = Modifier, onNotificationDismiss: (String) -> Unit = NotificationRuntime::dismiss,
    onNotificationsDismiss: (Set<String>) -> Unit = NotificationRuntime::dismissMany,
    workShift: WorkShiftDisplay? = null, workShiftBusy: Boolean = false, onClockIn: () -> Unit = {}, onEditClockIn: () -> Unit = {},
) {
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress by animateFloatAsState(dragFraction ?: if (panelOpen) 1f else 0f,
        animationSpec = if (dragFraction != null) snap() else tween(360, easing = NotificationMotion.Settle), label = "알림 패널 이동")
    val next by rememberUpdatedState(onNextPreset)
    val currentScene by rememberUpdatedState(scene)
    val cycling by rememberUpdatedState(canCycle)
    val currentWorkShift by rememberUpdatedState(workShift)
    val backdrop = rememberGraphicsLayer()
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val directionThreshold = with(LocalDensity.current) { 6.dp.toPx() }
    val close = { dragFraction = null; panelOpen = false }
    BackHandler(panelOpen || progress > 0f, onBack = close)
    BoxWithConstraints(modifier.clipToBounds().testTag("standby_stage").semantics { this[NotificationPanelProgress] = progress }) {
        val margin = 12.dp
        val panelWidth = (maxWidth * 0.5f - margin).coerceAtLeast(1.dp)
        val panelHeight = (maxHeight - margin * 2).coerceAtLeast(1.dp)
        val fullWidth = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        val panelPixels = with(LocalDensity.current) { panelWidth.roundToPx().toFloat() }
        val panelHeightPixels = with(LocalDensity.current) { panelHeight.roundToPx().toFloat() }
        val marginPixels = with(LocalDensity.current) { margin.roundToPx().toFloat() }
        val panelVisible = progress > 0.001f
        Box(Modifier.fillMaxSize().pointerInput(fullWidth, fullHeight) {
            var dx = 0f; var dy = 0f
            var action = StandbyDragAction.WAITING
            var blocked = false
            detectDragGestures(onDragStart = { point ->
                dx = 0f; dy = 0f; action = StandbyDragAction.WAITING
                blocked = panelOpen || currentScene.elements.any { (it.kind == ElementKind.BRIGHTNESS ||
                    (it.kind == ElementKind.WORK_SHIFT && currentWorkShift != null && currentWorkShift?.phase != WorkShiftPhase.COOLDOWN)) &&
                    point.x >= it.x * fullWidth && point.x <= (it.x + it.width) * fullWidth &&
                    point.y >= it.y * fullHeight && point.y <= (it.y + it.height) * fullHeight }
            }, onDragEnd = {
                if (!blocked) when (action) {
                    StandbyDragAction.NEXT_PRESET -> if (abs(dy) >= threshold) next()
                    StandbyDragAction.OPEN_NOTIFICATIONS -> panelOpen = -dx >= threshold || (dragFraction ?: 0f) >= 0.2f
                    else -> Unit
                }
                dragFraction = null
            }, onDragCancel = { dragFraction = null }) { change, delta ->
                if (!blocked) {
                    dx += delta.x; dy += delta.y
                    if (action == StandbyDragAction.WAITING) action = classifyStandbyDrag(dx, dy, directionThreshold, cycling)
                    if (action == StandbyDragAction.OPEN_NOTIFICATIONS) dragFraction = (-dx / (panelPixels + marginPixels)).coerceIn(0f, 1f)
                    change.consume()
                }
            }
        }.then(if (panelVisible) Modifier.clearAndSetSemantics {} else Modifier.semantics {
            customActions = buildList {
                add(CustomAccessibilityAction("알림 열기") { panelOpen = true; true })
                if (canCycle) add(CustomAccessibilityAction("다음 프리셋") { next(); true })
            }
        })) {
            SceneCanvas(scene, power, now, Modifier.fillMaxSize().recordGlassBackdrop(if (panelVisible) backdrop else null),
                night = night, brightness = brightness, onBrightness = onBrightness, onBrightnessFinished = onBrightnessFinished,
                onNight = onNight, onDiagnostics = onDiagnostics, onExit = onExit,
                workShift = workShift, workShiftBusy = workShiftBusy, onClockIn = onClockIn, onEditClockIn = onEditClockIn)
        }
        if (panelVisible) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f * progress))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
                .testTag("notification_backdrop").semantics { contentDescription = "알림 패널 닫기" })
            val slideX = (-marginPixels + (panelPixels + marginPixels) * (1f - progress)).roundToInt()
            val origin = Offset(fullWidth - panelPixels + slideX, ((fullHeight - panelHeightPixels) / 2f).roundToInt().toFloat())
            NotificationPanel(notifications, backdrop, origin, night, close,
                Modifier.align(Alignment.CenterEnd).offset { IntOffset(slideX, 0) }.size(panelWidth, panelHeight)
                    .pointerInput(panelPixels) {
                        var distance = 0f
                        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                            panelOpen = distance < threshold && (dragFraction ?: 1f) > 0.7f
                            dragFraction = null
                        }, onDragCancel = { dragFraction = null }) { change, amount ->
                            distance += amount
                            if (distance > 0f) { change.consume(); dragFraction = (1f - distance / (panelPixels + marginPixels)).coerceIn(0f, 1f) }
                        }
                    }, onDismiss = onNotificationDismiss, onDismissMany = onNotificationsDismiss)
        }
    }
}
