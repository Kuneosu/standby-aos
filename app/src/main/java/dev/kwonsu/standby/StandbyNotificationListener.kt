package dev.kwonsu.standby

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first

object NotificationRuntime {
    private val mutable = MutableStateFlow(NotificationFeedState())
    val state = mutable.asStateFlow()

    fun component(context: Context) = ComponentName(context, StandbyNotificationListener::class.java)
    fun isAllowed(context: Context) = runCatching {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
    }.getOrDefault(false)

    fun refreshAccess(context: Context) {
        val allowed = isAllowed(context)
        mutable.update { if (allowed) it.copy(permissionGranted = true) else NotificationFeedState() }
        if (allowed && !mutable.value.connected) runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    internal fun connected() { mutable.value = NotificationFeedState(permissionGranted = true, connected = true) }
    internal fun disconnected(allowed: Boolean) { mutable.value = NotificationFeedState(permissionGranted = allowed) }
    internal fun clearVisible() { mutable.update { it.copy(items = emptyList()) } }
    internal var dismissHandler: ((Set<String>) -> Unit)? = null
    fun dismiss(id: String) = dismissMany(setOf(id))
    fun dismissMany(ids: Set<String>) { if (ids.isNotEmpty()) dismissHandler?.invoke(ids.toSet()) }
    internal fun publish(items: List<NotificationEntry>, unavailable: Boolean = false, apps: List<NotificationApp> = emptyList(), historyWarning: String? = null) {
        mutable.update { if (it.permissionGranted && it.connected) it.copy(items = items, unavailable = unavailable, apps = apps, historyWarning = historyWarning) else it }
    }
}

/** 수신 이벤트를 순서대로 보관한다. 제거는 스탠바이 기록에만 적용한다. */
class StandbyNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = kotlinx.coroutines.channels.Channel<HistoryEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val names = LruCache<String, String>(64)
    private var latestRules: NotificationRules? = null
    private val dismissHandler: (Set<String>) -> Unit = { events.trySend(HistoryEvent.Dismiss(it)); Unit }

    override fun onCreate() {
        super.onCreate()
        NotificationRuntime.dismissHandler = dismissHandler
        scope.launch {
            val store = NotificationHistoryStore(this@StandbyNotificationListener)
            var rules = SettingsStore(this@StandbyNotificationListener).notificationRules.first()
            latestRules = rules
            val loaded = withContext(Dispatchers.IO) { runCatching { store.load() } }
            var history = loaded.getOrDefault(NotificationHistory())
            val loadFailed = loaded.isFailure
            var warning: String? = if (loadFailed) "저장된 알림을 읽지 못했습니다. 기존 파일을 유지하며 새 알림은 임시로 표시합니다." else null
            var activeApps = emptyList<NotificationApp>()
            scope.launch {
                SettingsStore(this@StandbyNotificationListener).notificationRules.collect {
                    latestRules = it
                    NotificationRuntime.clearVisible()
                    events.send(HistoryEvent.Rules(it))
                }
            }
            for (event in events) {
                val before = history
                try {
                    when (event) {
                        is HistoryEvent.Posted -> {
                            val app = appName(event.sbn.packageName)
                            activeApps = (activeApps + NotificationApp(event.sbn.packageName, app)).distinctBy { it.packageName }
                            if (!isOwnMonitor(event.sbn)) history = history.receive(
                                notificationArrivals(event.sbn, app, event.visibility, rules.ruleFor(event.sbn.packageName)), System.currentTimeMillis())
                        }
                        is HistoryEvent.Sync -> {
                            activeApps = event.snapshots.map { NotificationApp(it.sbn.packageName, appName(it.sbn.packageName)) }.distinctBy { it.packageName }
                            val arrivals = event.snapshots.filterNot { isOwnMonitor(it.sbn) }.flatMap {
                                notificationArrivals(it.sbn, appName(it.sbn.packageName), it.visibility, rules.ruleFor(it.sbn.packageName))
                            }
                            history = history.receive(arrivals, System.currentTimeMillis())
                        }
                        is HistoryEvent.Removed -> history = history.sourceRemoved(event.key)
                        is HistoryEvent.Dismiss -> history = history.dismissMany(event.ids)
                        is HistoryEvent.Rules -> {
                            rules = event.rules
                            if (NotificationRuntime.state.value.connected) events.send(HistoryEvent.Sync(snapshot()))
                        }
                        is HistoryEvent.Restrict -> event.privacy.forEach { (key, privacy) -> history = history.restrict(key, privacy) }
                    }
                    if (before != history && !loadFailed) {
                        val saved = withContext(Dispatchers.IO) { runCatching { store.save(history) } }
                        warning = if (saved.isFailure) "알림 기록을 저장하지 못했습니다. 앱을 다시 열면 일부 기록이 사라질 수 있습니다." else null
                    }
                    if (rules == latestRules) NotificationRuntime.publish(history.visible(rules), apps =
                        (activeApps + history.records.map { NotificationApp(it.payload.packageName, it.payload.appName) }).distinctBy { it.packageName }, historyWarning = warning)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (rules == latestRules) NotificationRuntime.publish(history.visible(rules), apps = activeApps, historyWarning = "일부 알림을 처리하지 못했습니다.")
                }
            }
        }
    }

