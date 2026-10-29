package dev.kwonsu.standby

import kotlin.math.abs

enum class StandbyDragAction { WAITING, NEXT_PRESET, OPEN_NOTIFICATIONS, IGNORE }

/** 첫 명확한 이동 방향을 고정해 대각선 드래그가 두 동작을 함께 실행하지 않게 한다. */
fun classifyStandbyDrag(x: Float, y: Float, threshold: Float, canCycle: Boolean): StandbyDragAction = when {
    abs(y) > threshold && abs(y) > abs(x) * 1.15f -> if (canCycle) StandbyDragAction.NEXT_PRESET else StandbyDragAction.IGNORE
    abs(x) > threshold && abs(x) > abs(y) * 1.15f -> if (x < 0f) StandbyDragAction.OPEN_NOTIFICATIONS else StandbyDragAction.IGNORE
    else -> StandbyDragAction.WAITING
}
