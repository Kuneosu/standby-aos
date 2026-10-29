package dev.kwonsu.standby

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import kotlin.math.min
import kotlin.math.roundToInt

/** 내용은 움직이지 않고 아래쪽 경계가 열리는 아코디언. */
@Composable
internal fun NotificationHistoryReveal(expanded: Boolean, viewportHeight: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fraction by animateFloatAsState(if (expanded) 1f else 0f,
        tween(if (expanded) 360 else 320, easing = FastOutSlowInEasing), label = "알림 기록 펼침 높이")
    if (expanded || fraction > 0f) {
        Layout(content = { Box { content() } }, modifier = modifier.clipToBounds()) { measurables, constraints ->
            val body = measurables.single().measure(constraints.copy(minHeight = 0))
            // 긴 기록 전체를 수백 화면 높이로 애니메이션하면 첫 프레임에 화면이 가득 찬다.
            // 보이는 한 화면만 먼저 열고, 끝난 뒤 나머지는 같은 위치에서 스크롤한다.
            val travel = min(body.height, viewportHeight.coerceAtLeast(1))
            val height = if (expanded && fraction >= 1f) body.height else (travel * fraction).roundToInt()
            layout(body.width, height) { body.placeRelative(0, 0) }
        }
    }
}
