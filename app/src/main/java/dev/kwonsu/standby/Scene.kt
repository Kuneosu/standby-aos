package dev.kwonsu.standby

import kotlin.math.max

enum class ClockFont(val label: String) {
    SYSTEM("기본"), ESAMANRU("이사만루"), MAPLESTORY("메이플스토리"), KKUKKUKK("메모먼트꾹꾹체")
}
enum class BackgroundKind(val label: String) { SOLID("색상"), PRESET("프리셋"), PHOTO("사진") }
enum class PhotoFit(val label: String) { COVER("채우기"), CONTAIN("맞추기"), STRETCH("늘이기") }
enum class BackdropPreset(val label: String) { AURORA("오로라"), DUSK("노을"), OCEAN("심해"), PAPER("샌드") }
enum class ElementKind(val label: String) {
    CLOCK("시계"), DATE("날짜"), BATTERY("배터리"), NIGHT_BUTTON("야간 버튼"), DIAGNOSTICS_BUTTON("진단 버튼"), CLOSE_BUTTON("닫기 버튼"), BRIGHTNESS("밝기 슬라이더"), WORK_SHIFT("출퇴근 게이지"), GIF("GIF")
}
enum class GaugeOrientation(val label: String) { HORIZONTAL("가로"), VERTICAL("세로") }
enum class ElementTextAlignment(val label: String) { LEFT("왼쪽"), CENTER("가운데"), RIGHT("오른쪽") }
enum class CanvasAnchor { FREE, START, CENTER, END }

data class BackgroundSpec(
    val kind: BackgroundKind = BackgroundKind.SOLID,
    val color: Long = 0xFF050708,
    val preset: BackdropPreset = BackdropPreset.AURORA,
    val media: String? = null,
    val fit: PhotoFit = PhotoFit.COVER,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val dim: Float = 0.2f,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f,
) {
    fun normalized() = copy(color = opaque(color), media = validMediaName(media),
        zoom = bounded(zoom, 0.5f, 3f, 1f), panX = bounded(panX, -1f, 1f, 0f),
        panY = bounded(panY, -1f, 1f, 0f), dim = bounded(dim, 0f, 0.9f, 0.2f),
        widthScale = bounded(widthScale, 0.5f, 2f, 1f), heightScale = bounded(heightScale, 0.5f, 2f, 1f))
}

/** 위치와 크기는 가로 캔버스에 대한 비율로 저장한다. 해상도가 바뀌어도 같은 배치를 유지한다. */
data class SceneElement(
    val id: String,
    val kind: ElementKind,
    val x: Float = 0.1f,
    val y: Float = 0.1f,
    val width: Float = 0.3f,
    val height: Float = 0.25f,
    val color: Long? = null,
    val media: String? = null,
    val fit: PhotoFit = PhotoFit.CONTAIN,
    val opacity: Float = 1f,
    val speed: Float = 1f,
    val loop: Boolean = true,
    val positionLocked: Boolean = false,
    val gaugeOrientation: GaugeOrientation = GaugeOrientation.HORIZONTAL,
    val sharedSlot: LayoutSlot? = null,
    val textAlignment: ElementTextAlignment = ElementTextAlignment.CENTER,
    /** null은 기존 자동 크기이며, 직접 지정한 크기는 캔버스 높이에 대한 비율이다. */
    val textSize: Float? = null,
    /** 실제 글자 영역으로 배치할 때 사용하는 크기. 공통 배치의 크기에 포함한다. */
    val contentSize: Float? = null,
    val horizontalAnchor: CanvasAnchor = CanvasAnchor.FREE,
    val verticalAnchor: CanvasAnchor = CanvasAnchor.FREE,
    /** 각 변의 내부 여백. 가로는 요소 너비, 세로는 요소 높이에 대한 비율이다. */
    val horizontalPadding: Float = 0f,
    val verticalPadding: Float = 0f,
) {
    fun normalized(): SceneElement {
        val minimum = if (contentSize != null && hasTextStyle) 0.005f else 0.06f
        val w = bounded(width, minimum, 1f, 0.3f)
        val h = bounded(height, minimum, 1f, 0.25f)
        return copy(width = w, height = h, x = bounded(x, 0f, 1f - w, 0f), y = bounded(y, 0f, 1f - h, 0f),
            color = color?.let(::opaque), media = validMediaName(media),
            opacity = bounded(opacity, 0.1f, 1f, 1f), speed = bounded(speed, 0.25f, 3f, 1f),
            textSize = textSize?.let { bounded(it, 0.02f, 0.65f, 0.16f) },
            contentSize = contentSize?.takeIf { hasTextStyle }?.let { bounded(it, 0.02f, 1.5f, 0.16f) },
            horizontalPadding = bounded(horizontalPadding, 0f, 0.4f, 0f), verticalPadding = bounded(verticalPadding, 0f, 0.4f, 0f),
            sharedSlot = sharedSlot?.takeIf { it.kind == kind && it.occurrence in 0 until StandbyScene.MAX_STORED_ELEMENTS })
    }
}

