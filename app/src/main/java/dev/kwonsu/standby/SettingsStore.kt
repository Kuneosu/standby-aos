package dev.kwonsu.standby

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.dataStore by preferencesDataStore("standby_settings")
class SettingsStore(context: Context, private val preferences: DataStore<Preferences> = context.dataStore) {
    private val nightKey = booleanPreferencesKey("night")
    private val sceneKey = stringPreferencesKey("scene_v1")
    private val draftKey = stringPreferencesKey("scene_draft_v1")
    private val sharedSceneKey = booleanPreferencesKey("scene_uses_shared_layout")
    private val sharedDraftKey = booleanPreferencesKey("draft_uses_shared_layout")
    private val draftSourceKey = stringPreferencesKey("draft_source_preset")
    private val sceneSourceKey = stringPreferencesKey("scene_source_preset")
    private val presetsKey = stringPreferencesKey("presets_v1")
    private val cursorKey = stringPreferencesKey("cycle_cursor")
    private val entryKey = stringPreferencesKey("cycle_entry")
    private val dayBrightnessKey = floatPreferencesKey("day_brightness")
    private val nightBrightnessKey = floatPreferencesKey("night_brightness")
    private val notificationRulesKey = stringPreferencesKey("notification_rules_v1")
    val nightMode = preferences.data.map { it[nightKey] ?: false }
    val scene = preferences.data.map { snapshot(it).applied }
    val editorScene = preferences.data.map { snapshot(it).draft }
    val library = preferences.data.map { readLibrary(it) }
    val brightness = preferences.data.map { BrightnessSettings(it[dayBrightnessKey]?.let(::clampBrightness), clampBrightness(it[nightBrightnessKey] ?: 0.08f)) }
    val notificationRules = preferences.data.map { NotificationRulesCodec.decode(it[notificationRulesKey]) }.distinctUntilChanged()
    suspend fun setNotificationRule(packageName: String, rule: NotificationRule) {
        preferences.edit { data ->
            data[notificationRulesKey] = NotificationRulesCodec.encode(NotificationRulesCodec.decode(data[notificationRulesKey]).withRule(packageName, rule))
        }
    }
    suspend fun setNotificationSelectionMode(selectedOnly: Boolean) {
        preferences.edit { data -> data[notificationRulesKey] = NotificationRulesCodec.encode(
            NotificationRulesCodec.decode(data[notificationRulesKey]).copy(selectedOnly = selectedOnly)) }
    }
    suspend fun setNotificationAppSelected(packageName: String, selected: Boolean) {
        preferences.edit { data -> data[notificationRulesKey] = NotificationRulesCodec.encode(
            NotificationRulesCodec.decode(data[notificationRulesKey]).withAppSelected(packageName, selected)) }
    }
    private fun references(data: Preferences) = listOfNotNull(data[draftKey]?.let(SceneCodec::decode), data[sceneKey]?.let(SceneCodec::decode))
    private fun readLibrary(data: Preferences) = PresetCodec.decode(data[presetsKey]).withSharedElements(references(data))
    private fun snapshot(data: Preferences): EditorSnapshot {
        val library = readLibrary(data)
        val useShared = data[sharedSceneKey] ?: false
        val rawApplied = SceneCodec.decode(data[sceneKey])
        val applied = if (useShared) library.sharedLayout?.addMissingTo(rawApplied) ?: rawApplied else rawApplied
        val hasDraft = data[draftKey] != null
        val draftShared = if (hasDraft) data[sharedDraftKey] ?: false else useShared
        val rawDraft = if (hasDraft) SceneCodec.decode(data[draftKey]) else applied
        val draft = if (draftShared) library.sharedLayout?.addMissingTo(rawDraft) ?: rawDraft else rawDraft
        val source = (if (hasDraft) data[draftSourceKey] else data[sceneSourceKey])?.takeIf { id -> library.presets.any { it.id == id } }
        return EditorSnapshot(applied, draft, useShared, draftShared, source)
    }
    suspend fun editorSnapshot(): EditorSnapshot = snapshot(preferences.data.first())
    suspend fun setBrightness(value: Float, night: Boolean) {
        preferences.edit { it[if (night) nightBrightnessKey else dayBrightnessKey] = clampBrightness(value) }
    }
    suspend fun updateLibrary(update: (PresetLibrary) -> PresetLibrary) {
        preferences.edit { data ->
            val next = update(readLibrary(data)).normalized().withSharedElements(references(data))
            if (data[sharedSceneKey] == true) next.sharedLayout?.addMissingTo(SceneCodec.decode(data[sceneKey]))
            if (data[draftKey] != null && data[sharedDraftKey] == true) next.sharedLayout?.addMissingTo(SceneCodec.decode(data[draftKey]))
            data[presetsKey] = PresetCodec.encode(next)
        }
    }

