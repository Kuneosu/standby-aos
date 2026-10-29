package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private fun payload(key: String, privacy: NotificationPrivacy = NotificationPrivacy.PUBLIC, time: Long = 1L) =
        NotificationPayload(key, "example.app", "검사 앱", "제목 $key", "내용 $key", time, privacy)

    @Test fun upwardAndDownwardDragsChoosePresetAndLeftwardDragChoosesPanel() {
        assertEquals(StandbyDragAction.NEXT_PRESET, classifyStandbyDrag(2f, -30f, 6f, true))
        assertEquals(StandbyDragAction.NEXT_PRESET, classifyStandbyDrag(-2f, 30f, 6f, true))
        assertEquals(StandbyDragAction.OPEN_NOTIFICATIONS, classifyStandbyDrag(-30f, 2f, 6f, true))
    }

    @Test fun RightwardAndDisabledCycleDragsDoNotChangePresets() {
        assertEquals(StandbyDragAction.IGNORE, classifyStandbyDrag(30f, 0f, 6f, true))
        assertEquals(StandbyDragAction.IGNORE, classifyStandbyDrag(0f, -30f, 6f, false))
        assertEquals(StandbyDragAction.OPEN_NOTIFICATIONS, classifyStandbyDrag(-30f, 0f, 6f, false))
    }

    @Test fun SmallOrAmbiguousDiagonalMovementsWaitForDirection() {
        assertEquals(StandbyDragAction.WAITING, classifyStandbyDrag(-3f, 4f, 6f, true))
        assertEquals(StandbyDragAction.WAITING, classifyStandbyDrag(-20f, 20f, 6f, true))
    }

    @Test fun publicContentIsVisibleButSecretNotificationIsAbsent() {
        val visible = visibleNotifications(listOf(payload("public"), payload("secret", NotificationPrivacy.SECRET)))
        assertEquals(1, visible.size)
        assertEquals("제목 public", visible.single().title)
        assertEquals("내용 public", visible.single().text)
    }

    @Test fun privateOriginalNeverEntersDisplayedContent() {
        val entry = visibleNotifications(listOf(payload("private", NotificationPrivacy.PRIVATE))).single()
        assertTrue(entry.redacted)
        assertEquals("새 알림", entry.title)
        assertEquals("잠금 해제 후 내용을 확인하세요.", entry.text)
        assertFalse(entry.toString().contains("내용 private"))
    }

    @Test fun suppliedPublicVersionIsUsedInsteadOfPrivateOriginal() {
        val entry = visibleNotifications(listOf(payload("private", NotificationPrivacy.PRIVATE)
            .copy(publicTitle = "메시지가 도착했습니다", publicText = "공개용 안내"))).single()
        assertEquals("메시지가 도착했습니다", entry.title)
        assertEquals("공개용 안내", entry.text)
        assertFalse(entry.redacted)
    }

    @Test fun groupSummaryIsHiddenOnlyWhenThatAppsVisibleChildrenExist() {
        val entries = visibleNotifications(listOf(payload("summary").copy(groupKey = "group", groupSummary = true),
            payload("child").copy(groupKey = "group"), payload("other-summary").copy(packageName = "other.app", groupKey = "group", groupSummary = true)))
        assertEquals(setOf("child", "other-summary"), entries.map { it.key }.toSet())
    }

    @Test fun listIsBoundedDeduplicatedAndOrderedWithBoundedText() {
        val entries = visibleNotifications((0..120).map { payload("n$it", time = it.toLong()).copy(title = "가".repeat(200), text = "나".repeat(800)) } + payload("n120"))
        assertEquals(100, entries.size)
        assertEquals("n120", entries.first().key)
        assertEquals("n21", entries.last().key)
        assertEquals(160, entries.first().title.length)
        assertEquals(600, entries.first().text.length)
    }
}
