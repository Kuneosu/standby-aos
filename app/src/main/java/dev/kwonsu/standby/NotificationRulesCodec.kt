package dev.kwonsu.standby

import org.json.JSONObject

object NotificationRulesCodec {
    fun encode(rules: NotificationRules): String = JSONObject().put("version", 2).put("apps",
        JSONObject().apply { rules.apps.forEach { (name, rule) -> if (name.isNotBlank()) put(name, rule.name) } })
        .put("selectedOnly", rules.selectedOnly).put("selectedApps", org.json.JSONArray(rules.selectedApps.filter { it.isNotBlank() }.sorted())).toString()

    fun decode(raw: String?): NotificationRules {
        if (raw == null) return NotificationRules()
        return runCatching {
            val root = JSONObject(raw)
            val source = root.getJSONObject("apps")
            val selected = root.optJSONArray("selectedApps")
            NotificationRules(source.keys().asSequence().filter { it.isNotBlank() }.associateWith { name ->
                // 저장된 알 수 없는 값은 내용을 공개하는 기본 예외로 되돌리지 않는다.
                runCatching { NotificationRule.valueOf(source.getString(name)) }.getOrDefault(NotificationRule.LOCK_SCREEN)
            }, selectedOnly = root.optBoolean("selectedOnly", false), selectedApps = if (selected == null) {
                if (root.optBoolean("selectedOnly", false)) emptySet() else setOf("com.kakao.talk")
            } else (0 until selected.length()).mapNotNull { selected.optString(it).takeIf(String::isNotBlank) }.toSet())
        }.getOrDefault(NotificationRules(emptyMap()))
    }
}
