package dev.kwonsu.standby

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SceneEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(EditorState(loading = false))
    private var saved: StandbyScene? = null

    @Test fun landscapeFrameHandlesEmptyLoadingThenSavedScene() {
        val loaded = mutableStateOf(false)
        compose.setContent {
            FoldTheme {
                Box(Modifier.size(160.dp, 320.dp)) {
                    LandscapeFrame(FoldInfo(), DevicePosture.REVERSE_LANDSCAPE, 0) { fold ->
                        if (loaded.value) StandbyScreen(fold, PowerState(plugged = 1), false, "test", true,
                            emptyList(), {}, {}, StandbyScene())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { loaded.value = true }
        compose.onNodeWithTag("scene_canvas").assertExists()
        compose.runOnIdle { loaded.value = false }
        compose.waitForIdle()
    }

    private fun render(scene: StandbyScene = StandbyScene()) {
        state.value = EditorState(scene, scene, loading = false)
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.setContent {
            FoldTheme {
                SceneEditor(state.value, { update -> state.value = state.value.copy(scene = update(state.value.scene).normalized()) }, { _, _ -> },
                    { saved = state.value.scene; state.value = state.value.copy(applied = state.value.scene) }, {}, {}, {})
            }
        }
    }

    @Test fun allThreeFontsAndCustomColorCanBeSaved() {
        render()
        ClockFont.entries.filter { it != ClockFont.SYSTEM }.forEach { font ->
            compose.onNodeWithTag("font_${font.name}").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(font, state.value.scene.font) }
        }
        compose.onNodeWithTag("hex_기본 글자색").performScrollTo().performTextReplacement("FFBB33")
        compose.onNodeWithText("적용").performScrollTo().performClick()
        compose.onNodeWithTag("save_scene").performClick()
        compose.runOnIdle { assertEquals(0xFFFFBB33, saved!!.textColor); assertEquals(ClockFont.KKUKKUKK, saved!!.font) }
        capture("font-color-preview.png")
    }

    @Test fun draggingAndCornerResizeChangeElementAndRemainInBounds() {
        render()
        val before = state.value.scene.elements.first()
        compose.onNodeWithTag("element_clock").performTouchInput { swipe(center, center + Offset(30f, 12f), 500) }
        compose.runOnIdle {
            val after = state.value.scene.elements.first()
            assertTrue("드래그하면 가로 위치가 증가해야 합니다.", after.x > before.x)
        }
        val oldWidth = state.value.scene.elements.first().width
        compose.onNodeWithTag("resize_clock").performTouchInput { swipe(center, center + Offset(30f, 10f), 500) }
        compose.runOnIdle {
            val after = state.value.scene.elements.first()
            assertTrue(after.width > oldWidth)
            assertTrue(after.x + after.width <= 1.0001f)
            assertTrue(after.y + after.height <= 1.0001f)
        }
        capture("drag-resize-preview.png")
    }

    @Test fun gifSpeedAndLoopControlsAreIndependentForEachElement() {
        val one = newElement("motion-one", ElementKind.GIF)
        val two = newElement("motion-two", ElementKind.GIF)
        render(StandbyScene(elements = listOf(one, two)))
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("select_motion-one").performClick()
        compose.onNodeWithTag("slider_재생 속도").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.onNodeWithTag("gif_loop").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2f, state.value.scene.elements[0].speed)
            assertFalse(state.value.scene.elements[0].loop)
            assertEquals(1f, state.value.scene.elements[1].speed)
            assertTrue(state.value.scene.elements[1].loop)
        }
    }

    @Test fun buttonsCanBeRemovedAddedAndReordered() {
        render()
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("select_close").performScrollTo().performClick()
        compose.onNodeWithTag("remove_element").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(state.value.scene.elements.any { it.kind == ElementKind.CLOSE_BUTTON }) }
        compose.onNodeWithTag("add_CLOSE_BUTTON").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ElementKind.CLOSE_BUTTON, state.value.scene.elements.last().kind) }
        compose.onNodeWithText("뒤로", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ElementKind.CLOSE_BUTTON, state.value.scene.elements[state.value.scene.elements.lastIndex - 1].kind) }
    }

    @Test fun movingAnotherButtonKeepsEarlierMoveAndSliderEdit() {
        render()
        val initial = state.value.scene.elements.first { it.id == "night" }
        compose.onNodeWithTag("element_night").performTouchInput { swipe(center, center + Offset(-60f, -30f), 500) }
        var moved = initial
        compose.runOnIdle {
            moved = state.value.scene.elements.first { it.id == "night" }
            assertTrue("첫 버튼이 실제로 이동해야 합니다.", moved.x < initial.x)
        }
        compose.onNodeWithTag("element_close").performTouchInput { swipe(center, center + Offset(-30f, -30f), 500) }
        compose.runOnIdle { assertEquals("다른 버튼을 이동해도 첫 이동을 유지해야 합니다.", moved, state.value.scene.elements.first { it.id == "night" }) }
        compose.onNodeWithTag("slider_너비").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.18f) }
        compose.runOnIdle { assertEquals(moved, state.value.scene.elements.first { it.id == "night" }) }
        compose.onNodeWithTag("select_night").performScrollTo().performClick()
        compose.onNodeWithTag("slider_가로 위치").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.35f) }
        compose.runOnIdle {
            assertEquals(0.35f, state.value.scene.elements.first { it.id == "night" }.x)
            assertEquals(0.18f, state.value.scene.elements.first { it.id == "close" }.width)
        }
    }

    @Test fun consecutiveRemovalsAndRemainingElementEditDoNotRestoreDeletedButtons() {
        render()
        compose.onNodeWithTag("tab_2").performClick()
        listOf("close", "night").forEachIndexed { index, id ->
            compose.onNodeWithTag("select_$id").performScrollTo().performClick()
            compose.onNodeWithTag("remove_element").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(5 - index, state.value.scene.elements.size)
                assertFalse(state.value.scene.elements.any { it.id == "close" })
            }
        }
        compose.onNodeWithTag("element_battery").performTouchInput { swipe(center, center + Offset(-35f, 0f), 500) }
        compose.onNodeWithTag("slider_너비").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        compose.onNodeWithTag("save_scene").performClick()
        compose.runOnIdle {
            assertEquals(4, saved!!.elements.size)
            assertFalse(saved!!.elements.any { it.id == "close" || it.id == "night" })
        }
        compose.onNodeWithTag("element_close").assertDoesNotExist()
        compose.onNodeWithTag("element_night").assertDoesNotExist()
    }

    @Test fun photoPositionRatioAndFitCanBeEdited() {
        render(StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PHOTO)))
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("늘이기").performScrollTo().performClick()
        compose.onNodeWithTag("slider_사진 확대").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        compose.onNodeWithTag("slider_사진 좌우").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(-0.3f) }
        compose.onNodeWithTag("slider_사진 가로 비율").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1.4f) }
        compose.runOnIdle {
            val b = state.value.scene.background
            assertEquals(PhotoFit.STRETCH, b.fit); assertEquals(1.6f, b.zoom); assertEquals(-0.3f, b.panX); assertEquals(1.4f, b.widthScale)
        }
    }

    @Test fun componentColorOverridesGlobalColor() {
        render()
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithText("기본 글자색 사용").performScrollTo()
        compose.onAllNodes(isToggleable()).onLast().performClick()
        compose.onNodeWithTag("hex_요소 글자색").performScrollTo().performTextReplacement("55AAFF")
        compose.onNodeWithText("적용").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(0xFF55AAFF, state.value.scene.elements.first().color)
            assertEquals(0xFFA6E8CB, state.value.scene.textColor)
        }
    }

    @Test fun workShiftCanBeAddedTurnedAndSavedWithoutStartingWork() {
        render(StandbyScene(elements = emptyList()))
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("add_WORK_SHIFT").performScrollTo().performClick()
        compose.onNodeWithTag("gauge_direction_VERTICAL").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(GaugeOrientation.VERTICAL, state.value.scene.elements.single().gaugeOrientation) }
        compose.onNodeWithTag("slider_너비").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        compose.onNodeWithTag("position_locked").performScrollTo().performClick()
        compose.onNodeWithTag("gauge_direction_HORIZONTAL").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(GaugeOrientation.HORIZONTAL, state.value.scene.elements.single().gaugeOrientation)
            assertEquals(0.2f, state.value.scene.elements.single().width)
        }
        compose.onNodeWithTag("gauge_direction_VERTICAL").performScrollTo().performClick()
        compose.onNodeWithTag("save_scene").performClick()
        compose.onNodeWithText("65%").assertExists()
        compose.onNodeWithTag("clock_in").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(GaugeOrientation.VERTICAL, saved!!.elements.single().gaugeOrientation)
            assertTrue(saved!!.elements.single().positionLocked)
        }
        capture("work-shift-editor.png")
    }

    @Test fun workShiftColorCanBeSelectedSavedAndResetToDefaultCyan() {
        render(StandbyScene(elements = listOf(newElement("shift", ElementKind.WORK_SHIFT))))
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("select_shift").performScrollTo().performClick()
        compose.onNodeWithTag("work_shift_default_color").performScrollTo().assertIsOn()
        compose.onNodeWithTag("hex_게이지 색상").performScrollTo().performTextReplacement("FF9F43")
        compose.onNodeWithText("적용").performScrollTo().performClick()
        compose.onNodeWithTag("save_scene").performClick()
        compose.runOnIdle { assertEquals(0xFFFF9F43, saved!!.elements.single().color) }
        compose.onNodeWithTag("work_shift_default_color").performScrollTo().assertIsOff()
        capture("work-shift-custom-color.png")
        compose.onNodeWithTag("work_shift_default_color").performClick()
        compose.onNodeWithTag("save_scene").performClick()
        compose.runOnIdle { assertNull(saved!!.elements.single().color) }
        compose.onNodeWithTag("work_shift_default_color").assertIsOn()
    }

    private fun capture(name: String) {
        val image = compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "validation-0.3.0").apply { mkdirs() }
        File(folder, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
