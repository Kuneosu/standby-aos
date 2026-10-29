package dev.kwonsu.standby

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 실제 Activity·회전·DataStore 경로는 별도 검사 앱에서만 실행한다. */
class FullscreenEditorWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsStore(context)
    private fun await(tag: String) = compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun snapshot() = runBlocking { settings.editorSnapshot() }
    private fun library() = runBlocking { settings.library.first() }
    private fun open(): ActivityScenario<EditorActivity> {
        check(context.packageName.endsWith(".validation"))
        val activity = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
        activity.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        await("open_fullscreen")
        return activity
    }
    private fun set(tag: String, value: Float) = compose.onNodeWithTag(tag).performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("fullscreen_editor").captureToImage().asAndroidBitmap()
        val folder = File(context.filesDir, "validation-0.12.2").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun placeAtRight(name: String) {
        val target = compose.onNodeWithTag("place_horizontal_2").performScrollTo().assertIsDisplayed()
        val folder = File(context.filesDir, "validation-0.12.2").apply { mkdirs() }
        File(folder, "$name-bounds.txt").writeText(listOf("fullscreen_editor", "fullscreen_panel", "place_horizontal_2")
            .joinToString("\n") { tag -> "$tag: ${compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot}" })
        capture("$name-before.png")
        target.performClick()
        capture("$name-after.png")
    }

    @Test fun fullscreenEditsSurviveRecreationAndSaveToSelectedPresetAndDisk() = runBlocking {
        val initial = StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PRESET, preset = BackdropPreset.OCEAN),
            elements = listOf(SceneElement("clock", ElementKind.CLOCK, x = 0.35f, y = 0.15f, width = 0.55f, height = 0.4f),
                SceneElement("date", ElementKind.DATE, x = 0.35f, y = 0.58f, width = 0.55f, height = 0.12f)))
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.updateLibrary { PresetLibrary(presets = listOf(ScenePreset("full", "전체 화면 검사", initial))) }
            settings.saveScene(initial, sourcePresetId = "full")
            activity = open()
            compose.onNodeWithTag("open_fullscreen").performClick()
            await("fullscreen_editor")
            compose.waitForIdle()
            val frame = compose.onNodeWithTag("fullscreen_editor").fetchSemanticsNode().boundsInRoot
            activity.onActivity {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, it.requestedOrientation)
                assertEquals(it.window.decorView.width.toFloat(), frame.width, 2f)
                assertEquals(it.window.decorView.height.toFloat(), frame.height, 2f)
            }
            compose.onNodeWithTag("fullscreen_adjust").performClick()
            compose.onNodeWithTag("text_align_RIGHT").performClick()
            compose.onNodeWithTag("automatic_text_size").performScrollTo().performClick()
            set("slider_글자 크기", 0.18f)
            set("slider_너비", 0.65f)
            placeAtRight("screen-right-placement")
            compose.onNodeWithTag("padding_horizontal").performScrollTo().performTextReplacement("1.25")
            compose.onNodeWithTag("padding_horizontal").performImeAction()
            compose.onNodeWithTag("close_fullscreen_panel").performClick()
            compose.waitUntil(5000) { snapshot().draft.elements.first().horizontalPadding == 0.0125f }
            val fittedSize = snapshot().draft.elements.first().contentSize!!
            val box = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
            assertEquals(box.width * 0.0125f, box.right - compose.onNodeWithTag("text_clock").fetchSemanticsNode().boundsInRoot.right, 1f)
            compose.onNodeWithTag("resize_top_left_clock").performTouchInput { swipe(center, center + Offset(-40f, -12f), 400) }
            compose.waitUntil(5000) { snapshot().draft.elements.first().contentSize!! > fittedSize }
            val edited = snapshot().draft
            assertEquals(1f, edited.elements.first().let { it.x + it.width }, 0.001f)
            assertEquals(0.18f, edited.elements.first().textSize!!)
            assertEquals(initial, settings.scene.first())
            capture("fullscreen-right-edge-edit.png")
            activity.recreate()
            await("fullscreen_editor")
            assertEquals(edited, snapshot().draft)
            compose.onNodeWithTag("save_scene").performScrollTo().performClick()
            await("save_preset_dialog")
            compose.onNodeWithTag("save_with_preset").performClick()
            compose.waitUntil(5000) { snapshot().applied == edited && library().presets.single().scene == edited }
            compose.onNodeWithTag("fullscreen_clean_view").performScrollTo().performClick()
            capture("fullscreen-clean-saved.png")
            compose.onNodeWithTag("show_fullscreen_tools").performClick()
            compose.onNodeWithTag("exit_fullscreen").performClick()
            await("open_fullscreen")
            activity.close(); activity = open()
            assertEquals(edited, snapshot().draft)
            assertEquals(edited, snapshot().applied)
            assertEquals("full", snapshot().sourcePresetId)
            compose.onNodeWithTag("open_fullscreen").performClick(); await("fullscreen_editor")
            val reopened = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
            assertEquals(reopened.width * 0.0125f, reopened.right - compose.onNodeWithTag("text_clock").fetchSemanticsNode().boundsInRoot.right, 1f)
        } finally {
            activity?.close()
            settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene())
        }
    }

    @Test fun sharedFullscreenChangesPlacementButPreservesEachPresetsTextStyle() = runBlocking {
        val a = StandbyScene(elements = listOf(SceneElement("clock", ElementKind.CLOCK, x = 0.1f, y = 0.2f, width = 0.5f,
            height = 0.4f, textAlignment = ElementTextAlignment.RIGHT, textSize = 0.18f)))
        val b = a.copy(font = ClockFont.KKUKKUKK, elements = a.elements.map { it.copy(id = "b-clock", x = 0.3f,
            textAlignment = ElementTextAlignment.LEFT, textSize = 0.12f, color = 0xFFFFB6CA) })
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.updateLibrary { PresetLibrary(presets = listOf(ScenePreset("a", "공통 A", a, true), ScenePreset("b", "공통 B", b, true)),
                sharedLayout = SharedLayout.fromScene(a)) }
            settings.saveScene(a, useSharedLayout = true, sourcePresetId = "a")
            activity = open()
            compose.onNodeWithTag("open_fullscreen").performClick(); await("fullscreen_editor")
            compose.onNodeWithTag("resize_clock").assertDoesNotExist()
            compose.onNodeWithTag("fullscreen_adjust").performClick()
            compose.onNodeWithTag("fullscreen_edit_shared").performClick()
            await("shared_editor"); await("fullscreen_editor")
            compose.onNodeWithTag("fullscreen_layers").performClick()
            compose.onNodeWithTag("layer_pick_clock").performClick()
            compose.onNodeWithTag("fullscreen_adjust").performClick()
            compose.onAllNodes(hasTestTag("automatic_text_size") and hasAnyAncestor(hasTestTag("shared_editor"))).assertCountEquals(0)
            set("slider_너비", 0.6f)
            placeAtRight("shared-right-placement")
            compose.onNodeWithTag("padding_horizontal").performScrollTo().performTextReplacement("1.25")
            compose.onNodeWithTag("padding_horizontal").performImeAction()
            compose.onNodeWithTag("close_fullscreen_panel").performClick()
            capture("shared-fullscreen-placement.png")
            compose.onNodeWithTag("save_shared_layout").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("shared_editor").fetchSemanticsNodes().isEmpty() }
            val saved = library()
            assertEquals(listOf(a, b), saved.presets.map { it.scene })
            assertTrue(saved.sharedLayout!!.placements.single().contentSize!! > 0f)
            assertEquals(0.0125f, saved.sharedLayout!!.placements.single().horizontalPadding, 0.0001f)
            saved.presets.forEach { preset ->
                val display = saved.displayScene(preset).elements.single()
                assertEquals(1f, display.x + display.width, 0.001f)
                assertEquals(preset.scene.elements.single().textAlignment, display.textAlignment)
                assertEquals(preset.scene.elements.single().textSize, display.textSize)
            }
            activity.close(); activity = open()
            assertEquals(saved, library())
        } finally {
            activity?.close()
            settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene())
        }
    }
}
