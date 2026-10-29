package dev.kwonsu.standby

import kotlin.math.abs

data class AlignmentResult(val element: SceneElement, val vertical: Float? = null, val horizontal: Float? = null)

/** 임계값은 화면 픽셀을 캔버스 비율로 바꿔 전달한다. 가이드와 실제 스냅 위치를 일치시킨다. */
fun alignCenters(candidate: SceneElement, others: List<SceneElement>, thresholdX: Float, thresholdY: Float): AlignmentResult {
    val item = candidate.normalized()
    if (item.positionLocked) return AlignmentResult(item)
    fun nearest(center: Float, extent: Float, targets: List<Float>, threshold: Float) = targets
        .filter { it - extent / 2 >= 0 && it + extent / 2 <= 1 && abs(it - center) <= threshold }
        .minByOrNull { abs(it - center) }
    val targets = others.filter { it.id != item.id }
    val vertical = nearest(item.x + item.width / 2, item.width, targets.map { it.x + it.width / 2 } + 0.5f, thresholdX)
    val horizontal = nearest(item.y + item.height / 2, item.height, targets.map { it.y + it.height / 2 } + 0.5f, thresholdY)
    return AlignmentResult(item.copy(x = vertical?.minus(item.width / 2) ?: item.x, y = horizontal?.minus(item.height / 2) ?: item.y), vertical, horizontal)
}
