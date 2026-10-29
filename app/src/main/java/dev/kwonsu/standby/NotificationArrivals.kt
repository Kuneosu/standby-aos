package dev.kwonsu.standby

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** 같은 시스템 알림 ID 안에 포함된 메시지도 각각 안정적인 수신 ID로 나눈다. */
internal fun notificationArrivals(sbn: StatusBarNotification, appName: String, visibility: Int, rule: NotificationRule): List<NotificationArrival> {
    if (rule == NotificationRule.HIDDEN) return emptyList()
    val n = sbn.notification
    val base = notificationPayload(n, sbn.key, sbn.packageName, appName, sbn.postTime, visibility, sbn.groupKey, rule)
    if (visibleNotifications(listOf(base)).isEmpty()) return emptyList()
    val full = rule == NotificationRule.SHOW_CONTENT || (rule == NotificationRule.LOCK_SCREEN && base.privacy == NotificationPrivacy.PUBLIC)
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
    val messages = style?.messages.orEmpty()
    val occurrencesByMessage = mutableMapOf<String, Int>()
    if (messages.isNotEmpty()) return messages.mapIndexed { index, message ->
        val sender = message.person?.name?.toString().orEmpty()
        val body = message.text?.toString().orEmpty().ifBlank { "첨부파일 알림" }
        val conversation = style?.conversationTitle?.toString().orEmpty()
        val title = listOf(conversation, sender).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { base.title }
        val time = message.timestamp.takeIf { it > 0 } ?: (sbn.postTime - (messages.lastIndex - index))
        val identity = notificationDigest(sender, body)
        val occurrence = occurrencesByMessage.getOrDefault(identity, 0)
        occurrencesByMessage[identity] = occurrence + 1
        val id = notificationDigest(sbn.key, "message", if (message.timestamp > 0) message.timestamp.toString() else "시간 없음",
            sender, body, if (message.timestamp <= 0) occurrence.toString() else "")
        NotificationArrival(id, sbn.key, base.copy(title = if (full) title.take(160) else "", text = if (full) body.take(600) else "", postedAt = time))
    }
    val lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }?.filter { it.isNotBlank() }.orEmpty()
    if (lines.size > 1) {
        val occurrences = mutableMapOf<String, Int>()
        return lines.mapIndexed { index, line ->
            val occurrence = occurrences.getOrDefault(line, 0)
            occurrences[line] = occurrence + 1
            NotificationArrival(notificationDigest(sbn.key, "line", line, occurrence.toString()), sbn.key,
                base.copy(text = if (full) line.take(600) else "", postedAt = sbn.postTime - (lines.lastIndex - index)))
        }
    }
    // 내용이 같은 진행률·순위 갱신은 중복해서 쌓지 않는다. 중복 비교값은 원문 대신 해시로 보관한다.
    val signature = notificationDigest(notificationTitle(n), notificationText(n))
    val id = notificationDigest(sbn.key, "notification", sbn.postTime.toString(), signature)
    return listOf(NotificationArrival(id, sbn.key, base.copy(title = base.title.take(160), text = base.text.take(600)), signature))
}
