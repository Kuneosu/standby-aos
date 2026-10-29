package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class NotificationRulesTest {
    private fun payload(rule: NotificationRule, privacy: NotificationPrivacy = NotificationPrivacy.PRIVATE) =
        NotificationPayload("key", "sample.app", "검사 앱", "메시지 제목", "메시지 원문", 1L, privacy,
            publicTitle = "공개용 안내", publicText = "공개용 본문", rule = rule)

    @Test fun requestedDefaultsAndUnknownAppPolicyAreSeparate() {
        val rules = NotificationRules()
        assertEquals(NotificationRule.HIDDEN, rules.ruleFor("com.android.systemui"))
        assertEquals(NotificationRule.SHOW_CONTENT, rules.ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.LOCK_SCREEN, rules.ruleFor("another.app"))
    }

    @Test fun explicitUserChoicesOverrideInitialExceptions() {
        val original = NotificationRules()
        val changed = original.withRule("com.kakao.talk", NotificationRule.HIDDEN).withRule("com.android.systemui", NotificationRule.LOCK_SCREEN)
        assertEquals(NotificationRule.HIDDEN, changed.ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.LOCK_SCREEN, changed.ruleFor("com.android.systemui"))
        assertEquals(NotificationRule.SHOW_CONTENT, original.ruleFor("com.kakao.talk"))
    }

    @Test fun hiddenAppNeverEntersThePanelEvenWhenPublic() {
        assertTrue(visibleNotifications(listOf(payload(NotificationRule.HIDDEN, NotificationPrivacy.PUBLIC))).isEmpty())
    }

    @Test fun showContentUsesDeliveredOriginalInsteadOfPublicVersion() {
        val entry = visibleNotifications(listOf(payload(NotificationRule.SHOW_CONTENT))).single()
        assertEquals("메시지 제목", entry.title); assertEquals("메시지 원문", entry.text); assertFalse(entry.redacted)
    }

    @Test fun explicitStandbyRuleCanDisplayDeliveredContentRegardlessOfLockscreenMetadata() {
        val entry = visibleNotifications(listOf(payload(NotificationRule.SHOW_CONTENT, NotificationPrivacy.SECRET))).single()
        assertEquals("메시지 원문", entry.text)
        assertTrue(visibleNotifications(listOf(payload(NotificationRule.LOCK_SCREEN, NotificationPrivacy.SECRET))).isEmpty())
    }

    @Test fun hideContentDoesNotLeakPublicOrOriginalText() {
        val entry = visibleNotifications(listOf(payload(NotificationRule.HIDE_CONTENT, NotificationPrivacy.PUBLIC))).single()
        assertEquals("새 알림", entry.title); assertEquals("이 앱의 내용을 숨겼습니다.", entry.text); assertTrue(entry.redacted)
        assertFalse(entry.toString().contains("메시지 원문")); assertFalse(entry.toString().contains("공개용"))
    }

    @Test fun returningToLockscreenPolicyRestoresRedaction() {
        val source = payload(NotificationRule.SHOW_CONTENT).copy(publicTitle = null, publicText = null)
        assertEquals("메시지 원문", visibleNotifications(listOf(source)).single().text)
        assertEquals("잠금 해제 후 내용을 확인하세요.", visibleNotifications(listOf(source.copy(rule = NotificationRule.LOCK_SCREEN))).single().text)
    }

    @Test fun selectedOnlyHidesEveryUnselectedAppIncludingExplicitContentRules() {
        val rules = NotificationRules(selectedOnly = true).withRule("calendar.app", NotificationRule.SHOW_CONTENT)
            .withAppSelected("calendar.app", false)
        assertEquals(NotificationRule.SHOW_CONTENT, rules.ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.HIDDEN, rules.ruleFor("calendar.app"))
        assertEquals(NotificationRule.HIDDEN, rules.ruleFor("new.app"))
        assertEquals(NotificationRule.HIDDEN, rules.ruleFor("com.android.systemui"))
    }

    @Test fun selectionTogglePreservesContentChoiceAndAllModeRestoresDefaultPolicy() {
        val rules = NotificationRules(selectedOnly = true).withAppSelected("com.kakao.talk", false)
        assertEquals(NotificationRule.HIDDEN, rules.ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.SHOW_CONTENT, rules.storedRuleFor("com.kakao.talk"))
        assertEquals(NotificationRule.SHOW_CONTENT, rules.withAppSelected("com.kakao.talk", true).ruleFor("com.kakao.talk"))
        assertEquals(NotificationRule.LOCK_SCREEN, rules.copy(selectedOnly = false).ruleFor("new.app"))
    }

    @Test fun emptySelectionShowsNothingAndSelectingPreviouslyHiddenAppEnablesIt() {
        val empty = NotificationRules(selectedOnly = true, selectedApps = emptySet())
        assertEquals(NotificationRule.HIDDEN, empty.ruleFor("com.kakao.talk"))
        val enabled = empty.withAppSelected("com.android.systemui", true)
        assertEquals(NotificationRule.LOCK_SCREEN, enabled.ruleFor("com.android.systemui"))
        assertEquals(NotificationRule.HIDDEN, enabled.withRule("com.android.systemui", NotificationRule.HIDDEN).ruleFor("com.android.systemui"))
    }

    @Test fun editingContentWhileSelectedOnlyIncludesThatAppAndPreservesOthers() {
        val rules = NotificationRules(selectedOnly = true).withRule("calendar.app", NotificationRule.HIDE_CONTENT)
        assertEquals(setOf("com.kakao.talk", "calendar.app"), rules.selectedApps)
        assertEquals(NotificationRule.HIDE_CONTENT, rules.ruleFor("calendar.app"))
        assertEquals(NotificationRule.SHOW_CONTENT, rules.ruleFor("com.kakao.talk"))
    }
}
