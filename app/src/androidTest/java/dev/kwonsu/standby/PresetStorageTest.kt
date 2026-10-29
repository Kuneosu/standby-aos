package dev.kwonsu.standby

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PresetStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun currentAndSelectedPresetSaveAtomicallyAndSelectionSurvivesReopen() = runBlocking {
        val file = File(context.cacheDir, "preset-save-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        val original = StandbyScene(font = ClockFont.MAPLESTORY)
        val other = ScenePreset("other", "그대로", StandbyScene())
        val shared = SharedLayout.fromScene(original, setOf("clock"))
        val edited = original.copy(font = ClockFont.KKUKKUKK)
        try {
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("p", "선택", original, true), other), listOf("p", "other"), true, shared) }
            settings.saveScene(edited, true, "p", updatePreset = true)
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals("p", settings.editorSnapshot().sourcePresetId)
            assertEquals(edited, settings.editorSnapshot().applied)
            assertEquals(edited, settings.library.first().presets.first().scene)
            assertEquals(other, settings.library.first().presets.last())
            assertEquals(shared, settings.library.first().sharedLayout)
            assertEquals(listOf("p", "other"), settings.library.first().cycleIds)
            assertTrue(settings.library.first().presets.first().useSharedLayout)

            val currentOnly = edited.copy(textColor = 0xFF123456)
            settings.saveScene(currentOnly, false, "p")
            assertEquals(edited, settings.library.first().presets.first().scene)
            assertTrue(settings.library.first().presets.first().useSharedLayout)
            settings.saveDraft(original, false, "other"); settings.discardDraft()
            assertEquals(currentOnly, settings.editorSnapshot().draft)
            assertEquals("p", settings.editorSnapshot().sourcePresetId)

            settings.updateLibrary { it.copy(presets = listOf(other)) }
            assertNull(settings.editorSnapshot().sourcePresetId)
            assertTrue(runCatching { settings.saveScene(original, true, "p", updatePreset = true) }.isFailure)
            assertEquals(currentOnly, settings.editorSnapshot().applied)
            assertEquals(listOf(other), settings.library.first().presets)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun selectionSurvivesRecreationAndOnlyAdvancesOnNewEntryOrSwipe() = runBlocking {
        val file = File(context.cacheDir, "cycle-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        val original = StandbyScene(font = ClockFont.KKUKKUKK)
        try {
            settings.saveScene(original)
            settings.updateLibrary { PresetLibrary(listOf("a", "b", "c").map { ScenePreset(it, it, StandbyScene()) }, listOf("a", "c"), true) }
            assertEquals("a", settings.selectForEntry("one").presetId)
            assertEquals("a", settings.selectForEntry("one").presetId)
            assertEquals("c", settings.selectForEntry("one", advance = true).presetId)
            settings.setBrightness(0.7f, false); settings.setBrightness(0.12f, true)
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals("c", settings.selectForEntry("one").presetId)
            assertEquals(BrightnessSettings(0.7f, 0.12f), settings.brightness.first())
            assertEquals("a", settings.selectForEntry("two").presetId)
            settings.updateLibrary { it.copy(presets = it.presets.filterNot { p -> p.id == "a" }) }
            assertEquals(listOf("c"), settings.library.first().cycleIds)
            assertEquals("c", settings.selectForEntry("three").presetId)
            settings.updateLibrary { it.copy(cycleEnabled = false) }
            assertEquals(original, settings.selectForEntry("four").scene)
            settings.updateLibrary { it.copy(cycleEnabled = true, cycleIds = emptyList()) }
            assertEquals(original, settings.selectForEntry("five").scene)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun inactivePresetsKeepMediaThroughSaveAndDiscardCleanup() = runBlocking {
        val file = File(context.cacheDir, "media-cycle-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val settings = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        val repo = MediaRepository(context)
        val source = File(context.cacheDir, "source-${UUID.randomUUID()}.png")
        val names = mutableListOf<String>()
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("test-photo.png").use { input -> source.outputStream().use { input.copyTo(it) } }
            repeat(3) { names += repo.import(Uri.fromFile(source), false) }
            fun scene(name: String) = StandbyScene(background = BackgroundSpec(kind = BackgroundKind.PHOTO, media = name))
            settings.saveScene(scene(names[0])); settings.saveDraft(scene(names[1]))
            settings.updateLibrary { PresetLibrary(listOf(ScenePreset("excluded", "순환에서 제외", scene(names[2])))) }
            repo.removeUnused(settings.retainedMediaNames())
            assertTrue(names.all { repo.file(it)!!.exists() })
            settings.discardDraft(); repo.removeUnused(settings.retainedMediaNames())
            assertFalse(repo.file(names[1])!!.exists()); assertTrue(repo.file(names[2])!!.exists())
            settings.saveScene(StandbyScene()); repo.removeUnused(settings.retainedMediaNames())
            assertFalse(repo.file(names[0])!!.exists()); assertTrue(repo.file(names[2])!!.exists())
            settings.updateLibrary { PresetLibrary() }; repo.removeUnused(settings.retainedMediaNames())
            assertFalse(repo.file(names[2])!!.exists())
        } finally { names.forEach { repo.file(it)?.delete() }; source.delete(); job.cancelAndJoin(); file.delete() }
    }

    @Test fun lockedBrightnessElementAndOlderDesignsDecodeCorrectly() {
        val scene = StandbyScene(elements = listOf(newElement("brightness", ElementKind.BRIGHTNESS).copy(positionLocked = true)))
        assertEquals(scene, SceneCodec.decode(SceneCodec.encode(scene)))
        val old = SceneCodec.decode("{\"elements\":[{\"id\":\"old\",\"kind\":\"CLOCK\"}]}")
        assertFalse(old.elements.single().positionLocked)
        val library = PresetLibrary(listOf(ScenePreset("p", "유리 시계", scene)), listOf("p"), true)
        assertEquals(library, PresetCodec.decode(PresetCodec.encode(library)))
    }
}
