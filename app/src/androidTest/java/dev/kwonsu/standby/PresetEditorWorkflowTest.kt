package dev.kwonsu.standby

import android.content.Intent
import android.graphics.Bitmap
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

class PresetEditorWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun loadedPresetShowsSelectionAndSaveOffersCancelCurrentOnlyOrPresetIncludingExit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".validation"))
        val settings = SettingsStore(context)
        fun snapshot() = runBlocking { settings.editorSnapshot() }
        fun library() = runBlocking { settings.library.first() }
        fun open(): ActivityScenario<EditorActivity> {
            val result = ActivityScenario.launch<EditorActivity>(Intent(context, EditorActivity::class.java))
            compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
            return result
        }
        fun font(name: String) { compose.onNodeWithTag("tab_0").performClick(); compose.onNodeWithTag("font_$name").performScrollTo().performClick() }
        val original = StandbyScene(font = ClockFont.MAPLESTORY)
        val other = ScenePreset("other", "보존할 프리셋", StandbyScene(font = ClockFont.KKUKKUKK))
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            settings.saveScene(StandbyScene())
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("p", "아침 화면", original), other)) }
            activity = open()
            compose.onNodeWithTag("tab_3").performClick()
            compose.onNodeWithTag("load_preset_p").performScrollTo().performClick()
            compose.waitUntil(5000) { snapshot().sourcePresetId == "p" }
            compose.onNodeWithTag("selected_preset_name").assertTextEquals("선택: 아침 화면")
            compose.onNodeWithTag("selected_preset_p").performScrollTo().assertExists()
            compose.onNodeWithTag("save_scene").performClick()
            compose.waitUntil(5000) { snapshot().applied == original }
            compose.onNodeWithTag("save_preset_dialog").assertDoesNotExist()

            font("KKUKKUKK")
            compose.onNodeWithTag("save_scene").performClick()
            compose.onNodeWithTag("save_preset_dialog").assertExists()
            val directory = File(context.filesDir, "validation-0.10.0").apply { mkdirs() }
            File(directory, "preset-save-dialog.png").outputStream().use {
                compose.onNodeWithTag("save_preset_dialog").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("cancel_preset_save").performClick()
            assertEquals(original, snapshot().applied); assertEquals(original, library().presets.first().scene)
            compose.onNodeWithTag("save_scene").performClick()
            activity.recreate()
            compose.onNodeWithTag("save_current_only").performClick()
            compose.waitUntil(5000) { snapshot().applied.font == ClockFont.KKUKKUKK }
            assertEquals(original, library().presets.first().scene)
            activity.close(); activity = open()
            compose.onNodeWithTag("selected_preset_name").assertTextEquals("선택: 아침 화면")

            font("ESAMANRU")
            compose.onNodeWithTag("save_scene").performClick()
            compose.onNodeWithTag("save_with_preset").performClick()
            compose.waitUntil(5000) { library().presets.first().scene.font == ClockFont.ESAMANRU && snapshot().applied.font == ClockFont.ESAMANRU }
            assertEquals(other, library().presets.last())
            activity.close(); activity = open()
            compose.onNodeWithTag("selected_preset_name").assertTextEquals("선택: 아침 화면")
            compose.onNodeWithTag("save_scene").performClick()
            compose.onNodeWithTag("save_preset_dialog").assertDoesNotExist()
            font("KKUKKUKK")
            compose.onNodeWithText("돌아가기").performClick()
            compose.onNodeWithText("저장 후 나가기").performClick()
            compose.onNodeWithTag("save_with_preset").performClick()
            compose.waitUntil(5000) { activity!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(ClockFont.KKUKKUKK, snapshot().applied.font)
            assertEquals(snapshot().applied, library().presets.first().scene)
            assertEquals(other, library().presets.last())
        } finally { activity?.close(); settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene()) }
    }

    @Test fun saveChooseReorderRemoveAndLoadPresetsThroughRealEditor() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".validation"))
        val settings = SettingsStore(context)
        var activity: ActivityScenario<EditorActivity>? = null
        fun library() = runBlocking { settings.library.first() }
        try {
            settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene(font = ClockFont.ESAMANRU))
            activity = ActivityScenario.launch(Intent(context, EditorActivity::class.java))
            compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab_3").performClick()
            listOf("아침", "밤", "제외할 화면").forEachIndexed { index, name ->
                compose.onNodeWithTag("preset_name").performScrollTo().performTextReplacement(name)
                compose.onNodeWithTag("save_preset").performScrollTo().performClick()
                compose.waitUntil(5000) { library().presets.size == index + 1 }
            }
            val (first, second, excluded) = library().presets
            listOf(first, second).forEach { preset ->
                compose.onNodeWithTag("include_preset_${preset.id}").performScrollTo().performClick()
                compose.waitUntil(5000) { preset.id in library().cycleIds }
            }
            compose.onNodeWithTag("cycle_enabled").performScrollTo().performClick()
            compose.waitUntil(5000) { library().cycleEnabled }
            compose.onNodeWithTag("cycle_down_${first.id}").performScrollTo().performClick()
            compose.waitUntil(5000) { library().cycleIds == listOf(second.id, first.id) }
            assertFalse(excluded.id in library().cycleIds)
            compose.onNodeWithTag("cycle_remove_${second.id}").performScrollTo().performClick()
            compose.waitUntil(5000) { library().cycleIds == listOf(first.id) }
            compose.onNodeWithTag("delete_preset_${excluded.id}").performScrollTo().performClick()
            compose.onNodeWithTag("confirm_preset_action").performClick()
            compose.waitUntil(5000) { library().presets.size == 2 }
            compose.onNodeWithTag("tab_0").performClick()
            compose.onNodeWithTag("font_MAPLESTORY").performScrollTo().performClick()
            compose.onNodeWithTag("tab_3").performClick()
            compose.onNodeWithTag("load_preset_${first.id}").performScrollTo().performClick()
            compose.onNodeWithTag("confirm_preset_action").performClick()
            compose.waitUntil(5000) { runBlocking { settings.editorScene.first().font == ClockFont.ESAMANRU } }
            assertEquals(ClockFont.ESAMANRU, library().presets.first().scene.font)
            activity.close()
            activity = ActivityScenario.launch(Intent(context, EditorActivity::class.java))
            compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab_3").performClick()
            compose.onNodeWithTag("cycle_${first.id}").performScrollTo().assertExists()
            assertEquals(listOf(first.id), library().cycleIds)
        } finally {
            activity?.close()
            settings.updateLibrary { PresetLibrary() }; settings.saveScene(StandbyScene())
        }
    }
}
