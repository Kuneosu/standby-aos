package dev.kwonsu.standby

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("DiscouragedApi")
@Composable
fun ClockFont.family(): FontFamily {
    val resources = LocalResources.current
    val packageName = LocalContext.current.packageName
    return remember(this, resources, packageName) {
        val resourceName = when (this) {
            ClockFont.SYSTEM -> null
            ClockFont.ESAMANRU -> "esamanru"
            ClockFont.MAPLESTORY -> "maplestory"
            ClockFont.KKUKKUKK -> "kkukkukk"
        }
        // 재배포하지 않는 선택 글꼴이 없는 빌드에서도 저장된 글꼴 선택을 유지한다.
        val resourceId = resourceName?.let { resources.getIdentifier(it, "font", packageName) } ?: 0
        if (resourceId == 0) FontFamily.SansSerif else FontFamily(Font(resourceId))
    }
}

@Composable
fun SceneCanvas(
    scene: StandbyScene, power: PowerState, now: LocalDateTime, modifier: Modifier = Modifier,
    night: Boolean = false, editing: Boolean = false, selectedId: String? = null,
    onSelect: (String) -> Unit = {}, onChange: (SceneElement) -> Unit = {},
    onNight: () -> Unit = {}, onDiagnostics: () -> Unit = {}, onExit: () -> Unit = {},
    brightness: Float = 0.5f, onBrightness: (Float) -> Unit = {}, onBrightnessFinished: () -> Unit = {},
    alignmentAssist: Boolean = true,
    geometryReadOnlyIds: Set<String> = emptySet(), editableIds: Set<String>? = null,
    workShift: WorkShiftDisplay? = WorkShiftDisplay.Preview, workShiftBusy: Boolean = false,
    onClockIn: () -> Unit = {}, onEditClockIn: () -> Unit = {},
) {
    var pixels by remember { mutableStateOf(IntSize(1, 1)) }
    val currentChange by rememberUpdatedState(onChange)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentScene by rememberUpdatedState(scene)
    val assist by rememberUpdatedState(alignmentAssist)
    val snapPixels = with(LocalDensity.current) { 8.dp.toPx() }
    var guides by remember { mutableStateOf<AlignmentResult?>(null) }
    val lastGlass = scene.elements.indexOfLast { it.kind == ElementKind.BRIGHTNESS }
    val backgroundLayer = if (lastGlass >= 0) rememberGraphicsLayer() else null
    val layers = scene.elements.take(lastGlass.coerceAtLeast(0)).associate { element ->
        element.id to key(element.id) { rememberGraphicsLayer() }
    }
    BoxWithConstraints(modifier.clipToBounds().onSizeChanged { pixels = it }.testTag("scene_canvas")) {
        SceneBackdrop(scene.background, Modifier.recordGlassBackdrop(backgroundLayer))
        val w = maxWidth
        val h = maxHeight
        val family = scene.font.family()
        val sizer = rememberContentSizer(scene.font, w.value / h.value.coerceAtLeast(1f), now, power, h.value)
        val placements = remember(scene.elements, sizer) { scene.elements.map { sizer.resolve(it) } }
        val resolved = placements.map { it.element }
        placements.forEachIndexed { index, placement -> key(placement.element.id) {
            val element = placement.element
            Box(Modifier.offset { IntOffset((element.x * pixels.width).roundToInt(), (element.y * pixels.height).roundToInt()) }
                .size(w * element.width, h * element.height)
                .testTag("element_${element.id}"), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().recordGlassBackdrop(layers[element.id]).graphicsLayer { alpha = element.opacity }
                    .padding(horizontal = w * element.width * element.horizontalPadding, vertical = h * element.height * element.verticalPadding)
                    .clipToBounds()) {
                    val glassBackdrop = if (element.kind == ElementKind.BRIGHTNESS) buildList {
                        backgroundLayer?.let { add(GlassBackdrop(it, Offset.Zero)) }
                        resolved.take(index).forEach { behind ->
                            layers[behind.id]?.let { layer -> add(GlassBackdrop(layer,
                                Offset((behind.x * pixels.width).roundToInt().toFloat(), (behind.y * pixels.height).roundToInt().toFloat()))) }
                        }
                    } else emptyList()
                    val textColor = Color(if (night) 0xFFCF5548 else element.color ?: scene.textColor)
                    if (placement.ink != null) TightTextContent(element.id, placement.ink, sizer.typeface, textColor)
                    else SceneElementContent(element, family, textColor,
                        now, power, night, editing, onNight, onDiagnostics, onExit, brightness, onBrightness, onBrightnessFinished,
                        glassBackdrop, Offset((element.x * pixels.width).roundToInt() + with(LocalDensity.current) { (w * element.width * element.horizontalPadding).roundToPx() }.toFloat(),
                            (element.y * pixels.height).roundToInt() + with(LocalDensity.current) { (h * element.height * element.verticalPadding).roundToPx() }.toFloat()),
                        workShift, workShiftBusy, onClockIn, onEditClockIn, h)
                }
            }
        } }
        if (editing) {
            val editable = resolved.filter { editableIds == null || it.id in editableIds }
            // 콘텐츠 순서를 바꾸지 않고 선택한 요소의 조작 영역만 가장 위에 둔다.
            (editable.filterNot { it.id == selectedId } + editable.filter { it.id == selectedId }).forEach { element -> key(element.id) {
                SceneElementEditor(element, element.id == selectedId, element.id in geometryReadOnlyIds, pixels, w, h,
                    onSelect = { currentSelect(it) }, onChange = { currentChange(it) },
                    align = { candidate ->
                        val aligned = if (assist) alignCenters(candidate, currentScene.elements.map { sizer.resolve(it).element }, snapPixels / pixels.width, snapPixels / pixels.height)
                            else AlignmentResult(candidate)
                        guides = aligned; aligned.element
                    }, onGestureEnd = { guides = null })
            } }
        }
        if (editing) guides?.let { guide ->
            Canvas(Modifier.fillMaxSize().testTag("alignment_guides")) {
                val effect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
                guide.vertical?.let { drawLine(Color(0xFF8EE8FF), Offset(it * size.width, 0f), Offset(it * size.width, size.height), 2f, pathEffect = effect) }
                guide.horizontal?.let { drawLine(Color(0xFF8EE8FF), Offset(0f, it * size.height), Offset(size.width, it * size.height), 2f, pathEffect = effect) }
            }
        }
        if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    }
}