    /** 같은 진입 ID의 회전·화면 재생성에서는 선택을 유지하고 새 진입에서만 순환한다. */
    suspend fun selectForEntry(entry: String, advance: Boolean = false): PlaybackSelection {
        var result = PlaybackSelection(StandbyScene())
        preferences.edit { data ->
            val library = readLibrary(data)
            val cursor = data[cursorKey]
            val eligible = library.cycleEnabled && library.cycleIds.isNotEmpty()
            val id = if (!eligible) null else if (!advance && data[entryKey] == entry && cursor in library.cycleIds) cursor
                else nextPresetId(library, cursor)
            data[entryKey] = entry
            if (id != null) data[cursorKey] = id
            val preset = library.presets.firstOrNull { it.id == id }
            val displayed = preset?.let(library::displayScene)
                ?: library.displayScene(SceneCodec.decode(data[sceneKey]), data[sharedSceneKey] ?: false)
            result = PlaybackSelection(displayed, preset?.id,
                preset?.name ?: "현재 디자인", if (eligible) library.cycleIds.size else 0)
        }
        return result
    }

    suspend fun retainedMediaNames(): Set<String> {
        val data = preferences.data.first()
        return SceneCodec.decode(data[sceneKey]).mediaNames() + SceneCodec.decode(data[draftKey]).mediaNames() +
            readLibrary(data).mediaNames()
    }
    suspend fun setNightMode(enabled: Boolean) { preferences.edit { it[nightKey] = enabled } }
    suspend fun saveDraft(scene: StandbyScene, useSharedLayout: Boolean = false, sourcePresetId: String? = null) {
        preferences.edit {
            val library = readLibrary(it)
            val filled = if (useSharedLayout) library.sharedLayout?.addMissingTo(scene) ?: scene else scene
            it[presetsKey] = PresetCodec.encode(library)
            it[draftKey] = SceneCodec.encode(filled); it[sharedDraftKey] = useSharedLayout
            if (sourcePresetId == null) it.remove(draftSourceKey) else it[draftSourceKey] = sourcePresetId
        }
    }
    /** 현재 디자인과 선택 프리셋을 한 번에 저장해 일부만 반영되는 상태를 피한다. */
    suspend fun saveScene(scene: StandbyScene, useSharedLayout: Boolean = false, sourcePresetId: String? = null, updatePreset: Boolean = false) {
        preferences.edit { data ->
            var library = readLibrary(data)
            val filled = if (useSharedLayout) library.sharedLayout?.addMissingTo(scene) ?: scene else scene
            val selected = library.presets.firstOrNull { it.id == sourcePresetId }
            if (updatePreset) {
                require(selected != null) { "선택한 프리셋을 찾을 수 없습니다. 현재 화면만 저장해 주세요." }
                library = library.copy(presets = library.presets.map { preset ->
                    if (preset.id == selected.id) preset.copy(scene = filled.normalized(), useSharedLayout = useSharedLayout) else preset
                })
            }
            data[presetsKey] = PresetCodec.encode(library)
            data[sceneKey] = SceneCodec.encode(filled); data[sharedSceneKey] = useSharedLayout
            if (selected == null) data.remove(sceneSourceKey) else data[sceneSourceKey] = selected.id
            data.remove(draftKey); data.remove(sharedDraftKey); data.remove(draftSourceKey)
        }
    }
    suspend fun discardDraft() {
        preferences.edit {
            it[presetsKey] = PresetCodec.encode(readLibrary(it))
            it.remove(draftKey); it.remove(sharedDraftKey); it.remove(draftSourceKey)
        }
    }
}

data class EditorSnapshot(val applied: StandbyScene, val draft: StandbyScene, val appliedUsesSharedLayout: Boolean,
    val draftUsesSharedLayout: Boolean, val sourcePresetId: String?)

fun clampBrightness(value: Float) = bounded(value, 0.02f, 1f, 0.5f)
data class BrightnessSettings(val day: Float? = null, val night: Float = 0.08f)
