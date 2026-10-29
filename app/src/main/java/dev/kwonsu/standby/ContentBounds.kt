package dev.kwonsu.standby

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withScale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면 높이 1000 기준으로 측정하고 같은 좌표로 그려, 글꼴의 여백이 배치 영역에 섞이지 않게 한다. */
internal data class InkLine(val text: String, val size: Float, val bounds: RectF, val x: Float, val y: Float, val alpha: Float = 1f)
internal data class InkContent(val width: Float, val height: Float, val lines: List<InkLine>)
internal data class ContentPlacement(val element: SceneElement, val ink: InkContent? = null)

@Composable
internal fun rememberContentSizer(font: ClockFont, aspect: Float, now: LocalDateTime, power: PowerState, canvasHeightDp: Float = 300f): ContentSizer {
    val resolver = LocalFontFamilyResolver.current
    val family = font.family()
    val typeface by remember(resolver, family) { resolver.resolve(family) }
    val density = LocalDensity.current
    return remember(typeface, aspect, now.withSecond(0).withNano(0), power, density, canvasHeightDp) {
        ContentSizer(typeface as Typeface, aspect, now, power) { size ->
            // Android의 큰 글자 비선형 배율까지 반영해 기존 Text의 실제 크기를 보존한다.
            with(density) { (size * canvasHeightDp / fontScale).sp.toPx() } / (canvasHeightDp * density.density)
        }
    }
}

internal class ContentSizer(val typeface: Typeface, val aspect: Float, val now: LocalDateTime, val power: PowerState,
    private val legacySize: (Float) -> Float = { it },
) {
    private fun label(e: SceneElement): String = when (e.kind) {
        ElementKind.CLOCK -> now.format(DateTimeFormatter.ofPattern("HH:mm"))
        ElementKind.DATE -> now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
        ElementKind.BATTERY -> if (power.percent >= 0) "${power.percent}%" else "—"
        else -> ""
    }

    private fun measure(e: SceneElement, size: Float): InkContent {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = this@ContentSizer.typeface }
        fun line(text: String, value: Float): Pair<Float, RectF> {
            paint.textSize = value
            val path = Path().also { paint.getTextPath(text, 0, text.length, 0f, 0f, it) }
            return value to RectF().also { path.computeBounds(it, true) }
        }
        val main = line(label(e), size * 1000f)
        val secondary = if (e.kind == ElementKind.BATTERY) line(power.label, size * 300f) else null
        val width = maxOf(main.second.width(), secondary?.second?.width() ?: 0f) + 2f
        val gap = if (secondary == null) 0f else size * 100f
        val height = main.second.height() + (secondary?.second?.height() ?: 0f) + gap + 2f
        fun left(bounds: RectF) = when (e.textAlignment) {
            ElementTextAlignment.LEFT -> 1f
            ElementTextAlignment.CENTER -> (width - bounds.width()) / 2f
            ElementTextAlignment.RIGHT -> width - bounds.width() - 1f
        }
        val lines = listOf(InkLine(label(e), main.first, main.second, left(main.second), 1f)) +
            listOfNotNull(secondary?.let { InkLine(power.label, it.first, it.second, left(it.second), 1f + main.second.height() + gap, 0.7f) })
        return InkContent(width.coerceAtLeast(1f), height.coerceAtLeast(1f), lines)
    }

    fun resolve(source: SceneElement): ContentPlacement {
        val e = source.normalized()
        if (e.contentSize == null || !e.hasTextStyle) return ContentPlacement(e.anchored())
        var ink = measure(e, e.contentSize)
        val availableWidth = 1f - 2f * e.horizontalPadding
        val availableHeight = 1f - 2f * e.verticalPadding
        val fit = minOf(1f, availableWidth * aspect * 1000f / ink.width, availableHeight * 1000f / ink.height)
        val size = e.contentSize * fit
        if (fit < 1f) ink = measure(e, size)
        return ContentPlacement(e.copy(width = (ink.width / (aspect * 1000f * availableWidth)).coerceAtMost(1f),
            height = (ink.height / (1000f * availableHeight)).coerceAtMost(1f), contentSize = size).anchored(), ink)
    }

    fun fit(source: SceneElement): SceneElement {
        if (!source.hasTextStyle || source.contentSize != null) return resolve(source).element
        val innerWidth = source.width * (1f - 2f * source.horizontalPadding)
        val innerHeight = source.height * (1f - 2f * source.verticalPadding)
        var size = legacySize(source.textSize ?: minOf(innerHeight * if (source.kind == ElementKind.BATTERY) 0.48f else 0.72f,
            innerWidth * aspect / when (source.kind) { ElementKind.CLOCK -> 3.5f; ElementKind.DATE -> 12.5f; else -> 3.4f }))
        if (source.textSize != null) {
            val paint = Paint().apply { typeface = this@ContentSizer.typeface; textSize = size * 1000f }
            size *= minOf(1f, innerWidth * aspect * 1000f / paint.measureText(label(source)).coerceAtLeast(1f),
                innerHeight * (if (source.kind == ElementKind.BATTERY) 0.75f else 1f) / (size * 1.12f))
        }
        val fitted = resolve(source.copy(contentSize = size)).element
        val horizontal = when (source.textAlignment) { ElementTextAlignment.LEFT -> 0f; ElementTextAlignment.CENTER -> 0.5f; ElementTextAlignment.RIGHT -> 1f }
        return fitted.copy(x = source.x + (source.width - fitted.width) * horizontal,
            y = source.y + (source.height - fitted.height) / 2f).normalized().anchored()
    }
}

@Composable
internal fun TightTextContent(id: String, content: InkContent, typeface: Typeface, color: Color) {
    val paint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { this.typeface = typeface } }
    Canvas(Modifier.fillMaxSize().testTag("text_$id").semantics { text = AnnotatedString(content.lines.joinToString("\n") { it.text }) }) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withScale(size.width / content.width, size.height / content.height) {
                content.lines.forEach { line ->
                    paint.textSize = line.size; paint.color = color.copy(alpha = color.alpha * line.alpha).toArgb()
                    drawText(line.text, line.x - line.bounds.left, line.y - line.bounds.top, paint)
                }
            }
        }
    }
}
