package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryTest {
    private val rules = NotificationRules(emptyMap())
    private fun arrival(id: String, text: String = id, time: Long = id.filter(Char::isDigit).toLongOrNull() ?: 1L, signature: String? = null) =
        NotificationArrival(id, "same-source", NotificationPayload("same-source", "app", "검사 앱", "제목", text, time, NotificationPrivacy.PUBLIC), signature)

    @Test fun sameSystemIdAccumulatesDifferentContentsAndSystemRemovalKeepsHistory() {
        val history = NotificationHistory().receive(listOf(arrival("1", signature = "one")), 1)
            .receive(listOf(arrival("2", signature = "two")), 2).sourceRemoved("same-source")
        assertEquals(listOf("2", "1"), history.visible(rules).map { it.text })
    }
    @Test fun unchangedGenericUpdatesDoNotAccumulateButRepostedAfterRemovalDoes() {
        val first = NotificationHistory().receive(listOf(arrival("1", "같은 내용", signature = "same")), 1)
        val updated = first.receive(listOf(arrival("2", "같은 내용", signature = "same")), 2)
        assertEquals(1, updated.records.size)
        assertEquals(2, updated.sourceRemoved("same-source").receive(listOf(arrival("3", "같은 내용", signature = "same")), 3).records.size)
    }
    @Test fun repeatedMessageBundlesAppendOnlyNewMessages() {
        val history = NotificationHistory().receive(listOf(arrival("1"), arrival("2")), 2)
            .receive(listOf(arrival("1"), arrival("2"), arrival("3")), 3)
        assertEquals(listOf("3", "2", "1"), history.visible(rules).map { it.key })
    }
    @Test fun dismissedEntryDoesNotReturnWhenItsActiveNotificationIsSeenAgain() {
        val history = NotificationHistory().receive(listOf(arrival("1"), arrival("2")), 2).dismiss("2")
            .receive(listOf(arrival("1"), arrival("2"), arrival("3")), 3)
        assertEquals(listOf("3", "1"), history.visible(rules).map { it.key })
    }
    @Test fun bulkDismissKeepsOtherAppsAndMessagesThatArrivedAfterTheTap() {
        val other = arrival("3").let { it.copy(payload = it.payload.copy(packageName = "other")) }
        val history = NotificationHistory().receive(listOf(arrival("1"), arrival("2"), other, arrival("4")), 4)
        val cleared = history.dismissMany(setOf("1", "2"))
        assertEquals(listOf("4", "3"), cleared.records.map { it.id })
        assertEquals(history.seenIds, cleared.seenIds)
        assertEquals(cleared.records, cleared.receive(listOf(arrival("1"), arrival("2")), 5).records)
    }
    @Test fun clearingVisibleHistoryLeavesFilteredRecordsAndNeverBlocksNewArrivals() {
        val other = arrival("3").let { it.copy(payload = it.payload.copy(packageName = "other")) }
        val history = NotificationHistory().receive(listOf(arrival("1"), arrival("2"), other), 3)
        val visible = history.visible(rules.withRule("other", NotificationRule.HIDDEN)).map { it.key }.toSet()
        val cleared = history.dismissMany(visible)
        assertEquals(listOf("3"), cleared.records.map { it.id })
        val allCleared = cleared.dismissMany(setOf("3"))
        assertTrue(allCleared.records.isEmpty())
        assertEquals(listOf("4"), allCleared.receive(listOf(arrival("1"), arrival("2"), other, arrival("4")), 4).records.map { it.id })
    }
    @Test fun recordAndDedupeStorageAreBoundedWithoutBringingOldEntriesBack() {
        val history = NotificationHistory().receive((1..5100).map { arrival(it.toString()) }, 6000)
        assertEquals(500, history.records.size); assertEquals(5000, history.seenIds.size)
        assertEquals(5100L, history.records.first().payload.postedAt)
        assertEquals(history.records, history.receive(listOf(arrival("4500")), 7000).records)
    }
    @Test fun currentAppRulesFilterAndRedactExistingHistory() {
        val source = arrival("1", "비공개 내용").let { it.copy(payload = it.payload.copy(privacy = NotificationPrivacy.PRIVATE, rule = NotificationRule.SHOW_CONTENT)) }
        val history = NotificationHistory().receive(listOf(source), 1)
        assertEquals("비공개 내용", history.visible(rules.withRule("app", NotificationRule.SHOW_CONTENT)).single().text)
        assertTrue(history.visible(rules.withRule("app", NotificationRule.HIDDEN)).isEmpty())
        assertTrue(history.visible(rules.withRule("app", NotificationRule.HIDE_CONTENT)).single().redacted)
        assertEquals("잠금 해제 후 내용을 확인하세요.", history.visible(rules).single().text)
    }
    @Test fun unselectedAppsAreNotArchivedAndEmptyCaptureCannotRevealOldContents() {
        val hidden = arrival("1").let { it.copy(payload = it.payload.copy(rule = NotificationRule.HIDDEN)) }
        assertTrue(NotificationHistory().receive(listOf(hidden), 1).records.isEmpty())
        val masked = arrival("2", "").let { it.copy(payload = it.payload.copy(title = "", rule = NotificationRule.HIDE_CONTENT)) }
        assertTrue(NotificationHistory().receive(listOf(masked), 2).visible(rules.withRule("app", NotificationRule.SHOW_CONTENT)).single().redacted)
    }
    @Test fun latestAppGroupIsFirstAndEachGroupKeepsLatestEntryFirst() {
        val entries = listOf(NotificationEntry("a1", "a", "앱 A", "이전", "", 1), NotificationEntry("b1", "b", "앱 B", "중간", "", 2),
            NotificationEntry("a2", "a", "앱 A", "최신", "", 3))
        val groups = groupNotifications(entries)
        assertEquals(listOf("a", "b"), groups.map { it.packageName }); assertEquals(listOf("a2", "a1"), groups.first().items.map { it.key })
    }
    @Test fun realChildMessagesReplaceSyntheticGroupSummary() {
        val summary = arrival("1").let { it.copy(payload = it.payload.copy(groupKey = "group", groupSummary = true)) }
        val child = arrival("2").let { it.copy(payload = it.payload.copy(groupKey = "group")) }
        assertEquals(listOf("2"), NotificationHistory().receive(listOf(summary), 1).receive(listOf(child), 2).records.map { it.id })
    }
    @Test fun stricterSystemVisibilityStillAllowsLaterExplicitContentChoice() {
        val history = NotificationHistory().receive(listOf(arrival("1", "원문")), 1).restrict("same-source", NotificationPrivacy.PRIVATE)
        assertTrue(history.visible(rules).single().redacted)
        assertEquals("원문", history.visible(rules.withRule("app", NotificationRule.SHOW_CONTENT)).single().text)
    }
}
