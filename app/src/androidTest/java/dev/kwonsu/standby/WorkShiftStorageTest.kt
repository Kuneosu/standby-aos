package dev.kwonsu.standby

import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class WorkShiftStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = LocalDateTime.of(2026, 10, 26, 8, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun correctedStartSurvivesDiskReopenAndRejectsStaleEditsAndCompletionCallbacks() = runBlocking {
        val file = File(context.cacheDir, "shift-correction-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun create() = WorkShiftStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var store = create()
        suspend fun reopen() { job.cancelAndJoin(); job = SupervisorJob(); store = create() }
        try {
            val original = start + 3_600_000
            val now = start + 7_200_000
            assertTrue(store.clockIn(now, original, zone))
            assertTrue(store.changeStart(now, start, expectedStart = original, zone = zone))
            val corrected = store.state.first()
            reopen()
            assertEquals(corrected, store.state.first())
            assertEquals(start, corrected.startedAt)
            assertEquals(start + WorkShift.WORK_MILLIS, corrected.deadline)
            assertEquals(22, corrected.display(now).percent)
            assertFalse(store.changeStart(now, original, expectedStart = original, zone = zone))
            assertTrue(store.changeStart(now, start, expectedStart = start, zone = zone))
            assertEquals(corrected, store.state.first())
            assertFalse(store.autoComplete(original + WorkShift.WORK_MILLIS, expectedStart = original))
            assertFalse(store.claimCelebration(original + WorkShift.WORK_MILLIS, expectedStart = original))
            assertEquals(corrected, store.state.first())
            assertTrue(store.autoComplete(corrected.deadline, expectedStart = start))
            assertTrue(store.claimCelebration(corrected.deadline, expectedStart = start))
            val completed = store.state.first()
            reopen()
            assertEquals(completed, store.state.first())
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun finishingAnOldAnimationCannotConsumeTheNextShiftCelebration() = runBlocking {
        val file = File(context.cacheDir, "shift-token-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = WorkShiftStore(context, PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        try {
            val end = start + WorkShift.WORK_MILLIS
            store.clockIn(start, start, zone)
            store.autoComplete(end, expectedStart = start)
            val next = start + 86_400_000
            store.clockIn(next, next, zone)
            assertFalse(store.claimCelebration(next + WorkShift.WORK_MILLIS, expectedStart = start))
            assertFalse(store.state.first().celebrated)
            assertTrue(store.claimCelebration(next + WorkShift.WORK_MILLIS, expectedStart = next))
            assertTrue(store.state.first().celebrated)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun selectedTimeCompletionAndCooldownSurviveDiskReopen() = runBlocking {
        val file = File(context.cacheDir, "shift-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun create() = WorkShiftStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var store = create()
        suspend fun reopen() { job.cancelAndJoin(); job = SupervisorJob(); store = create() }
        try {
            assertTrue(store.clockIn(start + 7_200_000, start, zone))
            reopen()
            assertEquals(start, store.state.first().startedAt)
            assertEquals(start + WorkShift.WORK_MILLIS, store.state.first().deadline)
            assertEquals(22, store.state.first().display(start + 7_200_000).percent)
            assertFalse(store.autoComplete(start, expectedStart = start))
            val end = start + WorkShift.WORK_MILLIS
            assertTrue(store.autoComplete(end + 3_600_000, expectedStart = start))
            assertEquals(end, store.state.first().checkedOutAt)
            assertEquals(1, coroutineScope { List(5) { async { store.claimCelebration(end) } }.awaitAll().count { it } })
            reopen()
            assertTrue(store.state.first().celebrated)
            assertFalse(store.claimCelebration(end))
            assertFalse(store.autoComplete(end, expectedStart = start))
            reopen()
            assertEquals(WorkShiftPhase.COOLDOWN, store.state.first().display(end + WorkShift.REST_MILLIS - 1).phase)
            assertFalse(store.clockIn(end + WorkShift.REST_MILLIS - 1, start, zone))
            assertEquals(WorkShiftPhase.READY, store.state.first().display(end + WorkShift.REST_MILLIS).phase)
            val next = start + 86_400_000
            assertEquals(1, coroutineScope { List(5) { async { store.clockIn(next, next, zone) } }.awaitAll().count { it } })
            assertFalse(store.state.first().celebrated)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun legacyManualCheckoutIsMigratedOnReadAndStaysReadyAfterDiskReopen() = runBlocking {
        val file = File(context.cacheDir, "shift-legacy-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun preferences() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        var data = preferences()
        try {
            data.edit {
                it[longPreferencesKey("started_at")] = 0L
                it[longPreferencesKey("deadline")] = 0L
                it[longPreferencesKey("checked_out_at")] = start + 86_400_000
                it[booleanPreferencesKey("celebrated")] = false
                it[stringPreferencesKey("unrelated")] = "보존"
            }
            assertEquals(WorkShift(), WorkShiftStore(context, data).state.first())
            assertEquals("보존", data.data.first()[stringPreferencesKey("unrelated")])
            job.cancelAndJoin(); job = SupervisorJob(); data = preferences()
            val store = WorkShiftStore(context, data)
            assertEquals(WorkShiftPhase.READY, store.state.first().display(start + 86_400_000).phase)
            assertEquals(0L, data.data.first()[longPreferencesKey("checked_out_at")])
            assertTrue(store.clockIn(start + 86_400_000, start + 86_400_000, zone))
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun bothOrientationsPersistInSceneAndPresetAndLegacyDefaultsToHorizontal() = runBlocking {
        val file = File(context.cacheDir, "shift-scene-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun create() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = create()
        try {
            val scene = StandbyScene(elements = listOf(newElement("h", ElementKind.WORK_SHIFT).copy(color = 0xFFFF9F43),
                newElement("v", ElementKind.WORK_SHIFT).withGaugeOrientation(GaugeOrientation.VERTICAL).copy(color = 0xFFD0B0FF)))
            val library = PresetLibrary(presets = listOf(ScenePreset("work", "출퇴근", scene)))
            settings.saveScene(scene); settings.updateLibrary { library }
            job.cancelAndJoin(); job = SupervisorJob(); settings = create()
            assertEquals(scene, settings.editorSnapshot().applied)
            assertEquals(library, settings.library.first())
            val encoded = SceneCodec.encode(scene)
            val legacy = encoded.replace("\"gaugeOrientation\":\"VERTICAL\"", "\"unused\":true")
            assertEquals(GaugeOrientation.HORIZONTAL, SceneCodec.decode(legacy).elements.last().gaugeOrientation)
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
