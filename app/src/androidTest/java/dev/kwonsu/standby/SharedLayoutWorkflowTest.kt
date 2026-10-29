package dev.kwonsu.standby

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
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
import java.util.UUID

class SharedLayoutWorkflowTest {
    private val legacyKinds = setOf(ElementKind.CLOCK, ElementKind.DATE, ElementKind.BATTERY, ElementKind.BRIGHTNESS)
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsStore(context)
    private fun library() = runBlocking { settings.library.first() }
    private fun snapshot() = runBlocking { settings.editorSnapshot() }
    private fun open(): ActivityScenario<EditorActivity> {
        val activity = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
        compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
        return activity
    }
    private fun presetTab() { compose.onNodeWithTag("tab_3").performClick() }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun set(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }
    private fun assertRenderedX(id: String, expected: Float) {
        val canvas = compose.onNodeWithTag("scene_canvas").fetchSemanticsNode().boundsInRoot
        val element = compose.onNodeWithTag("element_$id").fetchSemanticsNode().boundsInRoot
        assertEquals(expected, (element.left - canvas.left) / canvas.width, 0.015f)
    }

    @Test fun createLinkEditReopenStyleOverwriteAndUnlinkRestoreOriginalPresets() = runBlocking {
        check(context.packageName.endsWith(".validation"))
        val source = File(context.cacheDir, "shared-source-${UUID.randomUUID()}.gif")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-motion.gif").use { input -> source.outputStream().use { input.copyTo(it) } }
        val repo = MediaRepository(context)
        val media = repo.import(Uri.fromFile(source), true)
        val a = StandbyScene(font = ClockFont.MAPLESTORY,
            elements = defaultElements().filter { it.kind in legacyKinds } + newElement("brightness", ElementKind.BRIGHTNESS))
        val b = StandbyScene(font = ClockFont.KKUKKUKK, textColor = 0xFFFFAABB,
            background = BackgroundSpec(kind = BackgroundKind.PRESET, preset = BackdropPreset.DUSK),
            elements = a.elements.map { it.copy(id = "b-${it.id}", x = 0.1f, y = 0.12f, width = 0.25f, height = 0.14f, color = 0xFFFFEECC) } +
                newElement("b-gif", ElementKind.GIF, media).copy(x = 0.65f, y = 0.55f, width = 0.2f, height = 0.3f))
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.saveScene(a)
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("a", "아침", a), ScenePreset("b", "노을", b)), listOf("a", "b"), true) }
            activity = open(); presetTab()
            click("capture_shared_layout")
            legacyKinds.forEach { compose.onNodeWithTag("shared_toggle_${it}_0").performScrollTo().performClick() }
            compose.onNodeWithTag("save_shared_layout").performClick()
            compose.waitUntil(5000) { library().sharedLayout != null && snapshot().draftUsesSharedLayout }
            listOf("a", "b").forEach { id ->
                click("shared_preset_$id")
                compose.waitUntil(5000) { library().presets.first { it.id == id }.useSharedLayout }
            }
            assertEquals(listOf(a, b), library().presets.map { it.scene })
            click("load_preset_b"); compose.onNodeWithTag("confirm_preset_action").performClick()
            compose.waitUntil(5000) { snapshot().sourcePresetId == "b" && snapshot().draft == b }
            assertRenderedX("b-clock", a.elements.first().x)

            // 새 GIF가 있는 프리셋을 불러오면 기존 편집기는 요소 탭을 선택한다.
            presetTab()
            click("edit_shared_layout")
            compose.onNodeWithTag("shared_editor").assertExists()
            compose.onNodeWithTag("shared_tab_placement").performClick()
            val commonClock = compose.onNode(hasTestTag("element_b-clock") and hasAnyAncestor(hasTestTag("shared_editor")))
            val beforeClock = commonClock.fetchSemanticsNode().boundsInRoot
            commonClock.performTouchInput { swipe(center, center + Offset(45f, 0f), 350) }
            assertTrue("공통 편집에서도 실제 드래그로 이동", commonClock.fetchSemanticsNode().boundsInRoot.left > beforeClock.left)
            val contextGif = compose.onNode(hasTestTag("element_b-gif") and hasAnyAncestor(hasTestTag("shared_editor")))
            val beforeGif = contextGif.fetchSemanticsNode().boundsInRoot
            contextGif.performTouchInput { swipe(center, center + Offset(-40f, 0f), 350) }
            assertEquals("공통 편집은 GIF 배치를 바꾸지 않음", beforeGif, contextGif.fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("shared_select_CLOCK_0").performClick()
            set("shared_slider_너비", 0.42f); set("shared_slider_높이", 0.22f)
            set("shared_slider_가로 위치", 0.38f); set("shared_slider_세로 위치", 0.3f)
            click("shared_position_locked")
            compose.onNodeWithTag("shared_slider_가로 위치").assertIsNotEnabled()
            val bitmap = compose.onNodeWithTag("shared_canvas").captureToImage().asAndroidBitmap()
            val previewDir = File(context.filesDir, "validation-0.5.0").apply { mkdirs() }
            File(previewDir, "shared-layout-editor.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.onNodeWithTag("save_shared_layout").performClick()
            compose.waitUntil(5000) { library().sharedLayout!!.placements.first().x == 0.38f &&
                compose.onAllNodesWithTag("shared_editor").fetchSemanticsNodes().isEmpty() }
            val shared = library().sharedLayout!!
            assertTrue(shared.placements.first().positionLocked)
            assertEquals(listOf(a, b), library().presets.map { it.scene })
            assertEquals(b, snapshot().draft)
            assertRenderedX("b-clock", 0.38f)

            compose.onNodeWithTag("save_scene").performClick()
            compose.waitUntil(5000) { snapshot().applied == b && snapshot().appliedUsesSharedLayout }
            activity.close(); activity = open()
            assertRenderedX("b-clock", 0.38f)
            compose.onNodeWithTag("tab_2").performClick()
            click("select_b-clock")
            compose.onNodeWithTag("slider_가로 위치").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("position_locked").performScrollTo().assertIsNotEnabled()
            click("select_b-gif")
            set("slider_가로 위치", 0.6f)
            compose.onNodeWithTag("tab_0").performClick()
            click("font_ESAMANRU")
            compose.onNodeWithTag("hex_기본 글자색").performScrollTo().performTextReplacement("CCDDEE")
            compose.onNodeWithText("적용").performScrollTo().performClick()
            compose.waitUntil(5000) { snapshot().draft.textColor == 0xFFCCDDEE && snapshot().draft.font == ClockFont.ESAMANRU }
            presetTab(); click("overwrite_preset_b"); compose.onNodeWithTag("confirm_preset_action").performClick()
            compose.waitUntil(5000) { library().presets[1].scene.font == ClockFont.ESAMANRU }
            val updated = library().presets[1].scene
            assertEquals(b.elements.filter { it.kind in legacyKinds }, updated.elements.filter { it.kind in legacyKinds })
            assertEquals(b.background, updated.background)
            assertEquals(0xFFCCDDEE, updated.textColor)
            assertEquals(0.6f, updated.elements.last().x)
            assertTrue(repo.file(media)!!.exists())

            click("shared_preset_b")
            compose.waitUntil(5000) { !library().presets[1].useSharedLayout }
            click("load_preset_b"); compose.onNodeWithTag("confirm_preset_action").performClick()
            compose.waitUntil(5000) { !snapshot().draftUsesSharedLayout && snapshot().draft == updated }
            assertRenderedX("b-clock", b.elements.first().x)
            assertEquals(updated, settings.selectForEntry("shared-workflow", advance = true).let { selected ->
                if (selected.presetId == "b") selected.scene else settings.selectForEntry("shared-workflow", advance = true).scene
            })
            assertTrue(library().presets[0].useSharedLayout)
            assertEquals(a, library().presets[0].scene)
            activity.close(); activity = open()
            assertRenderedX("b-clock", b.elements.first().x)
            assertFalse(snapshot().draftUsesSharedLayout)
        } finally {
            activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene())
            repo.file(media)?.delete(); source.delete()
        }
    }

    @Test fun unsavedSharedEditsSurviveActivityRecreationAndCanBeDiscarded() = runBlocking {
        check(context.packageName.endsWith(".validation"))
        val scene = StandbyScene(elements = listOf(newElement("clock", ElementKind.CLOCK).copy(width = 0.3f)))
        val shared = SharedLayout.fromScene(scene)
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.saveScene(scene, true)
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("p", "원본", scene, true)), sharedLayout = shared) }
            activity = open(); presetTab(); click("edit_shared_layout")
            compose.onNodeWithTag("shared_tab_placement").performClick()
            set("shared_slider_가로 위치", 0.45f)
            activity.recreate()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("shared_editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("shared_slider_가로 위치").performScrollTo().assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.45f, 0f..0.7f))
            compose.onNodeWithTag("cancel_shared_layout").performClick()
            compose.onNodeWithTag("discard_shared_layout").performClick()
            compose.onNodeWithTag("shared_editor").assertDoesNotExist()
            assertEquals(shared, library().sharedLayout)
            assertEquals(scene, library().presets.single().scene)
        } finally { activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene()) }
    }
}
