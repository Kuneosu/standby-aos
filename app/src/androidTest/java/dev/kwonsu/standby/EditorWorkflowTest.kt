package dev.kwonsu.standby

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** 실제 설정·ViewModel·Activity를 연결한다. 개인용 앱과 다른 validation 패키지에서만 실행한다. */
@RunWith(AndroidJUnit4::class)
class EditorWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun sequentialEditsSurviveDraftReopenAndSavedReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".validation"))
        val settings = SettingsStore(context)
        val initial = StandbyScene(font = ClockFont.MAPLESTORY,
            background = BackgroundSpec(kind = BackgroundKind.PRESET, preset = BackdropPreset.OCEAN))
        var activity: ActivityScenario<EditorActivity>? = null
        fun openEditor() {
            activity = ActivityScenario.launch(Intent(context, EditorActivity::class.java))
            compose.waitUntil(5000) { compose.onAllNodesWithTag("element_battery").fetchSemanticsNodes().isNotEmpty() }
        }
        fun draft() = runBlocking { settings.editorScene.first() }
        try {
            settings.saveScene(initial)
            openEditor()
            compose.onNodeWithTag("element_night").performTouchInput { swipe(center, center + Offset(-60f, -30f), 500) }
            compose.waitUntil(5000) { draft().elements.first { it.id == "night" }.x < initial.elements.first { it.id == "night" }.x }
            val movedNight = draft().elements.first { it.id == "night" }
            compose.onNodeWithTag("element_close").performTouchInput { swipe(center, center + Offset(-35f, -30f), 500) }
            compose.waitUntil(5000) { draft().elements.first { it.id == "close" }.x < initial.elements.first { it.id == "close" }.x }
            assertEquals(movedNight, draft().elements.first { it.id == "night" })

            listOf("close", "night").forEachIndexed { index, id ->
                compose.onNodeWithTag("select_$id").performScrollTo().performClick()
                compose.onNodeWithTag("remove_element").performScrollTo().performClick()
                compose.waitUntil(5000) { draft().elements.size == 5 - index }
            }
            compose.onNodeWithTag("select_battery").performScrollTo().performClick()
            compose.onNodeWithTag("slider_너비").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
            compose.waitUntil(5000) { draft().elements.first { it.id == "battery" }.width == 0.25f }
            val expected = draft()
            assertEquals(4, expected.elements.size)
            assertFalse(expected.elements.any { it.id == "close" || it.id == "night" })
            assertEquals(initial.font, expected.font)
            assertEquals(initial.background, expected.background)
            assertEquals(initial, settings.scene.first())

            // 임시 저장 복원 후에도 삭제된 요소가 돌아오지 않아야 한다.
            activity!!.close(); openEditor()
            compose.onNodeWithTag("element_close").assertDoesNotExist()
            compose.onNodeWithTag("element_night").assertDoesNotExist()
            assertEquals(expected, draft())
            compose.onNodeWithTag("save_scene").performClick()
            compose.waitUntil(5000) { runBlocking { settings.scene.first() == expected } }

            // 실제 저장을 끝내고 새 Activity로 열어도 모든 변경이 함께 유지되어야 한다.
            activity!!.close(); openEditor()
            compose.onNodeWithTag("element_close").assertDoesNotExist()
            compose.onNodeWithTag("element_night").assertDoesNotExist()
            assertEquals(expected, settings.scene.first())
            assertEquals(expected, draft())
        } finally {
            activity?.close()
            settings.saveScene(StandbyScene())
        }
    }

    @Test fun realEditorSavesReopensDiscardsAndAnimatesImportedMedia() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".validation")) { "개인용 앱에서는 통합 검사를 실행할 수 없습니다." }
        val settings = SettingsStore(context)
        val repo = MediaRepository(context)
        val sources = mutableListOf<File>()
        suspend fun import(name: String, gif: Boolean): String {
            val file = File(context.cacheDir, "${UUID.randomUUID()}-$name"); sources += file
            instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
            return repo.import(Uri.fromFile(file), gif)
        }
        var activity: ActivityScenario<EditorActivity>? = null
        try {
            val image = import("test-photo.png", false)
            val gif = import("test-motion.gif", true)
            val initial = StandbyScene(font = ClockFont.MAPLESTORY, background = BackgroundSpec(kind = BackgroundKind.PHOTO, media = image),
                elements = defaultElements() + newElement("test-gif", ElementKind.GIF, gif))
            settings.saveScene(initial)
            activity = ActivityScenario.launch(Intent(context, EditorActivity::class.java))
            compose.onNodeWithTag("font_KKUKKUKK").performScrollTo().performClick()
            compose.onNodeWithTag("save_scene").performClick()
            compose.waitUntil(5000) { runBlocking { settings.scene.first().font == ClockFont.KKUKKUKK } }
            // 실제 뒤로가기 버튼의 터치도 검증한다.
            compose.onNodeWithText("돌아가기").performTouchInput { click() }
            compose.waitUntil(5000) { activity!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            activity.close()
            activity = ActivityScenario.launch(Intent(context, EditorActivity::class.java))
            compose.onNodeWithTag("font_KKUKKUKK").assertExists()
            assertEquals(ClockFont.KKUKKUKK, settings.scene.first().font)
            assertEquals(image, settings.scene.first().background.media)
            assertEquals(gif, settings.scene.first().elements.last().media)

            // 원본을 지워도 앱 내부 복사본을 화면에서 계속 재생한다.
            sources.forEach { it.delete() }
            compose.onNodeWithTag("open_fullscreen").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("fullscreen_editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("fullscreen_clean_view").performClick()
            val colors = mutableSetOf<Int>()
            val folder = File(context.filesDir, "validation-0.3.0").apply { mkdirs() }
            repeat(5) { index ->
                Thread.sleep(180)
                val bitmap = compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap()
                colors += bitmap.getPixel(bitmap.width / 2, (bitmap.height * 0.45f).toInt())
                if (index < 2) File(folder, "photo-gif-frame-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            assertTrue("GIF가 실제 화면에서 두 가지 이상의 프레임으로 바뀌어야 합니다: $colors", colors.size >= 2)
            assertTrue(colors.all { it in setOf(Color.RED, Color.rgb(0, 128, 0), Color.BLUE) })

            compose.onNodeWithTag("show_fullscreen_tools").performClick()
            compose.onNodeWithTag("exit_fullscreen").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("font_ESAMANRU").performScrollTo().performClick()
            compose.onNodeWithText("돌아가기").performClick()
            compose.onNodeWithText("버리기").performClick()
            compose.waitUntil(5000) { activity!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(ClockFont.KKUKKUKK, settings.scene.first().font)
            assertEquals(settings.scene.first(), settings.editorScene.first())
        } finally {
            activity?.close()
            settings.saveScene(StandbyScene())
            repo.removeUnused(emptySet())
            sources.forEach { it.delete() }
        }
    }
}
