package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class WorkShiftInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val start = LocalDateTime.of(2026, 10, 26, 8, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = mutableLongStateOf(start + 7_200_000)
    private val generation = mutableIntStateOf(0)
    private val direction = mutableStateOf(GaugeOrientation.HORIZONTAL)
    private val gaugeColor = mutableStateOf<Long?>(null)
    private var cycles = 0

    private fun withStore(block: suspend (WorkShiftStore) -> Unit) = runBlocking {
        val file = File(compose.activity.cacheDir, "shift-ui-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = WorkShiftStore(compose.activity, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        try { block(store) } finally { compose.activityRule.scenario.close(); job.cancelAndJoin(); file.delete() }
    }

    private fun render(store: WorkShiftStore, rotated: Boolean = false) {
        compose.setContent { FoldTheme {
            val contents: @Composable () -> Unit = {
                key(generation.intValue) {
                    WorkShiftHost(false, store, now.longValue) { shift, busy, clockIn, editClockIn ->
                        val element = if (direction.value == GaugeOrientation.HORIZONTAL)
                            newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.12f, y = 0.57f, width = 0.76f, height = 0.25f)
                        else newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.4f, y = 0.08f, width = 0.2f, height = 0.84f, gaugeOrientation = GaugeOrientation.VERTICAL)
                        StandbyStage(StandbyScene(elements = listOf(element.copy(color = gaugeColor.value))), PowerState(percent = 80),
                            Instant.ofEpochMilli(now.longValue).atZone(zone).toLocalDateTime(), false, 0.5f, {}, {},
                            true, { cycles++ }, {}, {}, {}, NotificationFeedState(), Modifier.fillMaxSize(),
                            workShift = shift, workShiftBusy = busy, onClockIn = clockIn, onEditClockIn = editClockIn)
                    }
                }
            }
            if (rotated) Box(Modifier.size(260.dp, 440.dp)) {
                LandscapeFrame(FoldInfo(), DevicePosture.REVERSE_LANDSCAPE, 0) { contents() }
            } else Box(Modifier.fillMaxWidth().height(300.dp)) { contents() }
        } }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String, tag: String = "work_shift_host"): Bitmap {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "validation-0.13.0").apply { mkdirs() }
        File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }

    private fun assertDeadlineAboveRail(vertical: Boolean = false) {
        val caption = compose.onNodeWithTag("work_shift_deadline").fetchSemanticsNode().boundsInRoot
        val rail = compose.onNodeWithTag("work_shift_gauge").fetchSemanticsNode().boundsInRoot
        assertTrue("퇴근 시각은 막대 위에 배치되어야 합니다.", caption.bottom <= rail.top)
        if (vertical) assertTrue(caption.right >= rail.right)
        else assertEquals(rail.right, caption.right, 1f)
    }

    private fun correctStartThroughLongPress(vertical: Boolean, rotated: Boolean = false) = withStore { store ->
        store.clockIn(now.longValue, start, zone)
        direction.value = if (vertical) GaugeOrientation.VERTICAL else GaugeOrientation.HORIZONTAL
        render(store, rotated)
        awaitTag("work_shift_gauge")
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { click() }
        compose.onNodeWithTag("work_shift_time_picker").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { longClick() }
        compose.onNodeWithText("출근 시각 변경").assertExists()
        compose.onNodeWithText("08:00로 변경").assertExists()
        val original = store.state.first()
        compose.onNodeWithTag("cancel_shift_time").performClick()
        assertEquals(original, store.state.first())
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("9시간", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("09:00로 변경").assertExists()
        capture(if (vertical) "edit-start-vertical.png" else "edit-start-horizontal.png")
        compose.onNodeWithTag("confirm_shift_time").performTouchInput { click() }
        awaitTag("work_shift_gauge")
        val corrected = store.state.first()
        assertEquals(start + 3_600_000, corrected.startedAt)
        assertEquals(start + 3_600_000 + WorkShift.WORK_MILLIS, corrected.deadline)
        compose.onNodeWithText("11%").assertExists()
        compose.onNodeWithText("18:00").assertExists()
        assertEquals(0, cycles)
        capture(if (vertical) "corrected-vertical.png" else "corrected-horizontal.png")
        compose.runOnIdle { generation.intValue++ }
        awaitTag("work_shift_gauge")
        compose.onNodeWithTag("work_shift_component").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("09:00로 변경").assertExists()
        compose.onNodeWithTag("confirm_shift_time").performClick()
        awaitTag("work_shift_gauge")
        assertEquals(corrected, store.state.first())
        assertEquals(0, cycles)
    }

    @Test fun longPressCorrectsHorizontalGaugeAndNoChangeConfirmClosesPicker() = correctStartThroughLongPress(false)
    @Test fun longPressCorrectsVerticalGaugeInRotatedLandscape() = correctStartThroughLongPress(true, rotated = true)

    @Test fun editingAcrossOriginalDeadlineCanResumeWithoutOldFireworks() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue = start + WorkShift.WORK_MILLIS - 60_000
        render(store)
        awaitTag("work_shift_gauge")
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("9시간", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { now.longValue += 30 * 60_000 }
        compose.waitUntil(5000) { runBlocking { store.state.first().checkedOutAt > 0L } }
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
        compose.onNodeWithTag("confirm_shift_time").performClick()
        awaitTag("work_shift_gauge")
        assertEquals(WorkShiftPhase.WORKING, store.state.first().display(now.longValue).phase)
        assertEquals(0L, store.state.first().checkedOutAt)
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
    }

    @Test fun overnightEditStartsFromOriginalDateAndTime() = withStore { store ->
        val evening = start + 14 * 3_600_000
        store.clockIn(evening, evening, zone)
        now.longValue = evening + 4 * 3_600_000
        render(store)
        awaitTag("work_shift_gauge")
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { longClick() }
        compose.onNodeWithText("22:00로 변경").assertExists()
        compose.onNodeWithText("10월 26일 · 퇴근 07:00").assertExists()
        compose.onNodeWithTag("confirm_shift_time").performClick()
        awaitTag("work_shift_gauge")
        assertEquals(evening, store.state.first().startedAt)
        assertEquals(evening + WorkShift.WORK_MILLIS, store.state.first().deadline)
    }

    @Test fun actualDialSelectsEarlierStartAndCancelDoesNotStartShift() = withStore { store ->
        render(store)
        awaitTag("clock_in")
        capture("ready.png")
        capture("clock-in-button.png", "clock_in")
        compose.onNodeWithTag("clock_in").performTouchInput { click() }
        compose.onNodeWithTag("work_shift_time_picker").assertExists()
        compose.onNodeWithTag("cancel_shift_time").performClick()
        assertEquals(WorkShiftPhase.READY, store.state.first().display(now.longValue).phase)
        compose.onNodeWithTag("clock_in").performClick()
        capture("time-dial-before-selection.png")
        compose.onNodeWithContentDescription("8시간", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("08:00 출근").assertExists()
        capture("time-dial.png")
        compose.onNodeWithTag("confirm_shift_time").performClick()
        awaitTag("work_shift_gauge")
        assertEquals(start, store.state.first().startedAt)
        assertEquals(start + WorkShift.WORK_MILLIS, store.state.first().deadline)
        compose.onNodeWithText("22%").assertExists()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        assertEquals(0, cycles)
        capture("working-horizontal.png")
        capture("gauge-horizontal-detail.png", "work_shift_component")
        assertDeadlineAboveRail()
    }

    @Test fun completionFireworksCoverScreenOnceAndAutomaticallyHideGauge() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue = start + WorkShift.WORK_MILLIS - 1
        render(store)
        awaitTag("work_shift_gauge")
        compose.onNodeWithText("99%").assertExists()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { now.longValue++ }
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.advanceTimeBy(800)
        awaitTag("work_shift_fireworks")
        assertFalse(store.state.first().celebrated)
        assertEquals(compose.onNodeWithTag("work_shift_host").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("work_shift_fireworks").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        capture("complete-fireworks.png")
        compose.mainClock.advanceTimeBy(4500)
        compose.waitUntil(5000) { runBlocking { store.state.first().celebrated } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        assertEquals(start + WorkShift.WORK_MILLIS, store.state.first().checkedOutAt)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { generation.intValue++ }
        compose.waitForIdle()
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        compose.runOnIdle { now.longValue += WorkShift.REST_MILLIS - 1; generation.intValue++ }
        compose.waitForIdle()
        compose.onNodeWithTag("clock_in").assertDoesNotExist()
        compose.runOnIdle { now.longValue++ }
        awaitTag("clock_in")
        assertEquals(0, cycles)
    }

    @Test fun bothDirectionsFillCorrectlyAndGaugeGesturesDoNotChangePreset() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue = start + WorkShift.WORK_MILLIS / 2
        render(store)
        awaitTag("work_shift_gauge")
        val maxThickness = compose.activity.resources.displayMetrics.density * 24 + 1
        assertTrue(compose.onNodeWithTag("work_shift_gauge").fetchSemanticsNode().boundsInRoot.height <= maxThickness)
        val horizontal = capture("half-horizontal.png", "work_shift_gauge")
        fun green(image: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Double {
            var total = 0L; var count = 0
            for (y in (image.height * top).toInt() until (image.height * bottom).toInt())
                for (x in (image.width * left).toInt() until (image.width * right).toInt()) {
                    total += PixelColor.green(image.getPixel(x, y)); count++
                }
            return total.toDouble() / count
        }
        assertTrue(green(horizontal, 0.15f, 0.3f, 0.35f, 0.7f) > green(horizontal, 0.65f, 0.3f, 0.85f, 0.7f) + 40)
        compose.onNodeWithTag("work_shift_gauge").performTouchInput { swipe(center, center + Offset(0f, -150f), 350) }
        assertEquals(0, cycles)
        compose.runOnIdle { now.longValue = start + WorkShift.WORK_MILLIS * 58 / 100 }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("58%").assertExists()
        capture("reference-58-horizontal.png", "work_shift_component")
        compose.runOnIdle { gaugeColor.value = 0xFFFF9F43 }
        val colored = capture("custom-color-track.png", "work_shift_gauge")
        val band = (colored.width / 6 until colored.width / 3).map { colored.getPixel(it, colored.height / 2) }
        assertTrue("선택한 주황색이 실제 막대에 적용되어야 합니다.",
            band.map { PixelColor.red(it) }.average() > band.map { PixelColor.blue(it) }.average() + 30)
        capture("custom-color-gauge.png", "work_shift_component")
        compose.runOnIdle { gaugeColor.value = null }
        compose.runOnIdle { now.longValue = start + WorkShift.WORK_MILLIS / 2 }
        compose.runOnIdle { direction.value = GaugeOrientation.VERTICAL }
        assertTrue(compose.onNodeWithTag("work_shift_gauge").fetchSemanticsNode().boundsInRoot.width <= maxThickness)
        val vertical = capture("half-vertical.png", "work_shift_gauge")
        assertTrue(green(vertical, 0.3f, 0.65f, 0.7f, 0.85f) > green(vertical, 0.3f, 0.15f, 0.7f, 0.35f) + 40)
        compose.onNodeWithTag("work_shift_gauge").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "세로 게이지 50%"))
        capture("working-vertical.png")
        capture("gauge-vertical-detail.png", "work_shift_component")
        assertDeadlineAboveRail(vertical = true)
        compose.runOnIdle { now.longValue = start + WorkShift.WORK_MILLIS }
        compose.waitUntil(5000) { runBlocking { store.state.first().display(now.longValue).phase == WorkShiftPhase.COOLDOWN } }
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        compose.onNodeWithTag("clock_in").assertDoesNotExist()
    }

    @Test fun rotatedLandscapeKeepsDialAndConfirmTouchable() = withStore { store ->
        render(store, rotated = true)
        awaitTag("clock_in")
        compose.onNodeWithTag("clock_in").performTouchInput { click() }
        compose.onNodeWithTag("work_shift_time_picker").assertExists()
        capture("rotated-time-dial.png")
        compose.onNodeWithTag("confirm_shift_time").performTouchInput { click() }
        awaitTag("work_shift_gauge")
        assertEquals(now.longValue, store.state.first().startedAt)
    }
}
