package dev.kwonsu.standby

import androidx.compose.animation.core.CubicBezierEasing

/** 빠르게 반응한 뒤 천천히 멈추는 패널·목록 공통 곡선. */
internal object NotificationMotion {
    val Settle = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)
}
