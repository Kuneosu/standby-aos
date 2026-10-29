package dev.kwonsu.standby

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
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

class SharedElementSelectionWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsStore(context)
    private fun library() = runBlocking { settings.library.first() }
    private fun open(): ActivityScenario<EditorActivity> {
        val result = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
        compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_3").performClick()
        return result
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun drag(itemTag: String, zoneTag: String) {
        val item = compose.onNodeWithTag(itemTag).performScrollTo()
        val from = item.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(zoneTag).fetchSemanticsNode().boundsInRoot
        item.performTouchInput {
            val start = Offset(width / 2f, 12f)
            val end = target.center - from.topLeft
            down(start); advanceEventTime(700)
            repeat(20) { index -> moveTo(start + (end - start) * ((index + 1) / 20f), 20) }
            up()
        }
    }

    @Test fun actualDragDropSelectsGaugeGifButtonAndSecondClockAndPersistsWithoutChangingOriginals() = runBlocking<Unit> {
        check(context.packageName.endsWith(".validation"))
        val scene = StandbyScene(elements = listOf(newElement("shift", ElementKind.WORK_SHIFT), newElement("gif", ElementKind.GIF),
            newElement("close", ElementKind.CLOSE_BUTTON), newElement("clock-1", ElementKind.CLOCK),
            newElement("clock-2", ElementKind.CLOCK).copy(x = 0.05f)))
        val other = scene.copy(elements = scene.elements.map { it.copy(id = "other-${it.id}", x = 0.01f) })
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.saveScene(scene)
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("a", "기준", scene), ScenePreset("b", "연결", other, true))) }
            activity = open(); click("capture_shared_layout")
            drag("shared_item_available_WORK_SHIFT_0", "shared_tab_elements")
            compose.onNodeWithTag("shared_item_selected_WORK_SHIFT_0").assertDoesNotExist()
            drag("shared_item_available_WORK_SHIFT_0", "shared_selected_zone")
            compose.onNodeWithTag("shared_item_selected_WORK_SHIFT_0").assertExists()
            drag("shared_item_selected_WORK_SHIFT_0", "shared_available_zone")
            compose.onNodeWithTag("shared_item_available_WORK_SHIFT_0").assertExists()
            drag("shared_item_available_WORK_SHIFT_0", "shared_selected_zone")
            listOf("GIF_0", "CLOSE_BUTTON_0", "CLOCK_1").forEach { click("shared_toggle_$it") }
            compose.onNodeWithTag("shared_item_available_CLOCK_0").assertExists()
            activity.recreate()
            compose.onNodeWithTag("shared_item_selected_CLOCK_1").assertExists()
            val directory = File(context.filesDir, "validation-0.10.0").apply { mkdirs() }
            File(directory, "shared-element-selection.png").outputStream().use {
                compose.onNodeWithTag("shared_editor").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("save_shared_layout").performClick()
            compose.waitUntil(5000) { library().sharedLayout?.placements?.size == 4 && compose.onAllNodesWithTag("shared_editor").fetchSemanticsNodes().isEmpty() }
            val shared = library().sharedLayout!!
            assertEquals(setOf("shift", "gif", "close", "clock-2"), shared.placementFor(scene).keys)
            assertEquals(listOf(scene, other), library().presets.map { it.scene })
            assertEquals(0.01f, library().displayScene(library().presets[1]).elements.first { it.id == "other-clock-1" }.x)
            assertEquals(0.05f, library().displayScene(library().presets[1]).elements.first { it.id == "other-clock-2" }.x)
            activity.close(); activity = open(); click("edit_shared_layout")
            compose.onNodeWithTag("shared_item_selected_WORK_SHIFT_0").assertExists()
            compose.onNodeWithTag("shared_item_selected_CLOCK_1").assertExists()
            compose.onNodeWithTag("shared_item_available_CLOCK_0").assertExists()
        } finally { activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene()) }
    }

    @Test fun autoAddedSelectionsRemainAfterRemovingCommonSelectionAndCanBeCancelledOrSaved() = runBlocking {
        check(context.packageName.endsWith(".validation"))
        val reference = StandbyScene(elements = listOf(newElement("clock", ElementKind.CLOCK)))
        val saved = SharedLayout.fromScene(reference.copy(elements = reference.elements + newElement("missing", ElementKind.WORK_SHIFT)))
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.saveScene(reference, true)
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("p", "원본", reference, true)), sharedLayout = saved) }
            activity = open(); click("edit_shared_layout")
            compose.onNodeWithTag("shared_item_selected_WORK_SHIFT_0").assertExists()
            val filled = library().presets.single().scene
            assertEquals(2, filled.elements.size)
            assertEquals(ElementKind.WORK_SHIFT, filled.elements.last().kind)
            click("shared_toggle_WORK_SHIFT_0"); click("shared_toggle_CLOCK_0")
            compose.onNodeWithTag("cancel_shared_layout").performClick()
            compose.onNodeWithTag("discard_shared_layout").performClick()
            assertEquals(saved, library().sharedLayout)
            click("edit_shared_layout")
            click("shared_toggle_WORK_SHIFT_0"); click("shared_toggle_CLOCK_0")
            compose.onNodeWithTag("save_shared_layout").performClick()
            compose.waitUntil(5000) { library().sharedLayout == SharedLayout() && compose.onAllNodesWithTag("shared_editor").fetchSemanticsNodes().isEmpty() }
            activity.close(); activity = open(); click("edit_shared_layout")
            compose.onNodeWithTag("shared_item_available_CLOCK_0").assertExists()
            assertEquals(filled, library().displayScene(library().presets.single()))
            assertEquals(filled, library().presets.single().scene)
        } finally { activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene()) }
    }
}
