package dev.kwonsu.standby

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val scene: StandbyScene = StandbyScene(), val applied: StandbyScene = StandbyScene(),
    val loading: Boolean = true, val busy: Boolean = false, val message: String = "",
    val library: PresetLibrary = PresetLibrary(),
    val useSharedLayout: Boolean = false, val appliedUsesSharedLayout: Boolean = false, val sourcePresetId: String? = null,
) {
    val dirty: Boolean get() = scene != applied || useSharedLayout != appliedUsesSharedLayout
    val displayScene: StandbyScene get() = library.displayScene(scene, useSharedLayout)
    val sharedElementIds: Set<String> get() = if (useSharedLayout) library.sharedLayout?.placementFor(displayScene)?.keys.orEmpty() else emptySet()
    val selectedPreset: ScenePreset? get() = library.presets.firstOrNull { it.id == sourcePresetId }
    val hasPresetChanges: Boolean get() = selectedPreset?.let { scene != it.scene || useSharedLayout != it.useSharedLayout } ?: false
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val media = MediaRepository(application)
    private val mutable = MutableStateFlow(EditorState())
    val state = mutable.asStateFlow()
    private var pendingDraft: Job? = null

    init { viewModelScope.launch {
        val snapshot = settings.editorSnapshot()
        mutable.value = EditorState(snapshot.draft, snapshot.applied, loading = false,
            library = settings.library.first(), useSharedLayout = snapshot.draftUsesSharedLayout,
            appliedUsesSharedLayout = snapshot.appliedUsesSharedLayout, sourcePresetId = snapshot.sourcePresetId,
            message = if (snapshot.draft != snapshot.applied || snapshot.draftUsesSharedLayout != snapshot.appliedUsesSharedLayout)
                "이전에 편집하던 내용을 복원했습니다. 저장하면 시계에 적용됩니다." else "")
        settings.library.collect { library ->
            val current = mutable.value
            mutable.value = current.copy(library = library,
                scene = if (current.useSharedLayout) library.sharedLayout?.addMissingTo(current.scene) ?: current.scene else current.scene,
                applied = if (current.appliedUsesSharedLayout) library.sharedLayout?.addMissingTo(current.applied) ?: current.applied else current.applied,
                sourcePresetId = current.sourcePresetId?.takeIf { id -> library.presets.any { it.id == id } })
        }
    } }

    fun change(update: (StandbyScene) -> StandbyScene) {
        if (mutable.value.busy || mutable.value.loading) return
        // 화면 콜백이 생성된 시점의 장면 대신 현재 편집 상태에 변경을 누적한다.
        val current = mutable.value
        val basis = if (current.useSharedLayout) current.library.sharedLayout?.addMissingTo(current.scene) ?: current.scene else current.scene
        val edited = update(basis).normalized()
        mutable.value = current.copy(scene = if (current.useSharedLayout)
            preserveSharedPlacement(basis, edited, current.library.sharedLayout, matchByIdOnly = true) else edited, message = "")
        persistDraft()
    }

    fun persistDraft(immediate: Boolean = false) {
        if (mutable.value.loading || mutable.value.busy) return
        pendingDraft?.cancel()
        val current = mutable.value
        pendingDraft = viewModelScope.launch {
            if (!immediate) delay(200)
            runCatching { settings.saveDraft(current.scene, current.useSharedLayout, current.sourcePresetId) }
                .onFailure { mutable.value = mutable.value.copy(message = "임시 저장하지 못했습니다. 저장 버튼으로 다시 시도해 주세요.") }
        }
    }

    fun import(uri: Uri, gif: Boolean) {
        if (mutable.value.busy || (gif && (mutable.value.scene.elements.count { it.sharedSlot == null } >= StandbyScene.MAX_ELEMENTS ||
                mutable.value.scene.elements.size >= StandbyScene.MAX_STORED_ELEMENTS))) return
        mutable.value = mutable.value.copy(busy = true, message = "파일 가져오는 중…")
        viewModelScope.launch {
            try {
                pendingDraft?.cancelAndJoin()
                val name = media.import(uri, gif)
                val scene = mutable.value.scene
                val next = if (gif) scene.copy(elements = scene.elements + newElement(UUID.randomUUID().toString(), ElementKind.GIF, name))
                else scene.copy(background = scene.background.copy(kind = BackgroundKind.PHOTO, media = name, zoom = 1f, panX = 0f, panY = 0f))
                settings.saveDraft(next, mutable.value.useSharedLayout, mutable.value.sourcePresetId)
                mutable.value = mutable.value.copy(scene = next, message = if (gif) "GIF를 추가했습니다. 요소 목록에서 선택해 배치해 주세요." else "사진을 가져왔습니다.")
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(message = error.message ?: "파일을 가져오지 못했습니다.")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun save(updatePreset: Boolean = false, onSuccess: () -> Unit = {}) {
        if (mutable.value.busy || mutable.value.loading) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                pendingDraft?.cancelAndJoin()
                val scene = mutable.value.scene.normalized()
                val useShared = mutable.value.useSharedLayout
                val selected = mutable.value.selectedPreset
                settings.saveScene(scene, useShared, selected?.id, updatePreset)
                val saved = settings.editorSnapshot().applied
                mutable.value = mutable.value.copy(scene = saved, applied = saved, appliedUsesSharedLayout = useShared,
                    library = settings.library.first(), sourcePresetId = selected?.id,
                    message = if (updatePreset) "현재 화면과 ‘${selected?.name}’ 프리셋에 저장했습니다."
                        else "현재 화면을 저장했습니다. 다음 스탠바이 화면부터 적용됩니다.")
                runCatching { media.removeUnused(settings.retainedMediaNames()) }
                Diagnostics.record("scene_saved", "폰트=${scene.font}; 배경=${scene.background.kind}; 요소=${scene.elements.size}; GIF=${scene.elements.count { it.kind == ElementKind.GIF }}")
                onSuccess()
            } catch (error: Exception) { mutable.value = mutable.value.copy(message = error.message ?: "저장하지 못했습니다. 저장 공간을 확인하고 다시 시도해 주세요.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun discard(onSuccess: () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                pendingDraft?.cancelAndJoin()
                settings.discardDraft()
                val saved = settings.editorSnapshot()
                mutable.value = mutable.value.copy(scene = saved.applied, applied = saved.applied, useSharedLayout = saved.appliedUsesSharedLayout,
                    appliedUsesSharedLayout = saved.appliedUsesSharedLayout, sourcePresetId = saved.sourcePresetId)
                runCatching { media.removeUnused(settings.retainedMediaNames()) }
                onSuccess()
            } catch (_: Exception) { mutable.value = mutable.value.copy(message = "편집 취소에 실패했습니다. 다시 시도해 주세요.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun preset(action: PresetAction) {
        if (mutable.value.busy || mutable.value.loading) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                pendingDraft?.cancelAndJoin()
                val current = mutable.value
                val scene = current.scene
                settings.saveDraft(scene, current.useSharedLayout, current.sourcePresetId)
                if (action is PresetAction.Load) {
                    val saved = settings.library.first().presets.firstOrNull { it.id == action.id } ?: error("프리셋을 찾을 수 없습니다.")
                    settings.saveDraft(saved.scene, saved.useSharedLayout, saved.id)
                    mutable.value = mutable.value.copy(scene = saved.scene, useSharedLayout = saved.useSharedLayout, sourcePresetId = saved.id,
                        message = "‘${saved.name}’을 불러왔습니다." + if (saved.useSharedLayout) " 공통 배치로 표시하며 원래 배치는 보존합니다." else " 저장하면 현재 디자인에 적용됩니다.")
                } else {
                    settings.updateLibrary { library -> when (action) {
                        is PresetAction.Save -> {
                            require(action.name.isNotBlank()) { "프리셋 이름을 입력해 주세요." }
                            if (action.id == null) {
                                require(library.presets.size < PresetLibrary.MAX_PRESETS) { "프리셋은 최대 ${PresetLibrary.MAX_PRESETS}개까지 저장할 수 있습니다." }
                                library.copy(presets = library.presets + ScenePreset(UUID.randomUUID().toString(), action.name, scene, current.useSharedLayout))
                            } else library.copy(presets = library.presets.map { if (it.id == action.id) it.copy(name = action.name,
                                scene = if (it.useSharedLayout) preserveSharedPlacement(it.scene, scene, library.sharedLayout) else scene) else it })
                        }
                        is PresetAction.Rename -> library.copy(presets = library.presets.map { if (it.id == action.id) it.copy(name = action.name) else it })
                        is PresetAction.Delete -> library.copy(presets = library.presets.filterNot { it.id == action.id }, cycleIds = library.cycleIds.filterNot { it == action.id })
                        is PresetAction.Include -> library.copy(cycleIds = if (action.include) library.cycleIds + action.id else library.cycleIds.filterNot { it == action.id })
                        is PresetAction.Enable -> library.copy(cycleEnabled = action.enabled)
                        is PresetAction.Move -> {
                            val list = library.cycleIds.toMutableList()
                            val from = list.indexOf(action.id)
                            if (from >= 0) { val to = (from + action.delta).coerceIn(0, list.lastIndex); list.add(to, list.removeAt(from)) }
                            library.copy(cycleIds = list)
                        }
                        is PresetAction.Load -> library
                        is PresetAction.SaveSharedLayout -> {
                            val shared = action.layout.normalized()
                            library.copy(sharedLayout = shared, presets = library.presets.map {
                                if (library.sharedLayout == null && it.id == current.sourcePresetId) it.copy(useSharedLayout = true) else it
                            })
                        }
                        is PresetAction.UseSharedLayout -> {
                            require(!action.enabled || library.sharedLayout != null) { "공통 배치를 먼저 만들어 주세요." }
                            library.copy(presets = library.presets.map { if (it.id == action.id) it.copy(useSharedLayout = action.enabled) else it })
                        }
                    } }
                    val changeCurrentLink = when (action) {
                        is PresetAction.SaveSharedLayout -> if (current.library.sharedLayout == null) true else null
                        is PresetAction.UseSharedLayout -> if (action.id == null || action.id == current.sourcePresetId) action.enabled else null
                        else -> null
                    }
                    if (changeCurrentLink != null) {
                        val next = if (changeCurrentLink) settings.library.first().sharedLayout?.addMissingTo(scene) ?: scene else scene
                        settings.saveDraft(next, changeCurrentLink, current.sourcePresetId)
                        mutable.value = mutable.value.copy(scene = next, useSharedLayout = changeCurrentLink)
                    }
                    mutable.value = mutable.value.copy(message = when (action) {
                        is PresetAction.SaveSharedLayout -> "공통 배치를 저장했습니다. 연결된 모든 프리셋에 적용됩니다."
                        is PresetAction.UseSharedLayout -> if (action.enabled) "공통 배치에 연결했습니다. 원래 배치는 보존됩니다." else "공통 배치 연결을 해제했습니다. 원래 배치로 돌아갑니다."
                        else -> "프리셋 설정을 저장했습니다."
                    })
                }
                runCatching { media.removeUnused(settings.retainedMediaNames()) }
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(message = error.message ?: "프리셋 설정을 저장하지 못했습니다.")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
