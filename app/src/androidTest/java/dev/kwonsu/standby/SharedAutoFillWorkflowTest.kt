package dev.kwonsu.standby

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import java.util.UUID

class SharedAutoFillWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsStore(context)
    private fun library() = runBlocking { settings.library.first() }
    private fun snapshot() = runBlocking { settings.editorSnapshot() }
    private fun open(): ActivityScenario<EditorActivity> {
        val result = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
        compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
        return result
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun waitReady() { compose.waitUntil(5000) {
        compose.onAllNodes(hasTestTag("save_scene") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    } }

    @Test fun enablingCommonLayoutAddsVisibleEditableGaugeAndGifAndPreservesThemAfterSaveAndUnlink() = runBlocking {
        check(context.packageName.endsWith(".validation"))
        val source = File(context.cacheDir, "auto-source-${UUID.randomUUID()}.gif")
        val repo = MediaRepository(context)
        var media: String? = null
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("test-motion.gif").use { input -> source.outputStream().use { input.copyTo(it) } }
            media = repo.import(Uri.fromFile(source), true)
            val reference = StandbyScene(elements = listOf(newElement("shift", ElementKind.WORK_SHIFT).copy(x = 0.08f, y = 0.75f, width = 0.8f),
                newElement("gif", ElementKind.GIF, media).copy(x = 0.65f, y = 0.1f, width = 0.25f, height = 0.4f, speed = 1.5f)))
            val target = StandbyScene(elements = listOf(newElement("clock", ElementKind.CLOCK).copy(x = 0.05f, width = 0.5f)))
            val shared = SharedLayout.fromScene(reference)
            settings.saveScene(target)
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("source", "기준", reference), ScenePreset("target", "시계만 있던 화면", target),
                ScenePreset("off", "연결하지 않은 화면", target)), sharedLayout = shared) }
            activity = open(); compose.onNodeWithTag("tab_3").performClick(); click("load_preset_target")
            compose.waitUntil(5000) { snapshot().sourcePresetId == "target" }
            waitReady()
            click("shared_preset_target")
            compose.waitUntil(5000) { snapshot().draft.elements.size == 3 && library().presets[1].scene.elements.size == 3 }
            waitReady()
            val added = library().presets[1].scene
            assertEquals(target.elements.single(), added.elements.first())
            assertEquals(target, library().presets[2].scene)
            compose.onNodeWithTag("element_shared_WORK_SHIFT_0").assertExists()
            compose.onNodeWithTag("element_shared_GIF_0").assertExists()
            compose.onNodeWithTag("tab_2").performClick()
            val diagnosticDirectory = File(context.filesDir, "validation-0.10.1").apply { mkdirs() }
            File(diagnosticDirectory, "before-element-selection.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            click("select_shared_WORK_SHIFT_0")
            compose.onNodeWithTag("select_shared_WORK_SHIFT_0").assertIsSelected()
            compose.onNodeWithTag("slider_가로 위치").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("remove_element").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("hex_게이지 색상").performScrollTo().performTextReplacement("FF8844")
            compose.onNodeWithText("적용").performScrollTo().performClick()
            compose.waitUntil(5000) { snapshot().draft.elements.first { it.kind == ElementKind.WORK_SHIFT }.color == 0xFFFF8844 }
            compose.onNodeWithTag("save_scene").performClick(); compose.onNodeWithTag("save_with_preset").performClick()
            compose.waitUntil(5000) { library().presets[1].scene.elements.first { it.kind == ElementKind.WORK_SHIFT }.color == 0xFFFF8844 }
            waitReady()
            assertEquals(reference, library().presets[0].scene)
            activity.close(); activity = open()
            compose.onNodeWithTag("element_shared_GIF_0").assertExists()
            compose.onNodeWithTag("element_shared_WORK_SHIFT_0").assertExists()
            val directory = File(context.filesDir, "validation-0.10.1").apply { mkdirs() }
            File(directory, "auto-added-elements.png").outputStream().use {
                compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("tab_3").performClick(); click("shared_preset_target")
            compose.waitUntil(5000) { !snapshot().draftUsesSharedLayout }
            waitReady()
            assertEquals(3, snapshot().draft.elements.size)
            compose.onNodeWithTag("tab_2").performClick(); click("select_shared_GIF_0")
            compose.onNodeWithTag("remove_element").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5000) { snapshot().draft.elements.size == 2 }
            assertEquals(3, library().presets[1].scene.elements.size)
            assertTrue(repo.file(media)!!.exists())
        } finally {
            activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene())
            repo.file(media)?.delete(); source.delete()
        }
    }
}
