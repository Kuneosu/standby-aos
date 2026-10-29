package dev.kwonsu.standby

import android.app.Notification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NotificationDecodingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun notification(visibility: Int) = Notification.Builder(context, "decoding-test")
        .setSmallIcon(R.drawable.ic_standby).setContentTitle("비공개 원문 제목").setContentText("비공개 원문 내용").setVisibility(visibility)

    @Test fun androidPublicVersionAndSystemRestrictionsAreAppliedBeforePublishing() {
        val public = Notification.Builder(context, "decoding-test").setSmallIcon(R.drawable.ic_standby)
            .setContentTitle("공개 안내").setStyle(Notification.BigTextStyle().bigText("공개용 긴 안내")).build()
        val original = notification(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        val payload = notificationPayload(original, "key", "app", "검사 앱", 1L)
        assertEquals("", payload.title); assertEquals("", payload.text)
        val entry = visibleNotifications(listOf(payload)).single()
        assertEquals("공개 안내", entry.title); assertEquals("공개용 긴 안내", entry.text)
        assertTrue(visibleNotifications(listOf(notificationPayload(original, "key", "app", "검사 앱", 1L, Notification.VISIBILITY_SECRET))).isEmpty())
        val restricted = notificationPayload(notification(Notification.VISIBILITY_PUBLIC).build(), "key", "app", "검사 앱", 1L, Notification.VISIBILITY_PRIVATE)
        assertEquals("", restricted.title); assertTrue(visibleNotifications(listOf(restricted)).single().redacted)
    }

    @Test fun disconnectAndPermissionRevocationClearTheVisibleSnapshot() {
        try {
            NotificationRuntime.connected()
            NotificationRuntime.publish(listOf(NotificationEntry("key", "app", "검사 앱", "공개 알림", "공개 내용", 1L)))
            assertEquals(1, NotificationRuntime.state.value.items.size)
            NotificationRuntime.disconnected(false)
            assertEquals(NotificationFeedState(), NotificationRuntime.state.value)
            NotificationRuntime.publish(listOf(NotificationEntry("late", "app", "검사 앱", "늦은 결과", "이전 요청", 1L)))
            assertTrue(NotificationRuntime.state.value.items.isEmpty())
        } finally { NotificationRuntime.disconnected(false) }
    }

    @Test fun explicitContentRuleReadsTheDeliveredOriginalWithoutChangingTheNotification() {
        val original = notification(Notification.VISIBILITY_PRIVATE).setPublicVersion(notification(Notification.VISIBILITY_PUBLIC)
            .setContentTitle("공개 안내").setContentText("공개용 내용").build()).build()
        val payload = notificationPayload(original, "key", "com.kakao.talk", "카카오톡", 1L,
            Notification.VISIBILITY_SECRET, rule = NotificationRule.SHOW_CONTENT)
        val entry = visibleNotifications(listOf(payload)).single()
        assertEquals("비공개 원문 제목", entry.title); assertEquals("비공개 원문 내용", entry.text)
        assertEquals(Notification.VISIBILITY_PRIVATE, original.visibility)
        for (rule in listOf(NotificationRule.HIDDEN, NotificationRule.HIDE_CONTENT)) {
            val hidden = notificationPayload(original, "key", "com.kakao.talk", "카카오톡", 1L, rule = rule)
            assertEquals("", hidden.title); assertEquals("", hidden.text); assertNull(hidden.publicTitle); assertNull(hidden.publicText)
        }
    }

    @Test fun emptyTextFallsBackToMessagingStyleButSystemSuppliedRedactionIsNotReplaced() {
        val person = android.app.Person.Builder().setName("검사 발신자").build()
        val n = notification(Notification.VISIBILITY_PRIVATE)
            .setStyle(Notification.MessagingStyle(android.app.Person.Builder().setName("나").build())
                .addMessage("첫 메시지", 1L, person).addMessage("마지막 메시지", 2L, person)).build()
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "")
        n.extras.putCharSequence(Notification.EXTRA_TEXT, "")
        assertEquals("첫 메시지\n마지막 메시지", notificationText(n))
        n.extras.putCharSequence(Notification.EXTRA_TEXT, "시스템에서 숨긴 알림")
        assertEquals("시스템에서 숨긴 알림", notificationText(n))
    }
}
