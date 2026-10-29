package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WorkShiftTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun time(hour: Int, minute: Int = 0) = at("2026-10-26T%02d:%02d:00".format(hour, minute))

    @Test fun earlyStartsNeverFinishBeforeSeventeen() {
        listOf(0, 6, 7, 8).forEach { assertEquals(time(17), workShiftDeadline(time(it), zone)) }
    }
    @Test fun correctingStartEarlierOrLaterRecalculatesDeadlineAndProgress() {
        val original = WorkShift().clockIn(time(10), time(10), zone)
        val earlier = original.changeStart(time(12), time(8), zone)!!
        assertEquals(time(17), earlier.deadline)
        assertEquals(4f / 9, earlier.display(time(12)).progress, 0.00001f)
        val later = earlier.changeStart(time(12), time(9), zone)!!
        assertEquals(time(18), later.deadline)
        assertEquals(3f / 9, later.display(time(12)).progress, 0.00001f)
        assertEquals(0L, later.checkedOutAt)
        assertFalse(later.celebrated)
        assertEquals(0f, later.changeStart(time(12), time(13), zone)!!.display(time(12)).progress, 0f)
    }
    @Test fun correctingPastDeadlineCompletesAtNewDeadlineAndLaterCorrectionCanResumeSameShift() {
        val active = WorkShift().clockIn(time(9), time(9), zone)
        val completed = active.changeStart(time(17, 30), time(8), zone)!!
        assertEquals(WorkShiftPhase.COMPLETE, completed.display(time(17, 30)).phase)
        assertEquals(time(17), completed.checkedOutAt)
        assertFalse(completed.celebrated)
        val resumed = completed.changeStart(time(17, 30), time(9), zone)!!
        assertEquals(WorkShiftPhase.WORKING, resumed.display(time(17, 30)).phase)
        assertEquals(0L, resumed.checkedOutAt)
    }
    @Test fun confirmingUnchangedTimeKeepsCompletionAndCelebrationRecords() {
        val done = WorkShift().clockIn(time(8), time(8), zone).autoComplete(time(17)).claimCelebration(time(17))
        assertEquals(done, done.changeStart(time(18), time(8), zone))
    }
    @Test fun correctingOvernightShiftKeepsOriginalDateAndRejectsDifferentDayOrExpiredSession() {
        val overnight = WorkShift().clockIn(time(22), time(22), zone)
        val afterMidnight = at("2026-10-27T02:00:00")
        val fixed = overnight.changeStart(afterMidnight, time(21), zone)!!
        assertEquals(at("2026-10-27T06:00:00"), fixed.deadline)
        assertEquals(5f / 9, fixed.display(afterMidnight).progress, 0.00001f)
        assertNull(overnight.changeStart(afterMidnight, at("2026-10-27T01:00:00"), zone))
        assertNull(overnight.changeStart(overnight.deadline + WorkShift.REST_MILLIS, time(21), zone))
        assertNull(WorkShift().changeStart(time(12), time(8), zone))
    }
    @Test fun startAfterEightAddsExactlyNineHoursIncludingNextDay() {
        assertEquals(time(17, 1), workShiftDeadline(time(8, 1), zone))
        assertEquals(time(19, 30), workShiftDeadline(time(10, 30), zone))
        assertEquals(at("2026-10-27T07:15:00"), workShiftDeadline(time(22, 15), zone))
    }
    @Test fun lateEntryUsesChosenTimeAndAlreadyElapsedProgress() {
        val shift = WorkShift().clockIn(time(10), time(8), zone)
        assertEquals(time(17), shift.deadline)
        assertEquals(2f / 9, shift.display(time(10)).progress, 0.00001f)
        assertEquals(22, shift.display(time(10)).percent)
    }
    @Test fun earlyStartFillsAcrossTheWholeIntervalToSeventeen() {
        val shift = WorkShift().clockIn(time(7), time(7), zone)
        assertEquals(0.5f, shift.display(time(12)).progress, 0.00001f)
    }
    @Test fun futureSelectionStaysAtZeroUntilSelectedStart() {
        val shift = WorkShift().clockIn(time(7), time(8), zone)
        assertEquals(0f, shift.display(time(7)).progress, 0f)
        assertEquals(0f, shift.display(time(8)).progress, 0f)
    }
    @Test fun completionBoundaryNeverDisplaysOneHundredEarly() {
        val shift = WorkShift().clockIn(time(8), time(8), zone)
        assertEquals(WorkShiftPhase.WORKING, shift.display(time(17) - 1).phase)
        assertEquals(99, shift.display(time(17) - 1).percent)
        assertEquals(WorkShiftPhase.COMPLETE, shift.display(time(17)).phase)
        assertEquals(100, shift.display(time(17)).percent)
    }
    @Test fun backdatedCompletedShiftCanFinishImmediately() {
        val shift = WorkShift().clockIn(time(18), time(8), zone)
        assertEquals(WorkShiftPhase.COMPLETE, shift.display(time(18)).phase)
        assertTrue(shift.claimCelebration(time(18)).celebrated)
    }
    @Test fun duplicateClockInAndEarlyCompletionDoNotChangeSession() {
        val shift = WorkShift().clockIn(time(8), time(8), zone)
        assertEquals(shift, shift.clockIn(time(10), time(10), zone))
        assertEquals(shift, shift.autoComplete(time(17) - 1))
    }
    @Test fun celebrationIsClaimedOnlyOnceAndOnlyAfterCompletion() {
        val shift = WorkShift().clockIn(time(8), time(8), zone)
        assertEquals(shift, shift.claimCelebration(time(17) - 1))
        val claimed = shift.claimCelebration(time(17))
        assertTrue(claimed.celebrated)
        assertEquals(claimed, claimed.claimCelebration(time(18)))
    }
    @Test fun lateCompletionStillStartsRestAtDeadlineAndRetainsOriginalShift() {
        val end = time(17)
        val shift = WorkShift().clockIn(time(8), time(8), zone).autoComplete(time(23)).claimCelebration(time(23))
        assertEquals(time(8), shift.startedAt)
        assertEquals(end, shift.deadline)
        assertEquals(end, shift.checkedOutAt)
        assertEquals(6 * 60 * 60 * 1000L, shift.display(time(23)).remainingMillis)
        assertEquals(WorkShiftPhase.COOLDOWN, shift.display(end + WorkShift.REST_MILLIS - 1).phase)
        assertEquals(shift, shift.clockIn(end + WorkShift.REST_MILLIS - 1, time(8), zone))
        assertEquals(WorkShiftPhase.READY, shift.display(end + WorkShift.REST_MILLIS).phase)
        val next = shift.clockIn(end + WorkShift.REST_MILLIS, at("2026-10-27T08:00:00"), zone)
        assertFalse(next.celebrated)
        assertEquals(0L, next.checkedOutAt)
        assertEquals(at("2026-10-27T17:00:00"), next.deadline)
    }
    @Test fun reopeningAfterTwelveHoursDoesNotReplayOldFireworksOrRestartRest() {
        val shift = WorkShift().clockIn(time(8), time(8), zone)
        val boundary = shift.deadline + WorkShift.REST_MILLIS
        assertEquals(WorkShiftPhase.COMPLETE, shift.display(boundary - 1).phase)
        assertEquals(WorkShiftPhase.READY, shift.display(boundary).phase)
        assertEquals(shift, shift.claimCelebration(boundary))
        val completed = shift.autoComplete(at("2026-10-27T10:00:00"))
        assertEquals(time(17), completed.checkedOutAt)
        assertEquals(WorkShiftPhase.READY, completed.display(at("2026-10-27T10:00:00")).phase)
        assertEquals(completed, completed.autoComplete(at("2026-10-27T12:00:00")))
    }
    @Test fun legacyCheckoutWithoutDeadlineIsReleasedButActiveAndNewRecordsArePreserved() {
        assertEquals(WorkShift(), WorkShift(checkedOutAt = time(23)).migrateLegacyCheckout())
        val active = WorkShift().clockIn(time(8), time(8), zone)
        assertEquals(active, active.migrateLegacyCheckout())
        val completed = active.autoComplete(time(17)).claimCelebration(time(17))
        assertEquals(completed, completed.migrateLegacyCheckout())
    }
    @Test fun orientationChangesShapeButKeepsLockedPlacement() {
        val horizontal = newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.3f, y = 0.3f, width = 0.4f, height = 0.1f)
        val vertical = horizontal.withGaugeOrientation(GaugeOrientation.VERTICAL)
        assertEquals(0.1f, vertical.width, 0.0001f)
        assertEquals(0.4f, vertical.height, 0.0001f)
        assertEquals(horizontal.x + horizontal.width / 2, vertical.x + vertical.width / 2, 0.0001f)
        val locked = horizontal.copy(positionLocked = true)
        assertEquals(locked.copy(gaugeOrientation = GaugeOrientation.VERTICAL), locked.withGaugeOrientation(GaugeOrientation.VERTICAL))
    }
}
