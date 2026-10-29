package dev.kwonsu.standby

data class ScenePreset(val id: String, val name: String, val scene: StandbyScene, val useSharedLayout: Boolean = false)
data class PresetLibrary(val presets: List<ScenePreset> = emptyList(), val cycleIds: List<String> = emptyList(), val cycleEnabled: Boolean = false,
    val sharedLayout: SharedLayout? = null,
) {
    fun normalized(): PresetLibrary {
        val kept = presets.filter { it.id.isNotBlank() }.distinctBy { it.id }.take(MAX_PRESETS)
            .map { it.copy(name = it.name.trim().take(40).ifBlank { "프리셋" }, scene = it.scene.normalized()) }
        return copy(presets = kept, cycleIds = cycleIds.distinct().filter { id -> kept.any { it.id == id } },
            sharedLayout = sharedLayout?.normalized())
    }
    fun mediaNames() = presets.flatMap { it.scene.mediaNames() }.toSet() + sharedLayout?.mediaNames().orEmpty()
    fun withSharedElements(references: List<StandbyScene> = emptyList()): PresetLibrary {
        val shared = sharedLayout?.withTemplatesFrom(references + presets.map { it.scene }) ?: return this
        return copy(sharedLayout = shared, presets = presets.map {
            if (it.useSharedLayout) it.copy(scene = shared.addMissingTo(it.scene)) else it
        })
    }
    fun displayScene(scene: StandbyScene, useSharedLayout: Boolean) = if (useSharedLayout) sharedLayout?.applyTo(scene) ?: scene else scene
    fun displayScene(preset: ScenePreset) = displayScene(preset.scene, preset.useSharedLayout)
    companion object { const val MAX_PRESETS = 32 }
}

/** 등록한 목록만 순회한다. 제외·삭제된 프리셋은 선택하지 않는다. */
fun nextPresetId(library: PresetLibrary, previous: String?): String? {
    val current = library.normalized()
    if (!current.cycleEnabled || current.cycleIds.isEmpty()) return null
    val index = current.cycleIds.indexOf(previous)
    return current.cycleIds[(index + 1) % current.cycleIds.size]
}

data class PlaybackSelection(val scene: StandbyScene, val presetId: String? = null, val name: String = "현재 디자인", val cycleSize: Int = 0)

data class PlaybackVisit(val id: String, val screenWasOff: Boolean = false) {
    fun screenOff() = copy(screenWasOff = true)
    fun screenOn(nextId: String) = if (screenWasOff) PlaybackVisit(nextId) else this
}

sealed interface PresetAction {
    data class Save(val name: String, val id: String? = null) : PresetAction
    data class Load(val id: String) : PresetAction
    data class Rename(val id: String, val name: String) : PresetAction
    data class Delete(val id: String) : PresetAction
    data class Include(val id: String, val include: Boolean) : PresetAction
    data class Move(val id: String, val delta: Int) : PresetAction
    data class Enable(val enabled: Boolean) : PresetAction
    data class SaveSharedLayout(val layout: SharedLayout) : PresetAction
    data class UseSharedLayout(val id: String?, val enabled: Boolean) : PresetAction
}
