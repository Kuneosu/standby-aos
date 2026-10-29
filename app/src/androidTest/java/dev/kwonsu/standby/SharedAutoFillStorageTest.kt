package dev.kwonsu.standby

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SharedAutoFillStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun legacySelectionCopiesGifAndSecondGaugeAndKeepsMediaAfterSourceDeletionAndDiskReopen() = runBlocking {
        check(context.packageName.endsWith(".validation"))
        val file = File(context.cacheDir, "autofill-${UUID.randomUUID()}.preferences_pb")
        val source = File(context.cacheDir, "autofill-${UUID.randomUUID()}.gif")
        var job = SupervisorJob()
        var preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        var settings = SettingsStore(context, preferences)
        val repo = MediaRepository(context)
        var media: String? = null
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("test-motion.gif").use { input -> source.outputStream().use { input.copyTo(it) } }
            media = repo.import(Uri.fromFile(source), true)
            val first = newElement("shift-1", ElementKind.WORK_SHIFT)
            val second = first.copy(id = "shift-2", color = 0xFFFF8844, gaugeOrientation = GaugeOrientation.VERTICAL)
            val original = StandbyScene(elements = listOf(first, second, newElement("gif", ElementKind.GIF, media).copy(speed = 1.75f, loop = false)))
            val target = StandbyScene(elements = (0 until 20).map { newElement("clock-$it", ElementKind.CLOCK) })
            val selected = SharedLayout.fromScene(original, setOf("shift-2", "gif"))
            val legacy = selected.copy(placements = selected.placements.map { it.copy(template = null) })
            settings.saveScene(original)
            preferences.edit { it[stringPreferencesKey("presets_v1")] = PresetCodec.encode(PresetLibrary(
                listOf(ScenePreset("source", "원본", original), ScenePreset("target", "연결", target, true), ScenePreset("off", "개별", target)),
                listOf("target"), true, legacy)) }
            val hydrated = settings.library.first()
            val filled = hydrated.presets[1].scene
            assertEquals(22, filled.elements.size)
            assertEquals(target.elements, filled.elements.take(20))
            assertEquals(target, hydrated.presets[2].scene)
            val gauge = filled.elements.single { it.kind == ElementKind.WORK_SHIFT }
            assertEquals(LayoutSlot(ElementKind.WORK_SHIFT, 1), gauge.sharedSlot)
            assertEquals(GaugeOrientation.VERTICAL, gauge.gaugeOrientation); assertEquals(0xFFFF8844, gauge.color)
            val gif = filled.elements.single { it.kind == ElementKind.GIF }
            assertEquals(media, gif.media); assertEquals(1.75f, gif.speed); assertFalse(gif.loop)
            assertEquals(filled, settings.selectForEntry("entry").scene)
            settings.saveDraft(StandbyScene(elements = emptyList()))
            settings.updateLibrary { it.copy(presets = it.presets.filterNot { preset -> preset.id == "source" }) }
            settings.saveScene(StandbyScene(elements = emptyList())); settings.discardDraft()
            repo.removeUnused(settings.retainedMediaNames())
            assertTrue(repo.file(media)!!.exists())
            job.cancelAndJoin(); job = SupervisorJob()
            preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            settings = SettingsStore(context, preferences)
            assertEquals(filled, settings.library.first().presets.first().scene)
            assertEquals(filled, settings.selectForEntry("entry").scene)
            assertTrue(settings.library.first().sharedLayout!!.placements.all { it.template != null })
            settings.updateLibrary { it.copy(presets = it.presets.map { p -> p.copy(useSharedLayout = false) }) }
            assertEquals(filled, settings.library.first().presets.first().scene)
            settings.updateLibrary { it.copy(presets = emptyList()) }
            repo.removeUnused(settings.retainedMediaNames())
            assertTrue("공통 기준만 참조해도 GIF를 유지", repo.file(media)!!.exists())
            settings.updateLibrary { PresetLibrary() }
            repo.removeUnused(settings.retainedMediaNames())
            assertFalse(repo.file(media)!!.exists())
        } finally { job.cancelAndJoin(); file.delete(); source.delete(); repo.file(media)?.delete() }
    }

    @Test fun exceedingCapacityRejectsWholeUpdateWithoutLosingExistingElements() = runBlocking {
        val file = File(context.cacheDir, "autofill-capacity-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val settings = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        val full = StandbyScene(elements = (0 until 20).map { newElement("clock-$it", ElementKind.CLOCK) } +
            (0 until 20).map { newElement("date-$it", ElementKind.DATE).copy(sharedSlot = LayoutSlot(ElementKind.DATE, it)) })
        val initial = PresetLibrary(listOf(ScenePreset("full", "가득 찬 화면", full)))
        try {
            settings.updateLibrary { initial }
            val shared = SharedLayout.fromScene(StandbyScene(elements = listOf(newElement("shift", ElementKind.WORK_SHIFT))))
            assertTrue(runCatching { settings.updateLibrary { it.copy(sharedLayout = shared, presets = it.presets.map { p -> p.copy(useSharedLayout = true) }) } }.isFailure)
            assertEquals(initial, settings.library.first())
            assertEquals(40, settings.library.first().presets.single().scene.elements.size)
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
