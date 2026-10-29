package dev.kwonsu.standby

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NotificationRulesStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rulesPersistAcrossDiskReopenAndDoNotChangeExistingDesign() = runBlocking {
        val file = File(context.cacheDir, "notification-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun store() = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        var settings = store()
        try {
            val scene = StandbyScene(font = ClockFont.MAPLESTORY, background = BackgroundSpec(color = 0xFF223344))
            val library = PresetLibrary(presets = listOf(ScenePreset("one", "보존", scene)), cycleIds = listOf("one"), cycleEnabled = true)
            settings.saveScene(scene); settings.saveDraft(scene.copy(textColor = 0xFFFABBBB)); settings.updateLibrary { library }
            settings.setNightMode(true); settings.setBrightness(0.4f, false)
            val before = settings.editorSnapshot()
            assertEquals(NotificationRules(), settings.notificationRules.first())
            settings.setNotificationRule("com.kakao.talk", NotificationRule.HIDDEN)
            settings.setNotificationRule("com.android.systemui", NotificationRule.LOCK_SCREEN)
            settings.setNotificationRule("sample.app", NotificationRule.HIDE_CONTENT)
            settings.setNotificationAppSelected("sample.app", true)
            settings.setNotificationSelectionMode(true)
            job.cancelAndJoin(); job = SupervisorJob(); settings = store()
            assertEquals(NotificationRule.HIDDEN, settings.notificationRules.first().ruleFor("com.kakao.talk"))
            assertTrue(settings.notificationRules.first().selectedOnly)
            assertEquals(setOf("sample.app"), settings.notificationRules.first().selectedApps)
            assertEquals(NotificationRule.HIDDEN, settings.notificationRules.first().ruleFor("com.android.systemui"))
            assertEquals(NotificationRule.LOCK_SCREEN, settings.notificationRules.first().storedRuleFor("com.android.systemui"))
            assertEquals(NotificationRule.HIDE_CONTENT, settings.notificationRules.first().ruleFor("sample.app"))
            assertEquals(before, settings.editorSnapshot()); assertEquals(library, settings.library.first())
            assertTrue(settings.nightMode.first()); assertEquals(0.4f, settings.brightness.first().day!!, 0.0001f)
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun missingRulesUseRequestedDefaultsButMalformedSavedRulesNeverEnableContent() {
        assertEquals(NotificationRules(), NotificationRulesCodec.decode(null))
        val rules = NotificationRules(emptyMap()).withRule("com.kakao.talk", NotificationRule.LOCK_SCREEN)
        assertEquals(rules, NotificationRulesCodec.decode(NotificationRulesCodec.encode(rules)))
        assertEquals(NotificationRule.LOCK_SCREEN, NotificationRulesCodec.decode("잘못된 JSON").ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.LOCK_SCREEN, NotificationRulesCodec.decode("{\"apps\":{\"com.kakao.talk\":\"unknown\"}}").ruleFor("com.kakao.talk"))
        val legacy = NotificationRulesCodec.decode("{\"version\":1,\"apps\":{\"com.kakao.talk\":\"SHOW_CONTENT\"}}")
        assertFalse(legacy.selectedOnly)
        assertEquals(NotificationRule.SHOW_CONTENT, legacy.ruleFor("com.kakao.talk"))
        val emptySelected = NotificationRulesCodec.decode("{\"apps\":{},\"selectedOnly\":true}")
        assertTrue(emptySelected.selectedApps.isEmpty())
        assertEquals(NotificationRule.HIDDEN, emptySelected.ruleFor("sample.app"))
    }
}
