package dev.kwonsu.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.*
import kotlin.random.Random

/** 입력을 가로채지 않는, 한 번 재생하고 사라지는 전체 화면 폭죽. */
@Composable
internal fun WorkShiftFireworks(token: Long, night: Boolean, onFinished: () -> Unit) {
    val time = remember(token) { Animatable(0f) }
    val done by rememberUpdatedState(onFinished)
    val bursts = remember(token) { val random = Random(token); List(9) { index ->
        FireworkBurst(random.nextFloat() * 0.76f + 0.12f, random.nextFloat() * 0.6f + 0.12f,
            index * 0.28f, random.nextFloat() * 0.07f + 0.13f, index % 4)
    } }
    LaunchedEffect(token) { time.animateTo(4.2f, tween(4200, easing = LinearEasing)); done() }
    Canvas(Modifier.fillMaxSize().testTag("work_shift_fireworks").semantics { contentDescription = "퇴근 준비 완료 폭죽" }) {
        val colors = if (night) listOf(Color(0xFFDD8A62), Color(0xFFAA605E), Color(0xFFE2B687), Color(0xFFB27080))
            else listOf(Color(0xFFFFD47D), Color(0xFF97F1C1), Color(0xFF99C9FF), Color(0xFFF2A7CB))
        bursts.forEach { burst ->
            val elapsed = time.value - burst.start
            if (elapsed in 0f..1.85f) {
                val fraction = elapsed / 1.85f
                val radius = minOf(size.width, size.height) * burst.radius * (1f - (1f - fraction).pow(3))
                val center = Offset(size.width * burst.x, size.height * burst.y + size.height * 0.12f * fraction * fraction)
                val alpha = (1f - fraction).pow(1.4f) * if (night) 0.68f else 0.95f
                repeat(36) { ray ->
                    val angle = ray * (2 * PI / 36).toFloat() + burst.start
                    val length = radius * if (ray % 2 == 0) 1f else 0.7f
                    val tip = center + Offset(cos(angle) * length, sin(angle) * length)
                    val tail = center + Offset(cos(angle) * length * 0.83f, sin(angle) * length * 0.83f)
                    val stroke = (size.minDimension * 0.003f).coerceAtLeast(1f)
                    // 밝은 단색·사진 위에서도 입자가 배경과 구분되도록 어두운 외곽을 함께 그린다.
                    drawLine(Color.Black.copy(alpha = alpha * 0.7f), tail, tip, stroke * 2.4f, StrokeCap.Round)
                    drawLine(colors[burst.color].copy(alpha = alpha), tail, tip, stroke, StrokeCap.Round)
                    if (ray % 3 == 0) drawCircle(Color.White.copy(alpha = alpha * 0.7f), size.minDimension * 0.002f, tip)
                }
            }
        }
    }
}

private data class FireworkBurst(val x: Float, val y: Float, val start: Float, val radius: Float, val color: Int)
