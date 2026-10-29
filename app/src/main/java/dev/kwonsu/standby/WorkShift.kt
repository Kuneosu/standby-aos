package dev.kwonsu.standby

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

enum class WorkShiftPhase { READY, WORKING, COMPLETE, COOLDOWN }

data class WorkShiftDisplay(val phase: WorkShiftPhase, val progress: Float = 0f, val deadline: Long = 0L, val remainingMillis: Long = 0L) {
    val percent: Int get() = if (phase == WorkShiftPhase.COMPLETE) 100 else (progress * 100).toInt().coerceIn(0, 99)
    companion object { val Preview = WorkShiftDisplay(WorkShiftPhase.WORKING, 0.65f) }
}

/** 근무 상태는 프리셋·요소 배치와 분리해 한 번만 저장한다. */
data class WorkShift(val startedAt: Long = 0L, val deadline: Long = 0L, val checkedOutAt: Long = 0L, val celebrated: Boolean = false) {
    fun display(now: Long): WorkShiftDisplay {
        if (startedAt > 0 && deadline > startedAt) {
            if (now >= deadline + REST_MILLIS) return WorkShiftDisplay(WorkShiftPhase.READY)
            if (now >= deadline) return if (celebrated)
                WorkShiftDisplay(WorkShiftPhase.COOLDOWN, deadline = deadline, remainingMillis = deadline + REST_MILLIS - now)
                else WorkShiftDisplay(WorkShiftPhase.COMPLETE, 1f, deadline)
            val progress = ((now - startedAt).toDouble() / (deadline - startedAt)).coerceIn(0.0, 1.0).toFloat()
            return WorkShiftDisplay(WorkShiftPhase.WORKING, progress, deadline, (deadline - now).coerceAtLeast(0))
        }
        return WorkShiftDisplay(WorkShiftPhase.READY)
    }

    fun clockIn(now: Long, selectedStart: Long, zone: ZoneId): WorkShift = if (display(now).phase != WorkShiftPhase.READY) this
        else WorkShift(startedAt = selectedStart, deadline = workShiftDeadline(selectedStart, zone)).autoComplete(now)

    /** 기존 근무의 날짜는 유지한다. 수정 가능한 기록이 아니면 null을 반환한다. */
    fun changeStart(now: Long, selectedStart: Long, zone: ZoneId): WorkShift? {
        if (display(now).phase == WorkShiftPhase.READY || selectedStart <= 0L) return null
        val originalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
        if (Instant.ofEpochMilli(selectedStart).atZone(zone).toLocalDate() != originalDate) return null
        if (selectedStart == startedAt) return this
        return WorkShift(startedAt = selectedStart, deadline = workShiftDeadline(selectedStart, zone)).autoComplete(now)
    }

    /** 늦게 화면을 열어도 실제 100% 도달 시각을 보존한다. */
    fun autoComplete(now: Long): WorkShift = if (startedAt > 0 && deadline > startedAt && now >= deadline)
        copy(checkedOutAt = deadline) else this

    fun claimCelebration(now: Long): WorkShift = if (display(now).phase == WorkShiftPhase.COMPLETE)
        autoComplete(now).copy(celebrated = true) else this

    /** 구버전 수동 퇴근 기록은 마감 시각을 지워 복원이 불가능하므로 대기를 해제한다. */
    fun migrateLegacyCheckout(): WorkShift = if (startedAt == 0L && deadline == 0L && checkedOutAt > 0L) WorkShift() else this

    companion object {
        const val WORK_MILLIS = 9 * 60 * 60 * 1000L
        const val REST_MILLIS = 12 * 60 * 60 * 1000L
    }
}

/** 출근 당일 17시와 출근 후 9시간 중 늦은 순간을 확정한다. */
fun workShiftDeadline(startedAt: Long, zone: ZoneId): Long {
    val local = Instant.ofEpochMilli(startedAt).atZone(zone)
    val earliest = local.toLocalDate().atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
    return max(startedAt + WorkShift.WORK_MILLIS, earliest)
}
