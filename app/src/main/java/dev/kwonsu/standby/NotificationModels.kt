package dev.kwonsu.standby

enum class NotificationPrivacy { SECRET, PRIVATE, PUBLIC }

data class NotificationPayload(
    val key: String, val packageName: String, val appName: String, val title: String, val text: String,
    val postedAt: Long, val privacy: NotificationPrivacy = NotificationPrivacy.PRIVATE,
    val publicTitle: String? = null, val publicText: String? = null,
    val groupKey: String? = null, val groupSummary: Boolean = false,
    val rule: NotificationRule = NotificationRule.LOCK_SCREEN,
)

/** 앱별 표시 규칙을 통과한 내용만 메모리에 보관한다. 실행 동작은 보관하지 않는다. */
data class NotificationEntry(
    val key: String, val packageName: String, val appName: String, val title: String, val text: String,
    val postedAt: Long, val redacted: Boolean = false, val groupKey: String? = null, val groupSummary: Boolean = false,
)

data class NotificationFeedState(
    val permissionGranted: Boolean = false, val connected: Boolean = false,
    val items: List<NotificationEntry> = emptyList(), val unavailable: Boolean = false,
    val apps: List<NotificationApp> = emptyList(),
    val historyWarning: String? = null,
)

fun visibleNotifications(payloads: List<NotificationPayload>): List<NotificationEntry> {
    val visible = payloads.mapNotNull { source ->
        if (source.rule == NotificationRule.HIDDEN) return@mapNotNull null
        if (source.rule == NotificationRule.LOCK_SCREEN && source.privacy == NotificationPrivacy.SECRET) return@mapNotNull null
        val hideContent = source.rule == NotificationRule.HIDE_CONTENT
        val private = source.rule == NotificationRule.LOCK_SCREEN && source.privacy == NotificationPrivacy.PRIVATE
        val hasPublicVersion = source.publicTitle != null || source.publicText != null
        NotificationEntry(source.key, source.packageName, source.appName.take(80),
            (if (hideContent) "새 알림" else if (private) source.publicTitle ?: "새 알림" else source.title).ifBlank { "새 알림" }.take(160),
            (if (hideContent) "이 앱의 내용을 숨겼습니다." else if (private) source.publicText ?: if (hasPublicVersion) "" else "잠금 해제 후 내용을 확인하세요." else source.text).take(600),
            source.postedAt, hideContent || (private && !hasPublicVersion), source.groupKey, source.groupSummary)
    }.distinctBy { it.key }
    val groupsWithChildren = visible.filter { !it.groupSummary && it.groupKey != null }.map { it.packageName to it.groupKey }.toSet()
    return visible.filterNot { it.groupSummary && (it.packageName to it.groupKey) in groupsWithChildren }
        .sortedWith(compareByDescending<NotificationEntry> { it.postedAt }.thenBy { it.key }).take(100)
}
