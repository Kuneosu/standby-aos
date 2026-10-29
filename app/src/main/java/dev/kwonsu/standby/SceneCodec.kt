package dev.kwonsu.standby

import org.json.JSONArray
import org.json.JSONObject

object SceneCodec {
    fun encode(value: StandbyScene): String {
        val scene = value.normalized()
        val bg = scene.background
        return JSONObject().put("version", 1).put("font", scene.font.name).put("textColor", scene.textColor)
            .put("background", JSONObject().put("kind", bg.kind.name).put("color", bg.color)
                .put("preset", bg.preset.name).put("media", bg.media).put("fit", bg.fit.name)
                .put("zoom", bg.zoom).put("panX", bg.panX).put("panY", bg.panY).put("dim", bg.dim)
                .put("widthScale", bg.widthScale).put("heightScale", bg.heightScale))
            .put("elements", JSONArray().apply { scene.elements.forEach { e ->
                put(JSONObject().put("id", e.id).put("kind", e.kind.name).put("x", e.x).put("y", e.y)
                    .put("width", e.width).put("height", e.height).put("color", e.color).put("media", e.media)
                    .put("fit", e.fit.name).put("opacity", e.opacity).put("speed", e.speed).put("loop", e.loop).put("positionLocked", e.positionLocked)
                    .put("gaugeOrientation", e.gaugeOrientation.name).put("sharedOccurrence", e.sharedSlot?.occurrence)
                    .put("textAlignment", e.textAlignment.name).put("textSize", e.textSize)
                    .put("contentSize", e.contentSize).put("horizontalAnchor", e.horizontalAnchor.name).put("verticalAnchor", e.verticalAnchor.name)
                    .put("horizontalPadding", e.horizontalPadding).put("verticalPadding", e.verticalPadding))
            } }).toString()
    }

    fun decode(value: String?): StandbyScene = runCatching {
        if (value.isNullOrBlank()) return StandbyScene()
        val json = JSONObject(value)
        val b = json.optJSONObject("background") ?: JSONObject()
        val array = json.optJSONArray("elements")
        StandbyScene(
            font = enumValue(json.optString("font"), ClockFont.SYSTEM), textColor = json.optLong("textColor", 0xFFA6E8CB),
            background = BackgroundSpec(enumValue(b.optString("kind"), BackgroundKind.SOLID), b.optLong("color", 0xFF050708),
                enumValue(b.optString("preset"), BackdropPreset.AURORA), b.optionalString("media"),
                enumValue(b.optString("fit"), PhotoFit.COVER), b.number("zoom", 1f), b.number("panX", 0f),
                b.number("panY", 0f), b.number("dim", 0.2f), b.number("widthScale", 1f), b.number("heightScale", 1f)),
            elements = if (array == null) defaultElements() else (0 until array.length().coerceAtMost(StandbyScene.MAX_STORED_ELEMENTS)).mapNotNull { i ->
                val e = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = ElementKind.entries.firstOrNull { it.name == e.optString("kind") } ?: return@mapNotNull null
                SceneElement(e.optString("id", "item-$i"), kind, e.number("x", 0.1f), e.number("y", 0.1f),
                    e.number("width", 0.3f), e.number("height", 0.25f), if (e.has("color") && !e.isNull("color")) e.getLong("color") else null,
                    e.optionalString("media"), enumValue(e.optString("fit"), PhotoFit.CONTAIN),
                    e.number("opacity", 1f), e.number("speed", 1f), e.optBoolean("loop", true), e.optBoolean("positionLocked", false),
                    enumValue(e.optString("gaugeOrientation"), GaugeOrientation.HORIZONTAL),
                    if (e.has("sharedOccurrence") && !e.isNull("sharedOccurrence")) LayoutSlot(kind, e.optInt("sharedOccurrence", -1)) else null,
                    enumValue(e.optString("textAlignment"), ElementTextAlignment.CENTER),
                    if (e.has("textSize") && !e.isNull("textSize")) e.number("textSize", 0.16f) else null,
                    if (e.has("contentSize") && !e.isNull("contentSize")) e.number("contentSize", 0.16f) else null,
                    enumValue(e.optString("horizontalAnchor"), CanvasAnchor.FREE), enumValue(e.optString("verticalAnchor"), CanvasAnchor.FREE),
                    e.number("horizontalPadding", e.number("horizontalMargin", 0f)), e.number("verticalPadding", e.number("verticalMargin", 0f)))
            },
        ).normalized()
    }.getOrDefault(StandbyScene())

    private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback
    private fun JSONObject.optionalString(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.number(key: String, fallback: Float) = optDouble(key, fallback.toDouble()).toFloat()
}
