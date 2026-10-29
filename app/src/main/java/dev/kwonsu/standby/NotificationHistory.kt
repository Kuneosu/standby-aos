package dev.kwonsu.standby

import java.security.MessageDigest

data class NotificationArrival(val id: String, val sourceKey: String, val payload: NotificationPayload,
    val genericSignature: String? = null)
data class NotificationRecord(val id: String, val sourceKey: String, val payload: NotificationPayload, val receivedAt: Long,
    val originalAvailable: Boolean = payload.rule == NotificationRule.SHOW_CONTENT ||
        (payload.rule == NotificationRule.LOCK_SCREEN && payload.privacy == NotificationPrivacy.PUBLIC))

/** 알림 갱신과 시스템에서의 제거를 별개로 다루는 스탠바이 수신 기록. */
data class NotificationHistory(
    val records: List<NotificationRecord> = emptyList(),
    val seenIds: List<String> = emptyList(),
    val lastSignatures: Map<String, String> = emptyMap(),
) {
    fun receive(arrivals: List<NotificationArrival>, now: Long): NotificationHistory {
        val next = records.toMutableList()
        val seen = seenIds.toMutableSet()
        val signatures = lastSignatures.toMutableMap()
        for (arrival in arrivals) {
            val payload = arrival.payload
            if (visibleNotifications(listOf(payload)).isEmpty()) continue
            val found = next.indexOfFirst { it.id == arrival.id }
            if (found >= 0) {
                // 같은 수신의 공개 규칙 변경은 새 알림을 만들지 않는다.
                next[found] = NotificationRecord(arrival.id, arrival.sourceKey,
                    payload.copy(postedAt = minOf(next[found].payload.postedAt, payload.postedAt)), next[found].receivedAt)
            } else if (arrival.id !in seen && (arrival.genericSignature == null || signatures[arrival.sourceKey] != arrival.genericSignature)) {
                next.add(NotificationRecord(arrival.id, arrival.sourceKey, payload, now))
            }
            seen.add(arrival.id)
            if (arrival.genericSignature != null) signatures[arrival.sourceKey] = arrival.genericSignature
        }
        val childGroups = next.filter { !it.payload.groupSummary && it.payload.groupKey != null }
            .map { it.payload.packageName to it.payload.groupKey }.toSet()
        val kept = next.filterNot { it.payload.groupSummary && (it.payload.packageName to it.payload.groupKey) in childGroups }
            .sortedWith(compareByDescending<NotificationRecord> { it.payload.postedAt }.thenByDescending { it.receivedAt }).take(MAX_RECORDS)
        return copy(records = kept, seenIds = seen.toList().takeLast(MAX_SEEN), lastSignatures = signatures.entries.toList().takeLast(1000).associate { it.toPair() })
    }

    fun dismiss(id: String) = dismissMany(setOf(id))
    // 누른 시점의 항목만 제거한다. 이후 도착한 알림과 중복 방지 정보는 유지한다.
    fun dismissMany(ids: Set<String>) = copy(records = records.filterNot { it.id in ids })
    fun sourceRemoved(sourceKey: String) = copy(lastSignatures = lastSignatures - sourceKey)
    fun restrict(sourceKey: String, privacy: NotificationPrivacy) = copy(records = records.map {
        if (it.sourceKey == sourceKey) it.copy(payload = it.payload.copy(privacy = minOf(it.payload.privacy, privacy))) else it
    })

    fun visible(rules: NotificationRules): List<NotificationEntry> = records.mapNotNull { record ->
        val captured = record.payload
        val requested = rules.ruleFor(captured.packageName)
        val effective = if (requested == NotificationRule.SHOW_CONTENT && !record.originalAvailable) NotificationRule.LOCK_SCREEN else requested
        val payload = captured.copy(rule = effective)
        val entry = visibleNotifications(listOf(payload)).firstOrNull() ?: return@mapNotNull null
        if (captured.rule == NotificationRule.HIDE_CONTENT && requested != NotificationRule.HIDDEN)
            entry.copy(key = record.id, title = "새 알림", text = "수신 당시 내용 숨김 설정이 적용된 알림입니다.", redacted = true)
        else entry.copy(key = record.id)
    }

    companion object { const val MAX_RECORDS = 500; const val MAX_SEEN = 5000 }
}

data class NotificationAppGroup(val packageName: String, val items: List<NotificationEntry>) {
    val latest get() = items.first()
}

fun groupNotifications(items: List<NotificationEntry>): List<NotificationAppGroup> = items.groupBy { it.packageName }
    .map { (name, entries) -> NotificationAppGroup(name, entries.sortedByDescending { it.postedAt }) }
    .sortedByDescending { it.latest.postedAt }

internal fun notificationDigest(vararg values: String): String = MessageDigest.getInstance("SHA-256")
    .digest(values.joinToString(separator = "\u0000") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
