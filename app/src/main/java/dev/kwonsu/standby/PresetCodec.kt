package dev.kwonsu.standby

import org.json.JSONArray
import org.json.JSONObject

object PresetCodec {
    fun encode(value: PresetLibrary): String {
        val library = value.normalized()
        return JSONObject().put("version", 2).put("enabled", library.cycleEnabled)
            .apply { library.sharedLayout?.let { put("sharedLayout", JSONObject(SharedLayoutCodec.encode(it))) } }
            .put("cycle", JSONArray(library.cycleIds)).put("presets", JSONArray().apply {
                library.presets.forEach { put(JSONObject().put("id", it.id).put("name", it.name)
                    .put("useSharedLayout", it.useSharedLayout).put("scene", JSONObject(SceneCodec.encode(it.scene)))) }
            }).toString()
    }
    fun decode(raw: String?): PresetLibrary = runCatching {
        if (raw.isNullOrBlank()) return PresetLibrary()
        val json = JSONObject(raw)
        val presets = json.optJSONArray("presets") ?: JSONArray()
        val cycle = json.optJSONArray("cycle") ?: JSONArray()
        PresetLibrary((0 until presets.length().coerceAtMost(PresetLibrary.MAX_PRESETS)).mapNotNull { index ->
            val preset = presets.optJSONObject(index) ?: return@mapNotNull null
            val scene = preset.optJSONObject("scene") ?: return@mapNotNull null
            ScenePreset(preset.optString("id"), preset.optString("name"), SceneCodec.decode(scene.toString()), preset.optBoolean("useSharedLayout", false))
        }, (0 until cycle.length()).map { cycle.optString(it) }, json.optBoolean("enabled", false),
            SharedLayoutCodec.decode(json.optJSONObject("sharedLayout")?.toString())).normalized()
    }.getOrDefault(PresetLibrary())
}
