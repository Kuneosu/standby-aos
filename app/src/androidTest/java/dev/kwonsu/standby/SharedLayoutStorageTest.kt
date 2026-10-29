package dev.kwonsu.standby

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SharedLayoutStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun customSelectionAndEmptyLayoutSurviveDiskReopenAndLegacySelectionStaysExact() = runBlocking {
        val file = File(context.cacheDir, "shared-selection-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        val scene = StandbyScene(elements = listOf(newElement("clock", ElementKind.CLOCK), newElement("gif-1", ElementKind.GIF),
            newElement("gif-2", ElementKind.GIF).copy(x = 0.6f), newElement("shift", ElementKind.WORK_SHIFT), newElement("close", ElementKind.CLOSE_BUTTON)))
        try {
            val old = SharedLayoutCodec.decode("""{"placements":[{"kind":"CLOCK","occurrence":0,"x":0.1,"y":0.2,"width":0.4,"height":0.3}]}""")!!
            assertEquals(setOf("clock"), old.placementFor(scene).keys)
            val selected = SharedLayout.fromScene(scene, setOf("gif-2", "shift", "close"))
            val library = PresetLibrary(listOf(ScenePreset("p", "선택 배치", scene, true)), sharedLayout = selected)
            settings.updateLibrary { library }; settings.saveScene(scene, true)
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(library, settings.library.first())
            assertEquals(setOf("gif-2", "shift", "close"), settings.library.first().sharedLayout!!.placementFor(scene).keys)
            assertEquals(scene, settings.library.first().presets.single().scene)
            settings.updateLibrary { it.copy(sharedLayout = SharedLayout()) }
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(SharedLayout(), settings.library.first().sharedLayout)
            assertEquals(scene, settings.selectForEntry("empty").scene)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun legacyPresetsRemainUnlinkedAndSharedLayoutSurvivesDiskReopenWithoutFlattening() = runBlocking {
        val a = StandbyScene(font = ClockFont.MAPLESTORY, elements = listOf(newElement("a", ElementKind.CLOCK).copy(x = 0.1f)))
        val b = StandbyScene(font = ClockFont.KKUKKUKK, textColor = 0xFFFFAABB, background = BackgroundSpec(color = 0xFF223344),
            elements = listOf(newElement("b", ElementKind.CLOCK).copy(x = 0.3f, width = 0.3f, color = 0xFF112233),
                newElement("gif", ElementKind.GIF, "00000000-0000-0000-0000-000000000001.gif")))
        val legacy = JSONObject().put("version", 1).put("enabled", true).put("cycle", JSONArray(listOf("a", "b")))
            .put("presets", JSONArray().put(JSONObject().put("id", "a").put("name", "아침").put("scene", JSONObject(SceneCodec.encode(a))))
                .put(JSONObject().put("id", "b").put("name", "밤").put("scene", JSONObject(SceneCodec.encode(b)))))
        val decoded = PresetCodec.decode(legacy.toString())
        assertNull(decoded.sharedLayout); assertTrue(decoded.presets.none { it.useSharedLayout })
        assertEquals(listOf(a, b), decoded.presets.map { it.scene })
        assertEquals(decoded, PresetCodec.decode(PresetCodec.encode(decoded)))

        val file = File(context.cacheDir, "shared-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        try {
            settings.saveScene(b, useSharedLayout = true)
            settings.saveDraft(a, useSharedLayout = true, sourcePresetId = "a")
            val shared = SharedLayout.fromScene(a)
            settings.updateLibrary { decoded.copy(sharedLayout = shared, presets = decoded.presets.map { it.copy(useSharedLayout = true) }) }
            assertEquals(a, settings.selectForEntry("entry").scene)
            val switched = settings.selectForEntry("entry", advance = true)
            assertEquals("b", switched.presetId)
            assertEquals(shared.applyTo(b), switched.scene)
            assertEquals(b, settings.library.first().presets[1].scene)

            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(listOf(a, b), settings.library.first().presets.map { it.scene })
            assertEquals(shared, settings.library.first().sharedLayout)
            val snapshot = settings.editorSnapshot()
            assertEquals(a, snapshot.draft); assertEquals(b, snapshot.applied)
            assertTrue(snapshot.draftUsesSharedLayout); assertTrue(snapshot.appliedUsesSharedLayout)
            assertEquals("a", snapshot.sourcePresetId)

            val moved = shared.copy(placements = shared.placements.map { it.copy(x = 0.05f, width = 0.5f, positionLocked = true) })
            settings.updateLibrary { it.copy(sharedLayout = moved) }
            assertEquals(moved.applyTo(b), settings.selectForEntry("entry").scene)
            settings.updateLibrary { it.copy(presets = it.presets.map { p -> if (p.id == "b") p.copy(useSharedLayout = false) else p }) }
            assertEquals(b, settings.selectForEntry("entry").scene)
            assertEquals(a, settings.library.first().presets[0].scene)
            assertEquals(b, settings.library.first().presets[1].scene)

            settings.updateLibrary { it.copy(cycleEnabled = false) }
            assertEquals(moved.applyTo(b), settings.selectForEntry("current").scene)
            settings.discardDraft()
            assertTrue(settings.editorSnapshot().draftUsesSharedLayout)
            assertEquals(b, settings.editorSnapshot().draft)
            assertNull(settings.editorSnapshot().sourcePresetId)
            settings.saveScene(b, false)
            assertEquals(b, settings.selectForEntry("unlinked-current").scene)
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
