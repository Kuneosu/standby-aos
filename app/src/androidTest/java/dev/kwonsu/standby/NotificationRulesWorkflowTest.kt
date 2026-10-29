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

class NotificationRulesWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsStore(context)
    private fun rules() = runBlocking { settings.notificationRules.first() }
    private fun open(): ActivityScenario<NotificationSettingsActivity> {
        val activity = ActivityScenario.launch<NotificationSettingsActivity>(Intent(context, NotificationSettingsActivity::class.java))
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notification_rules_screen").fetchSemanticsNodes().isNotEmpty() }
        return activity
    }
    private fun choose(packageName: String, rule: NotificationRule) {
        compose.onNodeWithTag("notification_rule_list").performScrollToNode(hasTestTag("notification_rule_$packageName"))
        compose.onNodeWithTag("notification_content_$packageName").performClick()
        compose.onNodeWithTag("choose_rule_${rule.name}").performScrollTo().performClick()
        compose.waitUntil(5000) { rules().ruleFor(packageName) == rule }
    }

    @Test fun appRulesSaveImmediatelySurviveRecreationAndCanBeRestored() = runBlocking<Unit> {
        check(context.packageName.endsWith(".validation"))
        settings.setNotificationSelectionMode(false)
        settings.setNotificationRule("com.kakao.talk", NotificationRule.SHOW_CONTENT)
        settings.setNotificationRule("com.android.systemui", NotificationRule.HIDDEN)
        var activity: ActivityScenario<NotificationSettingsActivity>? = null
        try {
            activity = open()
            choose("com.kakao.talk", NotificationRule.HIDDEN)
            choose("com.android.systemui", NotificationRule.HIDE_CONTENT)
            activity.recreate()
            compose.onNodeWithTag("notification_app_search").performTextInput("카카오")
            compose.onNodeWithTag("notification_rule_com.kakao.talk").assertExists()
            compose.onNodeWithTag("notification_rule_com.android.systemui").assertDoesNotExist()
            choose("com.kakao.talk", NotificationRule.LOCK_SCREEN)
            activity.close(); activity = open()
            assertEquals(NotificationRule.LOCK_SCREEN, rules().ruleFor("com.kakao.talk"))
            assertEquals(NotificationRule.HIDE_CONTENT, rules().ruleFor("com.android.systemui"))
            choose("com.kakao.talk", NotificationRule.SHOW_CONTENT)
            choose("com.android.systemui", NotificationRule.HIDDEN)
            compose.onNodeWithTag("notification_app_search").performTextInput("카카오")
            val dir = File(context.filesDir, "validation-0.7.0").apply { mkdirs() }
            File(dir, "notification-rules.png").outputStream().use {
                compose.onNodeWithTag("notification_rules_screen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { activity?.close() }
    }

    @Test fun selectingAppsAndChangingModePreserveContentChoicesAcrossReopen() = runBlocking<Unit> {
        check(context.packageName.endsWith(".validation"))
        settings.setNotificationSelectionMode(false)
        settings.setNotificationRule("com.kakao.talk", NotificationRule.SHOW_CONTENT)
        settings.setNotificationRule("com.android.systemui", NotificationRule.HIDDEN)
        settings.setNotificationAppSelected("com.kakao.talk", true)
        var activity: ActivityScenario<NotificationSettingsActivity>? = null
        fun toggle(packageName: String, selected: Boolean) {
            compose.onNodeWithTag("notification_rule_list").performScrollToNode(hasTestTag("notification_rule_$packageName"))
            compose.onNodeWithTag("notification_rule_$packageName").performClick()
            compose.waitUntil(5000) { (packageName in rules().selectedApps) == selected }
        }
        try {
            activity = open()
            compose.onNodeWithTag("notification_mode_selected").performClick()
            compose.waitUntil(5000) { rules().selectedOnly }
            compose.onNodeWithTag("notification_rule_com.kakao.talk").assertIsOn()
            compose.onNodeWithTag("notification_rule_com.android.systemui").assertIsOff()
            toggle("com.kakao.talk", false)
            assertEquals(NotificationRule.SHOW_CONTENT, rules().storedRuleFor("com.kakao.talk"))
            toggle("com.kakao.talk", true)
            toggle("com.android.systemui", true)
            choose("com.android.systemui", NotificationRule.HIDE_CONTENT)
            compose.onNodeWithTag("notification_mode_all").performClick()
            compose.waitUntil(5000) { !rules().selectedOnly }
            compose.onNodeWithTag("notification_mode_selected").performClick()
            compose.waitUntil(5000) { rules().selectedOnly }
            activity.close(); activity = open()
            assertEquals(setOf("com.kakao.talk", "com.android.systemui"), rules().selectedApps)
            assertEquals(NotificationRule.HIDE_CONTENT, rules().ruleFor("com.android.systemui"))
            assertEquals(NotificationRule.SHOW_CONTENT, rules().ruleFor("com.kakao.talk"))
            toggle("com.android.systemui", false)
            compose.onNodeWithTag("notification_rule_com.kakao.talk").assertIsOn()
            compose.onNodeWithTag("notification_rule_com.android.systemui").assertIsOff()
            val dir = File(context.filesDir, "validation-0.7.1").apply { mkdirs() }
            File(dir, "selected-apps.png").outputStream().use {
                compose.onNodeWithTag("notification_rules_screen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { activity?.close(); settings.setNotificationSelectionMode(false) }
    }
}
