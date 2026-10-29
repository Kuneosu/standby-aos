package dev.kwonsu.standby

import org.json.JSONArray
import org.json.JSONObject

object SharedLayoutCodec {
    fun encode(layout: SharedLayout): String = JSONObject().put("placements", JSONArray().apply {
        layout.normalized().placements.forEach { placement ->
            put(JSONObject().put("kind", placement.slot.kind.name).put("occurrence", placement.slot.occurrence)
                .put("x", placement.x).put("y", placement.y).put("width", placement.width).put("height", placement.height)
                .put("locked", placement.positionLocked).put("contentSize", placement.contentSize)
                .put("horizontalAnchor", placement.horizontalAnchor.name).put("verticalAnchor", placement.verticalAnchor.name)
                .put("horizontalPadding", placement.horizontalPadding).put("verticalPadding", placement.verticalPadding).apply {
                    placement.template?.let { element ->
                        put("template", JSONObject(SceneCodec.encode(StandbyScene(elements = listOf(element))))
                            .getJSONArray("elements").getJSONObject(0))
                    }
                })
        }
    }).toString()

    fun decode(raw: String?): SharedLayout? = runCatching {
        if (raw.isNullOrBlank()) return null
        val entries = JSONObject(raw).optJSONArray("placements") ?: return null
        SharedLayout((0 until entries.length().coerceAtMost(StandbyScene.MAX_ELEMENTS)).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val kind = sharedLayoutKinds.firstOrNull { it.name == entry.optString("kind") } ?: return@mapNotNull null
            SharedPlacement(LayoutSlot(kind, entry.optInt("occurrence", 0)),
                entry.optDouble("x", 0.1).toFloat(), entry.optDouble("y", 0.1).toFloat(),
                entry.optDouble("width", 0.3).toFloat(), entry.optDouble("height", 0.2).toFloat(), entry.optBoolean("locked", false),
                entry.optJSONObject("template")?.let { element ->
                    SceneCodec.decode(JSONObject().put("elements", JSONArray().put(element)).toString()).elements.singleOrNull()?.takeIf { it.kind == kind }
                },
                if (entry.has("contentSize") && !entry.isNull("contentSize")) entry.optDouble("contentSize", 0.16).toFloat() else null,
                CanvasAnchor.entries.firstOrNull { it.name == entry.optString("horizontalAnchor") } ?: CanvasAnchor.FREE,
                CanvasAnchor.entries.firstOrNull { it.name == entry.optString("verticalAnchor") } ?: CanvasAnchor.FREE,
                entry.optDouble("horizontalPadding", entry.optDouble("horizontalMargin", 0.0)).toFloat(), entry.optDouble("verticalPadding", entry.optDouble("verticalMargin", 0.0)).toFloat())
        }).normalized()
    }.getOrNull()
}
