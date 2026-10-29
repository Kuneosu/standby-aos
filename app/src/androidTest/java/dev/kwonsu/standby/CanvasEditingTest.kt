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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class CanvasEditingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, tag: String = "scene_canvas"): Bitmap {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "validation-0.12.0").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun rightAlignedClockKeepsChosenSizeWhenBoxWidthChangesAcrossFonts() {
        val scene = mutableStateOf(StandbyScene(textColor = 0xFFFFFFFF, elements = listOf(
            SceneElement("clock", ElementKind.CLOCK, x = 0.2f, y = 0.2f, width = 0.8f, height = 0.5f,
                textAlignment = ElementTextAlignment.RIGHT, textSize = 0.12f))))
        compose.setContent { FoldTheme { SceneCanvas(scene.value, PowerState(), LocalDateTime.of(2026, 10, 28, 18, 48),
            Modifier.fillMaxWidth().height(260.dp)) } }
        ClockFont.entries.forEach { font ->
            compose.runOnIdle { scene.value = scene.value.copy(font = font, elements = scene.value.elements.map { it.copy(x = 0.2f, width = 0.8f) }) }
            val before = compose.onNodeWithTag("text_clock").fetchSemanticsNode().boundsInRoot
            val box = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
            assertEquals("글자 오른쪽이 요소 오른쪽에 맞아야 합니다.", box.right, before.right, 1f)
            val image = capture("right-edge-${font.name.lowercase()}.png", "element_clock")
            var lastInk = -1
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (PixelColor.red(image.getPixel(x, y)) > 220) lastInk = maxOf(lastInk, x)
            }
            assertTrue(lastInk >= 0)
            assertTrue("오른쪽에 큰 빈 여백이 남으면 안 됩니다.", image.width - 1 - lastInk <= before.height * 0.2f + 3)
            compose.runOnIdle { scene.value = scene.value.copy(elements = scene.value.elements.map { it.copy(x = 0.6f, width = 0.4f) }) }
            val after = compose.onNodeWithTag("text_clock").fetchSemanticsNode().boundsInRoot
            assertEquals(before.width, after.width, 1f)
            assertEquals(before.height, after.height, 1f)
            assertEquals(before.right, after.right, 1f)
        }
    }

    @Test fun selectingCoveredElementFromListMakesOnlyThatElementDraggableAndResizable() {
        val behind = SceneElement("behind", ElementKind.CLOCK, x = 0.45f, y = 0.2f, width = 0.55f, height = 0.5f)
        val front = SceneElement("front", ElementKind.BATTERY, x = behind.x, y = behind.y, width = behind.width, height = behind.height)
        val state = mutableStateOf(EditorState(scene = StandbyScene(elements = listOf(behind, front)), loading = false))
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.setContent { FoldTheme { SceneEditor(state.value,
            { update -> state.value = state.value.copy(scene = update(state.value.scene)) }, { _, _ -> }, {}, {}, {}, {}) } }
        compose.onNodeWithTag("open_layers").performClick()
        compose.onNodeWithTag("layer_pick_behind").performClick()
        compose.onNodeWithTag("resize_top_left_behind").performTouchInput { swipe(center, center + Offset(-35f, -15f), 400) }
        compose.runOnIdle {
            val resized = state.value.scene.elements.first()
            assertTrue(resized.width > behind.width)
            assertEquals(1f, resized.x + resized.width, 0.001f)
            assertEquals(front, state.value.scene.elements.last())
        }
        val resized = state.value.scene.elements.first()
        compose.onNodeWithTag("element_behind").performTouchInput { swipe(center, center + Offset(-45f, 15f), 400) }
        compose.runOnIdle {
            assertTrue(state.value.scene.elements.first().x < resized.x)
            assertEquals(front, state.value.scene.elements.last())
            assertEquals(listOf("behind", "front"), state.value.scene.elements.map { it.id })
        }
        capture("covered-element-selected.png")
    }

    @Test fun fullscreenUsesWholeCanvasAndKeepsEditsWhenReturningToControls() {
        val state = mutableStateOf(EditorState(loading = false))
        compose.setContent { FoldTheme { SceneEditor(state.value,
            { update -> state.value = state.value.copy(scene = update(state.value.scene)) }, { _, _ -> }, {}, {}, {}, {}) } }
        compose.onNodeWithTag("open_fullscreen").performClick()
        val frame = compose.onNodeWithTag("fullscreen_editor").fetchSemanticsNode().boundsInRoot
        assertEquals(frame, compose.onNodeWithTag("scene_canvas").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("fullscreen_layers").performClick()
        compose.onNodeWithTag("layer_pick_battery").assertIsDisplayed().performClick()
        compose.onNodeWithTag("fullscreen_adjust").performClick()
        compose.onNodeWithTag("text_align_RIGHT").performClick()
        compose.onNodeWithTag("close_fullscreen_panel").performClick()
        compose.onNodeWithTag("fullscreen_clean_view").performClick()
        compose.onNodeWithTag("resize_battery").assertDoesNotExist()
        compose.onNodeWithTag("show_fullscreen_tools").performClick()
        compose.onNodeWithTag("exit_fullscreen").performClick()
        compose.onNodeWithTag("fullscreen_editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ElementTextAlignment.RIGHT, state.value.scene.elements.first { it.id == "battery" }.textAlignment) }
    }
}
