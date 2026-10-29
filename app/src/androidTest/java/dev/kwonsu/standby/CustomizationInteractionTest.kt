package dev.kwonsu.standby

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
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
import java.time.LocalDateTime
import java.util.UUID

class CustomizationInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun brightnessConsumesDragFadesAndSwipesOnlyRegisteredPresets() = runBlocking {
        val file = File(compose.activity.cacheDir, "playback-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val settings = SettingsStore(compose.activity, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        val brightness = newElement("brightness", ElementKind.BRIGHTNESS).copy(x = 0.2f, y = 0.65f, width = 0.6f, height = 0.2f)
        val first = StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PRESET), elements = listOf(newElement("first-clock", ElementKind.CLOCK), brightness))
        val second = first.copy(elements = listOf(newElement("second-clock", ElementKind.CLOCK), brightness))
        val entry = mutableStateOf("first-entry")
        try {
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("a", "첫 화면", first), ScenePreset("excluded", "제외", StandbyScene()), ScenePreset("b", "두 번째", second)), listOf("a", "b"), true) }
            compose.setContent {
                FoldTheme {
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        StandbyPresentation(settings, entry.value, FoldInfo(), PowerState(plugged = 1), false, "test", true,
                            emptyList(), 0.5f, {}, {}, { value -> compose.activity.window.attributes = compose.activity.window.attributes.apply { screenBrightness = value } })
                    }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("element_first-clock").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("brightness_slider").assert(SemanticsMatcher.expectValue(GlassOpacity, 0.16f))
            compose.onNodeWithTag("brightness_slider").performTouchInput { swipe(Offset(width * 0.25f, height / 2f), Offset(width * 0.72f, height / 2f), 400) }
            compose.waitUntil(5000) { runBlocking { (settings.brightness.first().day ?: 0f) > 0.6f } }
            compose.onNodeWithTag("element_first-clock").assertExists()
            compose.runOnIdle { assertTrue(compose.activity.window.attributes.screenBrightness > 0.6f) }
            compose.onNodeWithTag("brightness_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithTag("brightness_slider").assert(SemanticsMatcher.expectValue(GlassOpacity, 1f))
            capture("brightness-active.png")
            compose.mainClock.advanceTimeBy(2000)
            compose.onNodeWithTag("brightness_slider").assert(SemanticsMatcher.expectValue(GlassOpacity, 0.16f))
            capture("brightness-idle.png")
            compose.onNodeWithTag("scene_canvas").performTouchInput { swipe(Offset(width * 0.9f, height * 0.85f), Offset(width * 0.9f, height * 0.12f), 400) }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("element_second-clock").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("b", settings.selectForEntry("first-entry").presetId)
            compose.runOnIdle { entry.value = "new-entry" }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("element_first-clock").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("a", settings.selectForEntry("new-entry").presetId)
        } finally { compose.activityRule.scenario.close(); job.cancelAndJoin(); file.delete() }
    }

    @Test fun positionLockBlocksDragAndResizeUntilUnlocked() {
        val state = mutableStateOf(EditorState(loading = false))
        compose.setContent { FoldTheme { SceneEditor(state.value, { edit -> state.value = state.value.copy(scene = edit(state.value.scene)) }, { _, _ -> }, {}, {}, {}, {}) } }
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("position_locked").performScrollTo().performClick()
        val before = state.value.scene.elements.first()
        assertTrue(before.positionLocked)
        compose.onNodeWithTag("element_clock").performTouchInput { swipe(center, center + Offset(65f, 25f), 400) }
        compose.runOnIdle { assertEquals(before, state.value.scene.elements.first()) }
        compose.onNodeWithTag("resize_clock").assertDoesNotExist()
        compose.onNodeWithTag("slider_가로 위치").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("position_locked").performScrollTo().performClick()
        compose.onNodeWithTag("element_clock").performTouchInput { swipe(center, center + Offset(65f, 25f), 400) }
        compose.runOnIdle { assertNotEquals(before.x, state.value.scene.elements.first().x) }
    }

    @Test fun draggingShowsAndSnapsToAnotherComponentsCenter() {
        val scene = mutableStateOf(StandbyScene(elements = listOf(
            SceneElement("moving", ElementKind.CLOCK, 0.1f, 0.15f, 0.2f, 0.2f),
            SceneElement("target", ElementKind.BATTERY, 0.6f, 0.55f, 0.2f, 0.2f))))
        compose.setContent { FoldTheme { SceneCanvas(scene.value, PowerState(percent = 80), LocalDateTime.now(),
            Modifier.fillMaxWidth().height(240.dp), editing = true, selectedId = "moving",
            onChange = { e -> scene.value = scene.value.copy(elements = scene.value.elements.map { if (it.id == e.id) e else it }) }) } }
        compose.onNodeWithTag("scene_canvas").performTouchInput {
            down(Offset(width * 0.2f, height * 0.25f)); moveTo(Offset(width * 0.7f, height * 0.25f), 400)
        }
        // 기기별 터치 슬롭 이후의 실제 요소 중심을 기준으로 가이드에 접근한다.
        var remaining = 0f
        compose.runOnIdle { remaining = 0.7f - scene.value.elements.first().let { it.x + it.width / 2 } }
        compose.onNodeWithTag("scene_canvas").performTouchInput { moveTo(Offset(width * (0.7f + remaining), height * 0.25f), 100) }
        compose.runOnIdle { assertEquals(0.7f, scene.value.elements.first().let { it.x + it.width / 2 }, 0.015f) }
        compose.onNodeWithTag("alignment_guides").assertExists()
        capture("alignment-guide.png")
        compose.onNodeWithTag("scene_canvas").performTouchInput { up() }
        compose.onNodeWithTag("alignment_guides").assertDoesNotExist()
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.filesDir, "validation-0.4.0").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
