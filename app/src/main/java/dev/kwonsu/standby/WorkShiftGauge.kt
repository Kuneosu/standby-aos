package dev.kwonsu.standby

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ShiftEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)
internal const val DEFAULT_WORK_SHIFT_COLOR = 0xFF65CBDFL
private data class ShiftColors(val accent: Color, val lit: Color, val dim: Color, val surface: Color)
private fun shiftColors(night: Boolean, custom: Color?): ShiftColors {
    if (night) return ShiftColors(Color(0xFFD39889), Color(0xFFE0A293), Color(0xFF452D2B), Color(0xFF1C1110))
    if (custom == null || custom == Color(DEFAULT_WORK_SHIFT_COLOR)) return ShiftColors(Color(DEFAULT_WORK_SHIFT_COLOR), Color(0xFF92E8FA), Color(0xFF1B4651), Color(0xFF06171C))
    val accent = custom.copy(alpha = 1f)
    return ShiftColors(accent, lerp(accent, Color.White, 0.38f), lerp(Color.Black, accent, 0.3f), lerp(Color.Black, accent, 0.09f))
}

@Composable
internal fun WorkShiftGauge(display: WorkShiftDisplay?, busy: Boolean, editing: Boolean, night: Boolean, customColor: Color?,
    orientation: GaugeOrientation, onClockIn: () -> Unit, onEditClockIn: () -> Unit = {},
) {
    if (display == null || display.phase == WorkShiftPhase.COOLDOWN) return
    val palette = shiftColors(night, customColor)
    val vertical = orientation == GaugeOrientation.VERTICAL
    val edit by rememberUpdatedState(onEditClockIn)
    val haptic = LocalHapticFeedback.current
    val canEdit = !busy && !editing && display.phase in setOf(WorkShiftPhase.WORKING, WorkShiftPhase.COMPLETE)
    val editGesture = if (canEdit) Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() })
    }.semantics { onLongClick("출근 시각 변경") { edit(); true } } else Modifier
    BoxWithConstraints(Modifier.fillMaxSize().then(editGesture).testTag("work_shift_component"), contentAlignment = Alignment.Center) {
        val gaugeWidth = maxWidth
        val gaugeHeight = maxHeight
        val fontScale = LocalDensity.current.fontScale
        val font = (if (vertical) minOf(gaugeWidth.value * 0.26f, gaugeHeight.value * 0.075f, 16f)
            else minOf(gaugeHeight.value * 0.36f, gaugeWidth.value / 11, 16f)).coerceAtLeast(1f) / fontScale
        val gap = minOf(6.dp, gaugeHeight * 0.1f)
        // 장식을 포함한 두께를 제한해 저장된 요소가 커도 얇은 비율을 유지한다.
        val thickness = minOf(24.dp, (if (vertical) gaugeWidth else gaugeHeight) * 0.48f)
        val badgeHeight = thickness * 0.8f
        val badgeWidth = minOf(gaugeWidth, thickness * 1.8f)
        val percentFont = minOf(font * 0.85f, thickness.value * 0.46f / fontScale)
        val actionHeight = minOf(44.dp, gaugeHeight)
        if (display.phase == WorkShiftPhase.READY) {
            ShiftActionButton("출근", palette, !busy && !editing, onClockIn,
                Modifier.width(minOf(100.dp, gaugeWidth)).height(actionHeight).testTag("clock_in"), font)
        } else {
            val deadline = if (display.deadline > 0) Instant.ofEpochMilli(display.deadline).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm")) else "미리보기"
            if (vertical) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                ShiftPercentage(display.percent, palette, percentFont, Modifier.size(badgeWidth, badgeHeight))
                ShiftDeadline(deadline, display.deadline > 0, palette, font * 0.65f, Modifier.align(Alignment.End))
                WorkShiftMeter(display, true, palette, Modifier.width(thickness).weight(1f))
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.End) {
                    ShiftDeadline(deadline, display.deadline > 0, palette, font * 0.65f)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ShiftPercentage(display.percent, palette, percentFont, Modifier.size(badgeWidth, badgeHeight))
                        WorkShiftMeter(display, false, palette, Modifier.weight(1f).height(thickness))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftDeadline(deadline: String, hasDeadline: Boolean, palette: ShiftColors, font: Float, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(3.dp)
    Row(modifier.testTag("work_shift_deadline").background(palette.surface.copy(alpha = 0.8f), shape)
        .border(0.5.dp, palette.accent.copy(alpha = 0.32f), shape).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(deadline, color = palette.accent, fontFamily = FontFamily.Monospace, fontSize = font.sp,
            lineHeight = (font * 1.2f).sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
        if (hasDeadline) Text("퇴근", color = palette.accent.copy(alpha = 0.7f), fontSize = font.sp,
            lineHeight = (font * 1.2f).sp, maxLines = 1)
    }
}

@Composable
private fun ShiftPercentage(percent: Int, palette: ShiftColors, font: Float, modifier: Modifier) {
    val accent = palette.accent
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val edge = minOf(0.75.dp.toPx(), size.minDimension / 12)
            val frame = techFrame(edge, edge, size.width - edge, size.height - edge, size.minDimension * 0.14f)
            drawPath(frame, palette.surface.copy(alpha = 0.9f))
            drawPath(frame, accent.copy(alpha = 0.12f), style = Stroke(edge * 3))
            drawPath(frame, accent.copy(alpha = 0.7f), style = Stroke(edge))
        }
        Text("$percent%", color = accent, fontSize = font.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun WorkShiftMeter(display: WorkShiftDisplay, vertical: Boolean, palette: ShiftColors, modifier: Modifier) {
    val progress by animateFloatAsState(display.progress, tween(600, easing = ShiftEase), label = "출퇴근 게이지")
    Canvas(modifier.testTag("work_shift_gauge").semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(display.progress, 0f..1f)
        stateDescription = "${if (vertical) "세로" else "가로"} 게이지 ${display.percent}%"
    }) {
        if (vertical) {
            val length = size.height
            val thickness = size.width
            withTransform({ translate(0f, length); rotate(-90f, pivot = Offset.Zero) }) {
                drawTechRail(length, thickness, progress, palette)
            }
        } else drawTechRail(size.width, size.height, progress, palette)
    }
}

private fun DrawScope.drawTechRail(length: Float, thickness: Float, progress: Float, palette: ShiftColors) {
    if (length <= 0f || thickness <= 0f) return
    val unit = thickness / 24f
    val accent = palette.accent
    val frame = techFrame(unit, unit * 3, length - unit, thickness - unit * 3, unit * 2)
    drawPath(frame, palette.surface.copy(alpha = 0.94f))
    drawPath(frame, accent.copy(alpha = 0.1f), style = Stroke(unit * 3))
    drawPath(frame, accent.copy(alpha = 0.78f), style = Stroke(unit * 0.8f))
    val available = (length - unit * 10).coerceAtLeast(0f)
    val count = (available / (unit * 7.2f)).toInt().coerceIn(12, 40)
    val pitch = available / count
    val cellWidth = pitch * 0.65f
    val cellHeight = unit * 12
    val dim = palette.dim
    val lit = palette.lit
    repeat(count) { index ->
        val start = Offset(unit * 5 + pitch * index + (pitch - cellWidth) / 2, unit * 6)
        val radius = CornerRadius(unit * 0.25f)
        drawRoundRect(dim, start, Size(cellWidth, cellHeight), radius)
        val fraction = (progress * count - index).coerceIn(0f, 1f)
        if (fraction > 0) drawRoundRect(lit, start, Size(cellWidth * fraction, cellHeight), radius)
    }
    val top = Path().apply {
        moveTo(length * 0.18f, unit * 2.5f); lineTo(length * 0.19f, unit * 0.4f)
        lineTo(length * 0.8f, unit * 0.4f); lineTo(length * 0.81f, unit * 2.5f); close()
    }
    drawPath(top, accent.copy(alpha = 0.88f))
    listOf(0.08f to 0.24f, 0.76f to 0.91f).forEach { (from, to) ->
        val lower = Path().apply {
            moveTo(length * from, thickness - unit * 2.4f); lineTo(length * (from + 0.01f), thickness - unit * 0.4f)
            lineTo(length * (to - 0.01f), thickness - unit * 0.4f); lineTo(length * to, thickness - unit * 2.4f); close()
        }
        drawPath(lower, accent.copy(alpha = 0.65f))
    }
}

private fun techFrame(left: Float, top: Float, right: Float, bottom: Float, cut: Float) = Path().apply {
    moveTo(left + cut, top); lineTo(right - cut, top); lineTo(right, top + cut); lineTo(right, bottom - cut)
    lineTo(right - cut, bottom); lineTo(left + cut, bottom); lineTo(left, bottom - cut); lineTo(left, top + cut); close()
}

@Composable
private fun ShiftActionButton(label: String, palette: ShiftColors, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, font: Float) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(180, easing = ShiftEase), label = "출퇴근 버튼 누름")
    // 배경과 게이지 색상이 같아도 버튼 바탕과 글자는 서로 구분되어야 한다.
    val foreground = if (palette.accent.luminance() >= 0.2f) palette.accent else lerp(palette.accent, Color.White, 0.65f)
    BoxWithConstraints(modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        val shape = CutCornerShape(4.dp)
        Box(Modifier.fillMaxWidth().height(minOf(28.dp, maxHeight)).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(palette.surface, shape).border(1.dp, foreground.copy(alpha = if (enabled) 0.8f else 0.4f), shape),
            contentAlignment = Alignment.Center) {
            Text(label, color = foreground, fontSize = font.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
