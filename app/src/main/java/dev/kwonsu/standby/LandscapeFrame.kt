package dev.kwonsu.standby

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints

/** 시스템 방향 잠금이나 Android 16 큰 화면 정책과 무관하게 시계는 가로 캔버스로만 그린다. */
@Composable
fun LandscapeFrame(fold: FoldInfo, posture: DevicePosture, displayRotation: Int, content: @Composable (FoldInfo) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged {
        Diagnostics.record("host_window_size", "${it.width}x${it.height}px · 실제 앱 창")
    }) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }
        val height = with(density) { maxHeight.roundToPx() }
        val rotate = height > width
        val targetRotation = if (posture == DevicePosture.REVERSE_LANDSCAPE) 3 else 1
        val clockwise = (targetRotation - displayRotation + 4) % 4 == 1
        val adjustedFold = if (rotate) rotateFoldForLandscape(fold, width, height, clockwise) else fold
        // 저장된 디자인을 읽는 동안 내용이 비어 있어도 측정할 컨테이너는 유지한다.
        Layout(content = { Box(Modifier.fillMaxSize()) { content(adjustedFold) } }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val child = measurables.single().measure(if (rotate) Constraints.fixed(h, w) else Constraints.fixed(w, h))
            layout(w, h) {
                child.placeWithLayer((w - child.width) / 2, (h - child.height) / 2) {
                    rotationZ = if (rotate) (if (clockwise) 90f else -90f) else 0f
                }
            }
        }
    }
}
