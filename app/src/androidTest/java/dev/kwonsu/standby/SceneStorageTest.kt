@file:Suppress("DEPRECATION")

package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SceneStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun everySettingSurvivesDiskReloadAndDraftDoesNotApplyUntilSave() = runBlocking {
        val file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        val expected = StandbyScene(font = ClockFont.KKUKKUKK, textColor = 0xFF99CCFF,
            background = BackgroundSpec(kind = BackgroundKind.PHOTO, media = "d6b71c24-e10f-465a-aa61-408d270787b3.img",
                fit = PhotoFit.STRETCH, zoom = 1.8f, panX = -0.3f, panY = 0.2f, dim = 0.4f, widthScale = 1.4f, heightScale = 0.7f),
            elements = listOf(SceneElement("motion", ElementKind.GIF, 0.1f, 0.2f, 0.4f, 0.3f,
                media = "d6b71c24-e10f-465a-aa61-408d270787b3.gif", speed = 2.25f, loop = false),
                SceneElement("clock", ElementKind.CLOCK, color = 0xFFFF5577, textAlignment = ElementTextAlignment.RIGHT, textSize = 0.22f)))
        try {
            settings.saveDraft(expected)
            assertEquals(StandbyScene(), settings.scene.first())
            assertEquals(expected, settings.editorScene.first())
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(expected, settings.editorScene.first())
            settings.saveScene(expected)
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(expected, settings.scene.first())
            assertEquals(expected, settings.editorScene.first())
            settings.saveDraft(StandbyScene(elements = emptyList()))
            settings.discardDraft()
            assertEquals(expected, settings.editorScene.first())
            settings.saveScene(StandbyScene(elements = emptyList()))
            assertTrue(settings.scene.first().elements.isEmpty())
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun unknownOrCorruptSettingsRecoverWithoutCrash() {
        assertEquals(StandbyScene(), SceneCodec.decode("broken"))
        assertEquals(ClockFont.SYSTEM, SceneCodec.decode("{\"font\":\"unknown\"}").font)
        assertTrue(SceneCodec.decode("{\"elements\":[{\"kind\":\"FUTURE\"}]}").elements.isEmpty())
        val legacy = SceneCodec.decode("{\"elements\":[{\"id\":\"clock\",\"kind\":\"CLOCK\"}]}").elements.single()
        assertEquals(ElementTextAlignment.CENTER, legacy.textAlignment)
        assertNull(legacy.textSize)
        assertEquals(ElementTextAlignment.CENTER, SceneCodec.decode("{\"elements\":[{\"kind\":\"CLOCK\",\"textAlignment\":\"unknown\"}]}").elements.single().textAlignment)
    }

    @Test fun importedFilesRemainReadableAfterSourceRemovalAndInvalidGifIsRejected() = runBlocking {
        val repo = MediaRepository(context)
        val originals = mutableListOf<File>()
        val copies = mutableListOf<File>()
        fun fixture(name: String): File = File(context.cacheDir, "test-${UUID.randomUUID()}-$name").also { file ->
            instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }; originals += file
        }
        try {
            val image = fixture("test-photo.png")
            val gif = fixture("test-motion.gif")
            val imageCopy = repo.file(repo.import(Uri.fromFile(image), false))!!; copies += imageCopy
            val gifCopy = repo.file(repo.import(Uri.fromFile(gif), true))!!; copies += gifCopy
            image.delete(); gif.delete()
            val bitmap = MediaRepository.decodePhoto(imageCopy)
            assertEquals(320, bitmap.width); bitmap.recycle()
            val movie = Movie.decodeFile(gifCopy.path)!!
            assertEquals(900, movie.duration())
            assertEquals(80, movie.width())
            val wrong = fixture("test-photo.png")
            try { repo.import(Uri.fromFile(wrong), true); fail("PNG를 GIF로 가져오면 거부되어야 합니다.") } catch (_: IllegalArgumentException) { }
        } finally { originals.forEach { it.delete() }; copies.forEach { it.delete() } }
    }

    @Test fun actualGifPixelsAdvanceAndOneShotHoldsLastFrame() {
        val bytes = instrumentation.context.assets.open("test-motion.gif").use { it.readBytes() }
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)!!
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun at(elapsed: Long, speed: Float, loop: Boolean): Int {
            bitmap.eraseColor(Color.TRANSPARENT)
            movie.setTime(gifFrame(elapsed, movie.duration(), speed, loop).timeMillis)
            movie.draw(canvas, 0f, 0f)
            return bitmap.getPixel(40, 30)
        }
        try {
            assertEquals(Color.RED, at(0, 1f, true))
            assertEquals(Color.rgb(0, 128, 0), at(350, 1f, true))
            assertEquals(Color.BLUE, at(350, 2f, true))
            assertEquals(Color.RED, at(350, 0.25f, true))
            assertEquals(Color.BLUE, at(1000, 1f, false))
            assertEquals(Color.BLUE, at(5000, 1f, false))
            assertEquals(Color.RED, at(1000, 1f, true))
        } finally { bitmap.recycle() }
    }
}
