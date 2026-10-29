package dev.kwonsu.standby

enum class NotificationRule(val label: String, val description: String) {
    HIDDEN("숨김", "스탠바이 알림 목록에서 제외합니다."),
    LOCK_SCREEN("잠금 설정 따름", "잠금 화면용 공개 내용만 표시합니다."),
    SHOW_CONTENT("내용 표시", "잠금 상태에서도 Android가 전달한 제목과 내용을 표시합니다."),
    HIDE_CONTENT("내용 숨김", "앱 이름과 도착 시간만 표시합니다."),
}

data class NotificationApp(val packageName: String, val name: String)

data class NotificationRules(val apps: Map<String, NotificationRule> = initialNotificationRules,
    val selectedOnly: Boolean = false, val selectedApps: Set<String> = setOf("com.kakao.talk"),
) {
    fun storedRuleFor(packageName: String) = apps[packageName] ?: NotificationRule.LOCK_SCREEN
    fun ruleFor(packageName: String) = if (selectedOnly && packageName !in selectedApps) NotificationRule.HIDDEN else storedRuleFor(packageName)
    fun withRule(packageName: String, rule: NotificationRule): NotificationRules {
        if (packageName.isBlank()) return this
        return copy(apps = apps + (packageName to rule), selectedApps = when {
            rule == NotificationRule.HIDDEN -> selectedApps - packageName
            selectedOnly -> selectedApps + packageName
            else -> selectedApps
        })
    }
    fun withAppSelected(packageName: String, selected: Boolean): NotificationRules {
        if (packageName.isBlank()) return this
        return copy(selectedApps = if (selected) selectedApps + packageName else selectedApps - packageName,
            apps = if (selected && storedRuleFor(packageName) == NotificationRule.HIDDEN) apps + (packageName to NotificationRule.LOCK_SCREEN) else apps)
    }
}

/** 개인 기기에서 요청한 초기 규칙. 이후 사용자가 바꾼 값은 저장값을 따른다. */
internal val initialNotificationRules = mapOf(
    "com.android.systemui" to NotificationRule.HIDDEN,
    "com.kakao.talk" to NotificationRule.SHOW_CONTENT,
)

internal val familiarNotificationApps = listOf(
    NotificationApp("com.android.systemui", "시스템 UI"),
    NotificationApp("com.kakao.talk", "카카오톡"),
)
