package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class NotificationPanelInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val feed = mutableStateOf(NotificationFeedState(true, true, (0..19).map { index ->
        NotificationEntry("sample-$index", "sample.app", listOf("일정", "메시지", "음악")[index % 3],
            "검사 알림 ${index + 1}", "유리 패널과 목록 스크롤을 확인하는 예시 알림입니다.", 1792976400000L - index * 60000L)
    }))
    private var cycles = 0
    private var brightness = mutableFloatStateOf(0.5f)
    private val scene = StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PRESET, preset = BackdropPreset.OCEAN),
        elements = listOf(newElement("clock", ElementKind.CLOCK), newElement("brightness", ElementKind.BRIGHTNESS).copy(x = 0.08f, y = 0.74f, width = 0.42f, height = 0.18f)))

    private fun render(scene: StandbyScene = this.scene, canCycle: Boolean = true, stageHeight: Dp = 360.dp) {
        compose.setContent { FoldTheme { Box(Modifier.fillMaxWidth().height(stageHeight)) {
            StandbyScreen(FoldInfo(), PowerState(percent = 80), false, "test", true, emptyList(), {}, {}, scene,
                brightness = brightness.floatValue, onBrightness = { brightness.floatValue = it }, canCycle = canCycle,
                onNextPreset = { cycles++ }, notificationFeed = feed.value,
                onNotificationDismiss = { id -> feed.value = feed.value.copy(items = feed.value.items.filterNot { it.key == id }) },
                onNotificationsDismiss = { ids -> feed.value = feed.value.copy(items = feed.value.items.filterNot { it.key in ids }) })
        } } }
    }
    private fun openPanel() {
        compose.onNodeWithTag("standby_stage").performTouchInput { swipe(Offset(width * 0.92f, height * 0.4f), Offset(width * 0.55f, height * 0.4f), 350) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("notification_panel").assertExists()
    }
    private fun capture(name: String) {
        val dir = File(compose.activity.filesDir, "validation-0.6.0").apply { mkdirs() }
        File(dir, name).outputStream().use { compose.onNodeWithTag("standby_stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun verticalCycleBrightnessPullScrollAndDismissStaySeparate() {
        render()
        compose.onNodeWithTag("scene_canvas").performTouchInput { swipe(Offset(width * 0.1f, height * 0.65f), Offset(width * 0.1f, height * 0.1f), 350) }
        compose.runOnIdle { assertEquals(1, cycles) }
        compose.onNodeWithTag("scene_canvas").performTouchInput { swipe(Offset(width * 0.1f, height * 0.1f), Offset(width * 0.1f, height * 0.65f), 350) }
        compose.runOnIdle { assertEquals(2, cycles) }
        compose.onNodeWithTag("brightness_slider").performTouchInput { swipe(Offset(width * 0.2f, height / 2f), Offset(width * 0.8f, height / 2f), 350) }
        compose.runOnIdle { assertTrue(brightness.floatValue > 0.8f); assertEquals(2, cycles) }
        compose.onNodeWithTag("notification_panel").assertDoesNotExist()

        compose.onNodeWithTag("standby_stage").performTouchInput {
            down(Offset(width * 0.92f, height * 0.4f)); moveTo(Offset(width * 0.74f, height * 0.4f), 250)
        }
        compose.mainClock.advanceTimeBy(32)
        val pulling = compose.onNodeWithTag("standby_stage").fetchSemanticsNode().config[NotificationPanelProgress]
        assertTrue("손가락 이동 중 패널이 중간 위치를 따라옴", pulling > 0f && pulling < 1f)
        compose.onNodeWithTag("standby_stage").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        val stage = compose.onNodeWithTag("standby_stage").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("notification_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(0.5f, (panel.left - stage.left) / stage.width, 0.02f)
        assertTrue(panel.right < stage.right && panel.top > stage.top && panel.bottom < stage.bottom)
        capture("notification-panel.png")
        compose.onNodeWithTag("notification_group_sample.app").performClick()
        compose.onNodeWithTag("notification_list").performTouchInput { swipeUp() }
        val scroll = compose.onNodeWithTag("notification_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("목록이 실제로 세로 스크롤됨", scroll.value() > 0f)
        compose.runOnIdle { assertEquals(2, cycles) }
        compose.onNodeWithTag("notification_list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(2, cycles) }
        compose.onNodeWithTag("notification_panel").performTouchInput { swipe(Offset(width * 0.2f, height * 0.25f), Offset(width * 0.88f, height * 0.25f), 350) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("notification_panel").assertDoesNotExist()
        openPanel()
        compose.onNodeWithTag("notification_backdrop").performTouchInput { click(Offset(width * 0.15f, height * 0.4f)) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("notification_panel").assertDoesNotExist()
        openPanel(); compose.onNodeWithTag("close_notifications").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("notification_panel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, cycles) }
    }

    @Test fun permissionEmptyArrivalRemovalAndRevocationUpdateAnOpenPanel() {
        feed.value = NotificationFeedState()
        render(canCycle = false); openPanel()
        compose.onNodeWithText("알림 접근을 켜 주세요").assertExists()
        compose.runOnIdle { feed.value = NotificationFeedState(permissionGranted = true) }
        compose.onNodeWithText("알림 연결 중").assertExists()
        compose.runOnIdle { feed.value = NotificationFeedState(true, true) }
        compose.onNodeWithText("새 알림이 없습니다").assertExists()
        compose.runOnIdle { feed.value = NotificationFeedState(true, true, listOf(NotificationEntry("arrived", "sample.app", "검사", "도착 확인", "내용", 1L))) }
        compose.onNodeWithText("도착 확인").assertDoesNotExist()
        compose.onNodeWithTag("notification_group_sample.app").performClick()
        compose.onNodeWithText("도착 확인").assertExists()
        compose.runOnIdle { feed.value = NotificationFeedState(true, true) }
        compose.onNodeWithText("도착 확인").assertDoesNotExist()
        compose.onNodeWithText("새 알림이 없습니다").assertExists()
        compose.runOnIdle { feed.value = NotificationFeedState() }
        compose.onNodeWithText("알림 접근을 켜 주세요").assertExists()
        compose.onNodeWithTag("notification_list").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, cycles) }
    }

    @Test fun glassBlursOnlyTheFloatingPanelArea() {
        val media = File(compose.activity.filesDir, "scene_media/${UUID.randomUUID()}.img").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val shade = if (x / 8 % 2 == 0) 40 else 200
            bitmap.setPixel(x, y, PixelColor.rgb(shade, shade, shade))
        }
        media.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        fun image() = compose.onNodeWithTag("standby_stage").captureToImage().asAndroidBitmap()
        fun range(bitmap: Bitmap, from: Float, to: Float): Int {
            val values = ((bitmap.width * from).toInt()..(bitmap.width * to).toInt()).map { PixelColor.red(bitmap.getPixel(it, (bitmap.height * 0.28f).toInt())) }
            return values.max() - values.min()
        }
        try {
            feed.value = NotificationFeedState(true, true)
            render(StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PHOTO, media = media.name, fit = PhotoFit.STRETCH, dim = 0f), elements = emptyList()))
            compose.waitUntil(5000) { range(image(), 0.1f, 0.3f) > 120 }
            openPanel()
            val rendered = image()
            assertTrue(range(rendered, 0.1f, 0.3f) > 100)
            assertTrue(range(rendered, 0.65f, 0.85f) < 35)
            assertTrue(PixelColor.red(rendered.getPixel((rendered.width * 0.7f).toInt(), (rendered.height * 0.28f).toInt())) in 50..130)
            capture("notification-panel-blur.png")
        } finally { compose.activityRule.scenario.close(); media.delete() }
    }

    @Test fun appGroupsExpandNewestFirstAndLeftSwipeRemovesOnlyOneRecord() {
        feed.value = NotificationFeedState(true, true, listOf(
            NotificationEntry("a-old", "a", "메시지", "이전 메시지", "보관한 내용", 100),
            NotificationEntry("b-one", "b", "일정", "일정 알림", "일정 내용", 200),
            NotificationEntry("a-new", "a", "메시지", "최신 메시지", "새로 도착한 내용", 300)))
        render(); openPanel()
        compose.onNodeWithText("최신 메시지").assertDoesNotExist()
        compose.onNodeWithText("새로 도착한 내용").assertDoesNotExist()
        compose.onNodeWithText("이전 메시지").assertDoesNotExist()
        val latest = compose.onNodeWithTag("notification_group_a").fetchSemanticsNode().boundsInRoot
        val other = compose.onNodeWithTag("notification_group_b").fetchSemanticsNode().boundsInRoot
        assertTrue(latest.top < other.top)
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.onNodeWithTag("notification_a-new").performScrollTo()
        compose.onNodeWithTag("notification_a-new").performTouchInput {
            swipe(Offset(width * 0.85f, height * 0.5f), Offset(width * 0.72f, height * 0.5f), 300)
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertEquals(3, feed.value.items.size) }
        compose.onNodeWithTag("notification_a-new").performTouchInput {
            swipe(Offset(width * 0.9f, height * 0.5f), Offset(width * 0.1f, height * 0.5f), 350)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitUntil(5000) { feed.value.items.none { it.key == "a-new" } }
        compose.onNodeWithTag("notification_panel").assertExists()
        compose.runOnIdle { assertEquals(setOf("a-old", "b-one"), feed.value.items.map { it.key }.toSet()); assertEquals(0, cycles) }
        compose.onNodeWithTag("notification_a-old").performScrollTo()
        compose.onNodeWithTag("notification_a-old").performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.5f), Offset(width * 0.9f, height * 0.5f), 350)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("notification_panel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, feed.value.items.size) }
        openPanel()
        compose.onNodeWithTag("notification_group_a").performClick()
        val dir = File(compose.activity.filesDir, "validation-0.8.0").apply { mkdirs() }
        File(dir, "grouped-history.png").outputStream().use { compose.onNodeWithTag("standby_stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun bulkClearKeepsOtherAppsAndAnOpenPanelCanReceiveAgainAfterClearingAll() {
        feed.value = NotificationFeedState(true, true, listOf(
            NotificationEntry("a-old", "a", "메시지", "이전 메시지", "보관한 내용", 100),
            NotificationEntry("b-one", "b", "일정", "일정 알림", "일정 내용", 200),
            NotificationEntry("a-new", "a", "메시지", "최신 메시지", "새로 도착한 내용", 300)))
        render(); openPanel()
        compose.onNodeWithTag("clear_group_a").performClick()
        compose.runOnIdle { assertEquals(listOf("b-one"), feed.value.items.map { it.key }); assertEquals(0, cycles) }
        compose.onNodeWithTag("notification_group_a").assertDoesNotExist()
        compose.onNodeWithTag("notification_group_b").assertExists()
        compose.onNodeWithTag("clear_all_notifications").performClick()
        compose.onNodeWithText("새 알림이 없습니다").assertExists()
        compose.onNodeWithTag("notification_panel").assertExists()
        compose.runOnIdle { assertTrue(feed.value.items.isEmpty()); assertEquals(0, cycles) }
        compose.runOnIdle { feed.value = feed.value.copy(items = listOf(NotificationEntry("new", "a", "메시지", "지운 뒤 도착", "새 내용", 400))) }
        compose.onNodeWithText("지운 뒤 도착").assertDoesNotExist()
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.onNodeWithText("지운 뒤 도착").assertExists()
        compose.onNodeWithTag("clear_group_a").performClick()
        compose.onNodeWithText("새 알림이 없습니다").assertExists()
    }

    @Test fun collapsedGroupsHideEveryMessageAndExpandBelowTheFixedAppHeader() {
        feed.value = NotificationFeedState(true, true, listOf(
            NotificationEntry("a-old", "a", "메시지", "이전 메시지", "보관한 내용", 100),
            NotificationEntry("b-one", "b", "일정", "일정 알림", "내용", 200),
            NotificationEntry("a-new", "a", "메시지", "최신 메시지", "새로 도착한 내용", 300)))
        render(stageHeight = 640.dp); openPanel()
        fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        fun height(tag: String) = bounds(tag).let { (it.bottom - it.top).value }
        fun captureFrame(name: String) {
            val dir = File(compose.activity.filesDir, "validation-0.8.3").apply { mkdirs() }
            File(dir, name).outputStream().use { compose.onNodeWithTag("standby_stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val headerTop = bounds("notification_group_a").top.value
        compose.onNodeWithText("최신 메시지").assertDoesNotExist()
        compose.onNodeWithText("이전 메시지").assertDoesNotExist()
        compose.onNodeWithText("새로 도착한 내용").assertDoesNotExist()
        val nextTop = bounds("notification_group_b").top.value
        val closedHeight = height("notification_group_block_a")
        captureFrame("00-collapsed.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.mainClock.advanceTimeBy(96)
        val openingHeight = height("notification_group_block_a")
        val openingNext = bounds("notification_group_b").top.value
        assertEquals("앱 이름이 제자리에 남음", headerTop, bounds("notification_group_a").top.value, 0.5f)
        captureFrame("01-opening.png")
        compose.mainClock.advanceTimeBy(400)
        val openedHeight = height("notification_group_block_a")
        assertTrue("이전 기록 영역이 아래로 늘어남", openingHeight > closedHeight && openingHeight < openedHeight)
        assertTrue("아래 앱이 열린 높이만큼 밀려남", openingNext > nextTop && openingNext < bounds("notification_group_b").top.value)
        assertEquals(headerTop, bounds("notification_group_a").top.value, 0.5f)
        captureFrame("02-expanded.png")
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closingHeight = height("notification_group_block_a")
        assertTrue("접힐 때 기록 영역이 위로 줄어듦", closingHeight > closedHeight && closingHeight < openedHeight)
        assertEquals(headerTop, bounds("notification_group_a").top.value, 0.5f)
        captureFrame("03-closing.png")
        // 도중에 다시 누르면 현재 높이에서 자연스럽게 반대로 이어진다.
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.mainClock.advanceTimeBy(32)
        val reversedHeight = height("notification_group_block_a")
        assertTrue("방향 전환은 중간 높이에서 이어짐: 접힘=$closedHeight, 전환 전=$closingHeight, 전환 후=$reversedHeight, 펼침=$openedHeight",
            reversedHeight > closedHeight && reversedHeight < openedHeight && kotlin.math.abs(reversedHeight - closingHeight) < (openedHeight - closedHeight) * 0.25f)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(openedHeight, height("notification_group_block_a"), 0.5f)
        compose.onNodeWithTag("notification_group_a").performClick()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(closedHeight, height("notification_group_block_a"), 0.5f)
        assertEquals(nextTop, bounds("notification_group_b").top.value, 0.5f)
        compose.onNodeWithTag("notification_a-old").assertDoesNotExist()
        compose.onNodeWithTag("notification_a-new").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun fiveHundredRecordsRevealWithinOneViewportAndTheOldestStillScrollsIntoView() {
        feed.value = NotificationFeedState(true, true, (1..500).map {
            NotificationEntry("many-$it", "many", "메시지", "기록 $it", "누적 알림 내용", it.toLong())
        })
        render(); openPanel()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("notification_group_many").performClick()
        compose.mainClock.advanceTimeBy(64)
        val revealing = compose.onNodeWithTag("notification_history_many").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val viewport = compose.onNodeWithTag("notification_list").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        assertTrue("긴 기록도 첫 프레임에 전체 화면을 덮지 않음", revealing > 0f && revealing < viewport)
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("notification_many-1").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, cycles) }
        compose.onNodeWithTag("notification_group_many").performScrollTo().performClick()
        compose.onNodeWithTag("notification_many-1").assertDoesNotExist()
        compose.onNodeWithTag("notification_many-500").assertDoesNotExist()
        compose.onNodeWithTag("notification_group_many").assertIsDisplayed()
    }
}
