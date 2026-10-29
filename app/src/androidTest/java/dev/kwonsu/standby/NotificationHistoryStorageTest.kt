package dev.kwonsu.standby

import android.app.Notification
import android.app.Person
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID

class NotificationHistoryStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun sbn(notification: Notification, postTime: Long) = StatusBarNotification("test.app", "test.app", 8, "conversation",
        Process.myUid(), 0, 0, notification, Process.myUserHandle(), postTime)
    private fun build(messages: List<Pair<Long, String>>): Notification {
        val person = Person.Builder().setName("검사 발신자").build()
        val style = Notification.MessagingStyle(Person.Builder().setName("나").build())
        messages.forEach { (time, text) -> style.addMessage(text, time, person) }
        return Notification.Builder(context, "history-test").setSmallIcon(R.drawable.ic_standby).setStyle(style)
            .setContentTitle("검사 대화").setVisibility(Notification.VISIBILITY_PRIVATE).build()
    }
    private fun arrivals(n: Notification, post: Long) = notificationArrivals(sbn(n, post), "검사 앱",
        NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE, NotificationRule.SHOW_CONTENT)

    @Test fun repeatedAndroidMessageBundlesKeepEveryNewMessageIncludingIdenticalText() {
        val first = arrivals(build(listOf(100L to "같은 메시지")), 1000)
        val next = arrivals(build(listOf(100L to "같은 메시지", 200L to "같은 메시지", 300L to "새 메시지")), 2000)
        val history = NotificationHistory().receive(first, 1000).receive(next, 2000).receive(next, 3000)
        assertEquals(3, history.records.size)
        assertEquals(listOf("새 메시지", "같은 메시지", "같은 메시지"), history.visible(NotificationRules(mapOf("test.app" to NotificationRule.SHOW_CONTENT))).map { it.text })
        assertEquals(3, history.sourceRemoved(first.first().sourceKey).records.size)
    }

    @Test fun inboxLinesStayIndividualAndIdenticalSystemUpdatesDoNotDuplicate() {
        fun inbox(vararg lines: String) = Notification.Builder(context, "history-test").setSmallIcon(R.drawable.ic_standby)
            .setContentTitle("묶음").setStyle(Notification.InboxStyle().apply { lines.forEach { addLine(it) } }).build()
        val first = arrivals(inbox("이전 알림", "다음 알림"), 1000)
        val next = arrivals(inbox("이전 알림", "다음 알림", "최신 알림"), 2000)
        val history = NotificationHistory().receive(first, 1000).receive(next, 2000)
        assertEquals(3, history.records.size)
        assertEquals("최신 알림", history.records.first().payload.text)
        assertEquals(first.first().payload.postedAt, history.records.last().payload.postedAt)
    }

    @Test fun encryptedHistoryAndDismissalSurviveDiskReopenWithoutPlaintext() {
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "$id.enc")
        val alias = "${context.packageName}.test-history.$id"
        try {
            val store = NotificationHistoryStore(context, file, alias)
            val items = arrivals(build(listOf(100L to "암호화 검사 본문", 200L to "두 번째 본문")), 2000)
            val history = NotificationHistory().receive(items, 2000)
            store.save(history)
            val bytes = file.readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains("암호화 검사 본문"))
            assertEquals(history, NotificationHistoryStore(context, file, alias).load())
            val dismissed = history.dismiss(items.last().id)
            store.save(dismissed)
            val reopened = NotificationHistoryStore(context, file, alias).load().receive(items, 3000)
            assertEquals(1, reopened.records.size); assertEquals(items.first().id, reopened.records.single().id)
            val corrupt = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            file.writeBytes(corrupt)
            assertTrue(runCatching { store.load() }.isFailure)
            assertArrayEquals(corrupt, file.readBytes())
        } finally {
            file.delete(); File(file.path + ".bak").delete(); File(file.path + ".new").delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    @Test fun bulkDismissalStaysDeletedAfterEncryptedReopenAndNewMessageStillArrives() {
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "$id.enc")
        val alias = "${context.packageName}.test-history.$id"
        try {
            val items = arrivals(build(listOf(100L to "첫 기록", 200L to "두 번째 기록")), 2000)
            val cleared = NotificationHistory().receive(items, 2000).dismissMany(items.map { it.id }.toSet())
            NotificationHistoryStore(context, file, alias).save(cleared)
            val bundle = arrivals(build(listOf(100L to "첫 기록", 200L to "두 번째 기록", 300L to "새 기록")), 3000)
            val reopened = NotificationHistoryStore(context, file, alias).load().receive(bundle, 3000)
            assertEquals(listOf("새 기록"), reopened.records.map { it.payload.text })
        } finally {
            file.delete(); File(file.path + ".bak").delete(); File(file.path + ".new").delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