@Composable
private fun SceneElementContent(e: SceneElement, family: FontFamily, color: Color, now: LocalDateTime, power: PowerState,
    night: Boolean, editing: Boolean, onNight: () -> Unit, onDiagnostics: () -> Unit, onExit: () -> Unit,
    brightness: Float, onBrightness: (Float) -> Unit, onBrightnessFinished: () -> Unit,
    glassBackdrop: List<GlassBackdrop>, offset: Offset,
    workShift: WorkShiftDisplay?, workShiftBusy: Boolean, onClockIn: () -> Unit, onEditClockIn: () -> Unit, canvasHeight: androidx.compose.ui.unit.Dp,
) {
    if (e.kind == ElementKind.WORK_SHIFT) {
        WorkShiftGauge(workShift, workShiftBusy, editing, night, e.color?.let { Color(it) }, e.gaugeOrientation, onClockIn, onEditClockIn)
        return
    }
    if (e.kind == ElementKind.BRIGHTNESS) {
        GlassyBrightnessSlider(brightness, editing, onBrightness, onBrightnessFinished, backdrop = glassBackdrop, offset = offset)
        return
    }
    if (e.kind == ElementKind.GIF) {
        MediaLayer(e.media, true, Modifier.fillMaxSize(), e.fit, speed = e.speed, loop = e.loop)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val label = when (e.kind) {
            ElementKind.CLOCK -> now.format(DateTimeFormatter.ofPattern("HH:mm"))
            ElementKind.DATE -> now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
            ElementKind.BATTERY -> if (power.percent >= 0) "${power.percent}%" else "—"
            ElementKind.NIGHT_BUTTON -> if (night) "야간 끄기" else "야간 켜기"
            ElementKind.DIAGNOSTICS_BUTTON -> "진단"
            ElementKind.CLOSE_BUTTON -> "닫기"
            ElementKind.GIF, ElementKind.BRIGHTNESS, ElementKind.WORK_SHIFT -> ""
        }
        val button = e.kind in setOf(ElementKind.NIGHT_BUTTON, ElementKind.DIAGNOSTICS_BUTTON, ElementKind.CLOSE_BUTTON)
        val automaticSize = minOf(maxHeight.value * if (e.kind == ElementKind.BATTERY) 0.48f else 0.72f,
            maxWidth.value / when (e.kind) { ElementKind.CLOCK -> 3.5f; ElementKind.DATE -> 12.5f; ElementKind.BATTERY -> 3.4f; else -> label.length + 1.5f })
        val fontScale = LocalDensity.current.fontScale
        val desiredSize = e.textSize?.takeIf { e.hasTextStyle }?.let { it * canvasHeight.value } ?: automaticSize
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        fun labelStyle(size: Float) = TextStyle(fontFamily = family, fontSize = (size / fontScale).coerceAtLeast(1f).sp,
            lineHeight = (size / fontScale * 1.12f).coerceAtLeast(1f).sp, platformStyle = PlatformTextStyle(includeFontPadding = false))
        val density = LocalDensity.current
        val measured = textMeasurer.measure(label, labelStyle(desiredSize), softWrap = false, maxLines = 1)
        val fit = if (e.textSize == null) 1f else minOf(1f,
            with(density) { maxWidth.toPx() } / measured.size.width.coerceAtLeast(1),
            with(density) { maxHeight.toPx() } * (if (e.kind == ElementKind.BATTERY) 0.75f else 1f) / measured.size.height.coerceAtLeast(1))
        val size = desiredSize * fit
        val horizontal = when (e.textAlignment) {
            ElementTextAlignment.LEFT -> Alignment.Start
            ElementTextAlignment.CENTER -> Alignment.CenterHorizontally
            ElementTextAlignment.RIGHT -> Alignment.End
        }
        val click: () -> Unit = when (e.kind) { ElementKind.NIGHT_BUTTON -> onNight; ElementKind.DIAGNOSTICS_BUTTON -> onDiagnostics; else -> onExit }
        val statusSize = minOf(maxHeight.value * 0.14f, maxWidth.value / 8f)
        Column(Modifier.fillMaxSize().then(if (button) Modifier.background(color.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
            .then(if (!editing) Modifier.clickable(onClick = click) else Modifier) else Modifier),
            horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
            Text(label, modifier = Modifier.testTag("text_${e.id}"), color = color, fontFamily = family, fontSize = (size / fontScale).coerceAtLeast(1f).sp,
                lineHeight = (size / fontScale * 1.12f).coerceAtLeast(1f).sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            if (e.kind == ElementKind.BATTERY) Text(power.label, color = color.copy(alpha = 0.7f), fontFamily = family,
                fontSize = (statusSize / fontScale).coerceAtLeast(1f).sp,
                lineHeight = (statusSize / fontScale * 1.12f).coerceAtLeast(1f).sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1)
        }
    }
}

@Composable
fun SceneBackdrop(background: BackgroundSpec, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(background.color))) {
        when (background.kind) {
            BackgroundKind.SOLID -> Unit
            BackgroundKind.PHOTO -> MediaLayer(background.media, false, Modifier.fillMaxSize(), background.fit,
                background.zoom, background.panX, background.panY, widthScale = background.widthScale, heightScale = background.heightScale)
            BackgroundKind.PRESET -> Canvas(Modifier.fillMaxSize()) {
                val colors = when (background.preset) {
                    BackdropPreset.AURORA -> listOf(Color(0xFF061D29), Color(0xFF285C54), Color(0xFF301A45))
                    BackdropPreset.DUSK -> listOf(Color(0xFF231934), Color(0xFFAC5F60), Color(0xFFDFAD83))
                    BackdropPreset.OCEAN -> listOf(Color(0xFF010D21), Color(0xFF123D64), Color(0xFF327B86))
                    BackdropPreset.PAPER -> listOf(Color(0xFFD6CDBB), Color(0xFFA39273), Color(0xFF6B6555))
                }
                drawRect(Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height)))
                drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha = 0.8f), Color.Transparent),
                    Offset(size.width * 0.72f, size.height * 0.3f), size.width * 0.6f),
                    radius = size.width * 0.6f, center = Offset(size.width * 0.72f, size.height * 0.3f))
            }
        }
        if (background.kind != BackgroundKind.SOLID) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
    }
}