val SceneElement.hasTextStyle: Boolean get() = kind in setOf(ElementKind.CLOCK, ElementKind.DATE, ElementKind.BATTERY)

fun SceneElement.automaticTextSize(canvasAspect: Float): Float = minOf(height * (1f - 2f * verticalPadding) * if (kind == ElementKind.BATTERY) 0.48f else 0.72f,
    width * (1f - 2f * horizontalPadding) * canvasAspect / when (kind) { ElementKind.CLOCK -> 3.5f; ElementKind.DATE -> 12.5f; else -> 3.4f }).coerceIn(0.02f, 0.65f)

enum class ResizeCorner(val left: Boolean, val top: Boolean, val arrow: String) {
    TOP_LEFT(true, true, "↖"), TOP_RIGHT(false, true, "↗"), BOTTOM_LEFT(true, false, "↙"), BOTTOM_RIGHT(false, false, "↘")
}

/** 반대쪽 모서리를 유지하므로 화면 끝에 붙인 요소도 안쪽 손잡이로 키울 수 있다. */
fun SceneElement.resizedFrom(corner: ResizeCorner, dx: Float, dy: Float): SceneElement {
    if (positionLocked || !dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return this
    if (contentSize != null) {
        val sx = 1f + (if (corner.left) -dx else dx) / width
        val sy = 1f + (if (corner.top) -dy else dy) / height
        val requested = if (kotlin.math.abs(sx - 1f) >= kotlin.math.abs(sy - 1f)) sx else sy
        val limitX = (if (corner.left) x + width else 1f - x) / width
        val limitY = (if (corner.top) y + height else 1f - y) / height
        val scale = requested.coerceIn(0.02f / contentSize, minOf(limitX, limitY, 1.5f / contentSize).coerceAtLeast(0.02f / contentSize))
        return copy(x = if (corner.left) x + width * (1f - scale) else x,
            y = if (corner.top) y + height * (1f - scale) else y, width = width * scale, height = height * scale,
            contentSize = contentSize * scale,
            horizontalAnchor = horizontalAnchor.takeUnless { it == CanvasAnchor.CENTER || (corner.left && it == CanvasAnchor.START) || (!corner.left && it == CanvasAnchor.END) } ?: CanvasAnchor.FREE,
            verticalAnchor = verticalAnchor.takeUnless { it == CanvasAnchor.CENTER || (corner.top && it == CanvasAnchor.START) || (!corner.top && it == CanvasAnchor.END) } ?: CanvasAnchor.FREE).normalized()
    }
    val left = if (corner.left) (x + dx).coerceIn(0f, (x + width - 0.06f).coerceAtLeast(0f)) else x
    val right = if (corner.left) x + width else (x + width + dx).coerceIn((x + 0.06f).coerceAtMost(1f), 1f)
    val top = if (corner.top) (y + dy).coerceIn(0f, (y + height - 0.06f).coerceAtLeast(0f)) else y
    val bottom = if (corner.top) y + height else (y + height + dy).coerceIn((y + 0.06f).coerceAtMost(1f), 1f)
    return copy(x = left, y = top, width = right - left, height = bottom - top,
        horizontalAnchor = CanvasAnchor.FREE, verticalAnchor = CanvasAnchor.FREE).normalized()
}

fun SceneElement.anchored(): SceneElement {
    fun position(anchor: CanvasAnchor, value: Float, size: Float) = when (anchor) {
        CanvasAnchor.FREE -> value
        CanvasAnchor.START -> 0f
        CanvasAnchor.CENTER -> (1f - size) / 2f
        CanvasAnchor.END -> 1f - size
    }.coerceIn(0f, (1f - size).coerceAtLeast(0f))
    return copy(x = position(horizontalAnchor, x, width), y = position(verticalAnchor, y, height))
}

data class StandbyScene(
    val font: ClockFont = ClockFont.SYSTEM,
    val textColor: Long = 0xFFA6E8CB,
    val background: BackgroundSpec = BackgroundSpec(),
    val elements: List<SceneElement> = defaultElements(),
) {
    fun normalized(): StandbyScene {
        var localCount = 0
        val sharedSlots = mutableSetOf<LayoutSlot>()
        val kept = elements.distinctBy { it.id }.map { it.normalized() }.filter { element ->
            element.sharedSlot?.let { sharedSlots.add(it) } ?: (localCount++ < MAX_ELEMENTS)
        }.take(MAX_STORED_ELEMENTS)
        return copy(textColor = opaque(textColor), background = background.normalized(), elements = kept)
    }
    fun mediaNames(): Set<String> = (listOfNotNull(background.media) + elements.mapNotNull { it.media }).toSet()
    companion object { const val MAX_ELEMENTS = 20; const val MAX_STORED_ELEMENTS = MAX_ELEMENTS * 2 }
}

fun defaultElements() = listOf(
    SceneElement("clock", ElementKind.CLOCK, 0.06f, 0.22f, 0.58f, 0.36f),
    SceneElement("date", ElementKind.DATE, 0.12f, 0.61f, 0.46f, 0.1f),
    SceneElement("battery", ElementKind.BATTERY, 0.73f, 0.25f, 0.2f, 0.24f),
    SceneElement("night", ElementKind.NIGHT_BUTTON, 0.68f, 0.64f, 0.15f, 0.12f),
    SceneElement("close", ElementKind.CLOSE_BUTTON, 0.84f, 0.64f, 0.12f, 0.12f),
    SceneElement("diagnostics", ElementKind.DIAGNOSTICS_BUTTON, 0.75f, 0.8f, 0.15f, 0.1f),
)

fun newElement(id: String, kind: ElementKind, media: String? = null): SceneElement = when (kind) {
    ElementKind.CLOCK -> SceneElement(id, kind, 0.2f, 0.25f, 0.55f, 0.35f)
    ElementKind.DATE -> SceneElement(id, kind, 0.25f, 0.6f, 0.45f, 0.12f)
    ElementKind.BATTERY -> SceneElement(id, kind, 0.7f, 0.25f, 0.23f, 0.25f)
    ElementKind.NIGHT_BUTTON, ElementKind.DIAGNOSTICS_BUTTON, ElementKind.CLOSE_BUTTON -> SceneElement(id, kind, 0.4f, 0.7f, 0.2f, 0.15f)
    ElementKind.GIF -> SceneElement(id, kind, 0.35f, 0.2f, 0.3f, 0.55f, media = media)
    ElementKind.BRIGHTNESS -> SceneElement(id, kind, 0.28f, 0.82f, 0.44f, 0.14f)
    ElementKind.WORK_SHIFT -> SceneElement(id, kind, 0.24f, 0.72f, 0.52f, 0.16f)
}

fun SceneElement.withGaugeOrientation(next: GaugeOrientation): SceneElement {
    if (next == gaugeOrientation) return this
    if (positionLocked) return copy(gaugeOrientation = next)
    return copy(gaugeOrientation = next, width = height, height = width,
        x = x + (width - height) / 2, y = y + (height - width) / 2).normalized()
}

fun SceneElement.applyEdit(next: SceneElement) = (if (positionLocked && next.positionLocked)
    next.copy(x = x, y = y, width = width, height = height, contentSize = contentSize,
        horizontalAnchor = horizontalAnchor, verticalAnchor = verticalAnchor,
        horizontalPadding = horizontalPadding, verticalPadding = verticalPadding) else next).normalized()

internal fun bounded(value: Float, low: Float, high: Float, fallback: Float) =
    (if (value.isFinite()) value else fallback).coerceIn(low, max(low, high))
internal fun opaque(color: Long) = (color and 0xFFFFFF) or 0xFF000000
internal fun validMediaName(name: String?) = name?.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.(img|gif)")) }
fun parseHexColor(text: String): Long? = text.removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }
    ?.toLongOrNull(16)?.let(::opaque)

data class GifFrame(val timeMillis: Int, val finished: Boolean)
/** GIF 파일의 반복 메타데이터 대신 사용자가 선택한 반복·속도를 적용한다. */
fun gifFrame(elapsedMillis: Long, durationMillis: Int, speed: Float, loop: Boolean): GifFrame {
    val duration = durationMillis.coerceAtLeast(1)
    val position = (elapsedMillis.coerceAtLeast(0) * bounded(speed, 0.25f, 3f, 1f).toDouble()).toLong()
    return if (loop) GifFrame((position % duration).toInt(), false)
    else GifFrame(position.coerceAtMost((duration - 1).toLong()).toInt(), position >= duration)
}
