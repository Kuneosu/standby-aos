package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.Lifecycle
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
import kotlin.math.abs

class WorkShiftRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val start = LocalDateTime.of(2026, 10, 27, 8, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = mutableLongStateOf(start + WorkShift.WORK_MILLIS)
    private val generation = mutableIntStateOf(0)
    private val direction = mutableStateOf(GaugeOrientation.HORIZONTAL)
    private val backdrop = 0xFFD0B0FF
    private var exits = 0

    private fun withStore(completesSoon: Boolean = false, block: suspend (WorkShiftStore) -> Unit) = runBlocking {
        val file = File(compose.activity.cacheDir, "shift-regression-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        val store = WorkShiftStore(compose.activity, preferences)
        if (completesSoon) preferences.edit {
            // 기기 시계는 변경하지 않고 검사 앱의 근무 마감만 잠시 뒤로 설정한다.
            val deadline = System.currentTimeMillis() + 2500
            it[longPreferencesKey("started_at")] = deadline - WorkShift.WORK_MILLIS
            it[longPreferencesKey("deadline")] = deadline
        }
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        try { block(store) } finally { compose.activityRule.scenario.close(); job.cancelAndJoin(); file.delete() }
    }

    private fun render(store: WorkShiftStore, liveClock: Boolean = false) {
        compose.setContent { FoldTheme {
            Box(Modifier.fillMaxWidth().height(352.dp)) {
                key(generation.intValue) {
                    WorkShiftHost(false, store, if (liveClock) null else now.longValue) { shift, busy, clockIn, editClockIn ->
                        val gauge = if (direction.value == GaugeOrientation.HORIZONTAL)
                            newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.02409345f, y = 0.6696673f,
                                width = 0.49036068f, height = 0.3303327f, color = backdrop)
                        else newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.05f, y = 0.1f,
                            width = 0.2f, height = 0.85f, color = backdrop, gaugeOrientation = GaugeOrientation.VERTICAL)
                        val scene = StandbyScene(textColor = 0xFF101010, background = BackgroundSpec(color = backdrop),
                            elements = listOf(newElement("clock", ElementKind.CLOCK).copy(x = 0.4f, y = 0.08f, width = 0.55f, height = 0.3f),
                                newElement("date", ElementKind.DATE).copy(x = 0.4f, y = 0.4f, width = 0.55f),
                                newElement("battery", ElementKind.BATTERY).copy(x = 0.6f, y = 0.6f, width = 0.3f), gauge))
                        StandbyStage(scene, PowerState(percent = 86), Instant.ofEpochMilli(now.longValue).atZone(zone).toLocalDateTime(),
                            false, 0.5f, {}, {}, false, {}, {}, {}, { exits++ }, NotificationFeedState(), Modifier.fillMaxSize(),
                            workShift = shift, workShiftBusy = busy, onClockIn = clockIn, onEditClockIn = editClockIn)
                    }
                }
            }
        } }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(5000) {
        // Activity 재개 후 비동기 창 포커스 변경도 다음 Compose 프레임에 반영한다.
        if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String, tag: String = "work_shift_host"): Bitmap {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "validation-0.11.0").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun clockInRemainsVisibleWhenGaugeColorMatchesLightBackground() = withStore { store ->
        render(store)
        awaitTag("clock_in")
        capture("ready-same-color-background.png")
        val button = capture("clock-in-contrast.png", "clock_in")
        var contrasting = 0
        for (y in 0 until button.height) for (x in 0 until button.width) {
            val pixel = button.getPixel(x, y)
            val distance = abs(PixelColor.red(pixel) - 208) + abs(PixelColor.green(pixel) - 176) + abs(PixelColor.blue(pixel) - 255)
            if (distance > 120) contrasting++
        }
        assertTrue("출근 버튼은 같은 색의 밝은 배경에서도 구분되어야 합니다.",
            contrasting.toFloat() / (button.width * button.height) > 0.25f)
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("clock_in").assertIsDisplayed().assertHasClickAction().performTouchInput { click() }
        compose.onNodeWithTag("work_shift_time_picker").assertIsDisplayed()
    }

    @Test fun interruptedFireworksReplayUntilAnimationFinishesThenStayConsumed() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue--
        render(store)
        awaitTag("work_shift_gauge")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { now.longValue++ }
        compose.mainClock.advanceTimeBy(800)
        awaitTag("work_shift_fireworks")
        val firstFrame = capture("fireworks-before-recreation.png")
        assertEquals(compose.onNodeWithTag("work_shift_host").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("work_shift_fireworks").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(700)
        val nextFrame = capture("fireworks-next-frame.png")
        var changed = 0
        for (y in 0 until firstFrame.height) for (x in 0 until firstFrame.width) {
            if (firstFrame.getPixel(x, y) != nextFrame.getPixel(x, y)) changed++
        }
        assertTrue("실제 폭죽 프레임의 픽셀이 시간에 따라 달라져야 합니다.", changed > 100)
        assertFalse("화면에서 재생 중인 폭죽을 미리 완료 처리하면 안 됩니다.", store.state.first().celebrated)
        compose.runOnIdle { generation.intValue++ }
        compose.mainClock.advanceTimeBy(32)
        awaitTag("work_shift_gauge")
        compose.mainClock.advanceTimeBy(800)
        awaitTag("work_shift_fireworks")
        capture("fireworks-after-recreation.png")
        compose.mainClock.advanceTimeBy(4500)
        compose.waitUntil(5000) { runBlocking { store.state.first().celebrated } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { generation.intValue++ }
        compose.waitForIdle()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
    }

    @Test fun pausedCompletionWaitsForVisibleWindowBeforePlaying() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue--
        render(store)
        awaitTag("work_shift_gauge")
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        now.longValue++
        assertFalse(store.state.first().celebrated)
        compose.mainClock.autoAdvance = false
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(800)
        awaitTag("work_shift_fireworks")
        assertFalse(store.state.first().celebrated)
        compose.mainClock.advanceTimeBy(4500)
        compose.waitUntil(5000) { runBlocking { store.state.first().celebrated } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        compose.onNodeWithTag("element_clock").assertIsDisplayed()
    }

    @Test fun realClockAutomaticallyChecksOutAndKeepsSceneInVerticalDirection() = withStore(completesSoon = true) { store ->
        direction.value = GaugeOrientation.VERTICAL
        render(store, liveClock = true)
        awaitTag("work_shift_gauge")
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.waitUntil(7000) { runBlocking { store.state.first().checkedOutAt > 0 } }
        assertEquals(store.state.first().deadline, store.state.first().checkedOutAt)
        compose.waitUntil(7000) { runBlocking { store.state.first().celebrated } }
        compose.waitForIdle()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
        compose.onNodeWithTag("element_clock").assertIsDisplayed()
        assertEquals(0, exits)
    }

    @Test fun reopeningAfterTwelveHoursImmediatelyShowsClockInWithoutOldFireworks() = withStore { store ->
        store.clockIn(start, start, zone)
        now.longValue = start + WorkShift.WORK_MILLIS + WorkShift.REST_MILLIS
        render(store)
        awaitTag("clock_in")
        compose.onNodeWithTag("work_shift_fireworks").assertDoesNotExist()
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        compose.onNodeWithTag("element_clock").assertIsDisplayed()
        assertEquals(start + WorkShift.WORK_MILLIS, store.state.first().checkedOutAt)
        assertEquals(0, exits)
    }

    @Test fun autoCheckoutHidesOnlyGaugeAndPreservesClockBackgroundAndBatteryAcrossReentry() = withStore { store ->
        store.clockIn(start, start, zone)
        store.claimCelebration(now.longValue)
        render(store)
        awaitTag("scene_canvas")
        compose.onNodeWithTag("clock_out").assertDoesNotExist()
        fun assertSceneRemains() {
            compose.onNodeWithTag("work_shift_component").assertDoesNotExist()
            compose.onNodeWithTag("scene_canvas").assertIsDisplayed()
            listOf("clock", "date", "battery").forEach { compose.onNodeWithTag("element_$it").assertIsDisplayed() }
            compose.onNodeWithText("86%").assertIsDisplayed()
            val pixel = capture("cooldown-screen.png").getPixel(0, 0)
            assertEquals(backdrop.toInt(), pixel)
            assertEquals(0, exits)
        }
        assertSceneRemains()
        compose.runOnIdle { generation.intValue++; now.longValue += WorkShift.REST_MILLIS - 1 }
        compose.waitForIdle()
        assertSceneRemains()
        compose.onNodeWithTag("clock_in").assertDoesNotExist()
        compose.runOnIdle { now.longValue++ }
        awaitTag("clock_in")
        compose.onNodeWithTag("scene_canvas").assertIsDisplayed()
        assertEquals(0, exits)
    }
}
