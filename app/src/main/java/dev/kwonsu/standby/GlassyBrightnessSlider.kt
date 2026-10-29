package dev.kwonsu.standby

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ceil
import kotlin.math.roundToInt

internal val GlassOpacity = SemanticsPropertyKey<Float>("GlassOpacity")

@Composable
internal fun GlassyBrightnessSlider(value: Float, editing: Boolean, onChange: (Float) -> Unit, onFinished: () -> Unit,
    modifier: Modifier = Modifier, backdrop: List<GlassBackdrop> = emptyList(), offset: Offset = Offset.Zero,
) {
    val frostedLayer = rememberGraphicsLayer()
    var interacting by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(false) }
    var touchVersion by remember { mutableIntStateOf(0) }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(interacting, touchVersion) {
        if (interacting || touchVersion > 0) {
            recent = true
            if (!interacting) { delay(1400); recent = false }
        }
    }
    val opacity by animateFloatAsState(if (editing || interacting || recent) 1f else 0.16f, tween(300), label = "밝기 유리 투명도")
    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = opacity }.testTag("brightness_slider")
        .semantics {
            contentDescription = "화면 밝기"
            stateDescription = "${(clampBrightness(value) * 100).toInt()}%"
            this[GlassOpacity] = opacity
            if (!editing) {
                progressBarRangeInfo = ProgressBarRangeInfo(clampBrightness(value), 0.02f..1f)
                setProgress { target -> touchVersion++; change(clampBrightness(target)); finish(); true }
            }
        }.then(if (editing) Modifier else Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(x: Float) { change(0.02f + ((x / size.width - 0.18f) / 0.64f).coerceIn(0f, 1f) * 0.98f) }
                interacting = true; touchVersion++; down.consume(); update(down.position.x)
                try { drag(down.id) { pointer -> pointer.consume(); update(pointer.position.x) } }
                finally { interacting = false; finish() }
            }
        })) {
        val edge = 0.6.dp.toPx()
        val radius = CornerRadius(size.height / 2)
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
        // 슬라이더 주변까지 표본을 잡아 블러 가장자리에 사각형 이음새가 생기지 않게 한다.
        if (backdrop.isNotEmpty()) {
            val blur = 14.dp.toPx()
            val padding = ceil(blur * 2).toInt()
            frostedLayer.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            frostedLayer.record(size = IntSize(size.width.roundToInt() + padding * 2, size.height.roundToInt() + padding * 2)) {
                backdrop.forEach { source ->
                    translate(padding + source.offset.x - offset.x, padding + source.offset.y - offset.y) { drawLayer(source.layer) }
                }
            }
            clipPath(outline) { translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(frostedLayer) } }
        }
        // 무채색의 균일한 반투명 표면. 색상 변화는 뒤쪽 화면에서만 들어온다.
        drawRoundRect(Color.Black.copy(alpha = 0.04f), cornerRadius = radius)
        drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = radius)
        drawRoundRect(Color.White.copy(alpha = 0.32f),
            topLeft = Offset(edge / 2, edge / 2), size = Size((size.width - edge).coerceAtLeast(0f), (size.height - edge).coerceAtLeast(0f)),
            cornerRadius = radius, style = Stroke(edge))
        drawRoundRect(Color.Black.copy(alpha = 0.05f),
            topLeft = Offset(edge * 1.5f, edge * 1.5f), size = Size((size.width - edge * 3).coerceAtLeast(0f), (size.height - edge * 3).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((radius.x - edge).coerceAtLeast(0f)), style = Stroke(edge / 2))
        val y = size.height / 2
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val end = left + (right - left) * ((clampBrightness(value) - 0.02f) / 0.98f)
        val thickness = (size.height * 0.065f).coerceAtLeast(1f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(left, y), Offset(right, y), thickness, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.88f), Offset(left, y), Offset(end, y), thickness, StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.2f), size.height * 0.15f, Offset(end, y + 1))
        drawCircle(Color.White, size.height * 0.125f, Offset(end, y))
        listOf(size.width * 0.08f to 0.085f, size.width * 0.92f to 0.115f).forEach { (x, factor) ->
            val r = size.height * factor
            drawCircle(Color.White.copy(alpha = 0.9f), r, Offset(x, y), style = Stroke((size.height * 0.025f).coerceAtLeast(1f)))
            repeat(8) { i ->
                val angle = i * Math.PI / 4
                fun point(scale: Float) = Offset(x + cos(angle).toFloat() * r * scale, y + sin(angle).toFloat() * r * scale)
                drawLine(Color.White.copy(alpha = 0.8f), point(1.55f), point(2.05f), (size.height * 0.025f).coerceAtLeast(1f), StrokeCap.Round)
            }
        }
    }
}