    private fun isOwnMonitor(sbn: StatusBarNotification) = sbn.packageName == packageName && sbn.notification.channelId == ChargeMonitorService.CHANNEL
    private fun appName(name: String): String = names[name] ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString()
    }.getOrDefault(name).also { names.put(name, it) }
    private fun visibility(key: String, map: RankingMap = currentRanking): Int {
        val ranking = Ranking()
        return if (map.getRanking(key, ranking)) ranking.lockscreenVisibilityOverride else Ranking.VISIBILITY_NO_OVERRIDE
    }
    private fun snapshot(): List<HistoryEvent.Posted> {
        val map = currentRanking
        return activeNotifications.orEmpty().map { HistoryEvent.Posted(it, visibility(it.key, map)) }
    }
    override fun onListenerConnected() {
        NotificationRuntime.connected()
        Diagnostics.record("notification_listener_connected", "알림 기록 연결됨")
        runCatching { events.trySend(HistoryEvent.Sync(snapshot())) }
    }
    override fun onListenerDisconnected() {
        NotificationRuntime.disconnected(NotificationRuntime.isAllowed(this))
        Diagnostics.record("notification_listener_disconnected", "알림 기록 연결 해제")
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        events.trySend(HistoryEvent.Posted(sbn, runCatching { visibility(sbn.key) }.getOrDefault(Ranking.VISIBILITY_NO_OVERRIDE)))
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { events.trySend(HistoryEvent.Removed(sbn.key)) }
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val restrictions = rankingMap.orderedKeys.mapNotNull { key ->
            val value = visibility(key, rankingMap)
            if (value == Ranking.VISIBILITY_NO_OVERRIDE) null else key to restrictedPrivacy(Notification.VISIBILITY_PUBLIC, value)
        }.toMap()
        events.trySend(HistoryEvent.Restrict(restrictions))
    }
    override fun onDestroy() {
        if (NotificationRuntime.dismissHandler === dismissHandler) NotificationRuntime.dismissHandler = null
        events.close(); scope.cancel()
        NotificationRuntime.disconnected(NotificationRuntime.isAllowed(this))
        super.onDestroy()
    }

    private sealed interface HistoryEvent {
        data class Posted(val sbn: StatusBarNotification, val visibility: Int) : HistoryEvent
        data class Sync(val snapshots: List<Posted>) : HistoryEvent
        data class Removed(val key: String) : HistoryEvent
        data class Dismiss(val ids: Set<String>) : HistoryEvent
        data class Rules(val rules: NotificationRules) : HistoryEvent
        data class Restrict(val privacy: Map<String, NotificationPrivacy>) : HistoryEvent
    }
}

/** 사용자가 정한 스탠바이 표시 규칙에 필요한 내용만 추출한다. 시스템에서 가린 원문은 복원하지 않는다. */
internal fun notificationPayload(notification: Notification, key: String, packageName: String, appName: String, postedAt: Long,
    visibilityOverride: Int = NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE, groupKey: String? = null,
    rule: NotificationRule = NotificationRule.LOCK_SCREEN,
): NotificationPayload {
    val privacy = restrictedPrivacy(notification.visibility, visibilityOverride)
    val fullContent = rule == NotificationRule.SHOW_CONTENT || (rule == NotificationRule.LOCK_SCREEN && privacy == NotificationPrivacy.PUBLIC)
    val public = notification.publicVersion.takeIf { rule == NotificationRule.LOCK_SCREEN && privacy == NotificationPrivacy.PRIVATE }
    return NotificationPayload(key, packageName, appName,
        if (fullContent) notificationTitle(notification) else "",
        if (fullContent) notificationText(notification) else "", postedAt, privacy,
        public?.let(::notificationTitle), public?.let(::notificationText), groupKey,
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, rule)
}

internal fun restrictedPrivacy(visibility: Int, override: Int): NotificationPrivacy {
    fun privacy(value: Int) = when (value) {
        Notification.VISIBILITY_SECRET -> NotificationPrivacy.SECRET
        Notification.VISIBILITY_PUBLIC -> NotificationPrivacy.PUBLIC
        else -> NotificationPrivacy.PRIVATE
    }
    val own = privacy(visibility)
    return if (override == NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE) own else minOf(own, privacy(override))
}

internal fun notificationTitle(notification: Notification): String =
    notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
        ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

internal fun notificationText(notification: Notification): String =
    notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ?: notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")?.takeIf { it.isNotBlank() }
        ?: NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages
            ?.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }?.takeLast(4)?.joinToString("\n").orEmpty()
