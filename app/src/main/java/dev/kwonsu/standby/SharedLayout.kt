package dev.kwonsu.standby

val sharedLayoutKinds = ElementKind.entries.toSet()

/** ID가 다른 프리셋끼리는 종류와 같은 종류 안의 순서로 배치를 대응한다. */
data class LayoutSlot(val kind: ElementKind, val occurrence: Int = 0)
data class SharedPlacement(
    val slot: LayoutSlot, val x: Float, val y: Float, val width: Float, val height: Float,
    val positionLocked: Boolean = false,
    val template: SceneElement? = null,
    val contentSize: Float? = null,
    val horizontalAnchor: CanvasAnchor = CanvasAnchor.FREE,
    val verticalAnchor: CanvasAnchor = CanvasAnchor.FREE,
    val horizontalPadding: Float = 0f,
    val verticalPadding: Float = 0f,
) {
    fun applyTo(element: SceneElement) = element.copy(x = x, y = y, width = width, height = height, positionLocked = positionLocked,
        contentSize = contentSize, horizontalAnchor = horizontalAnchor, verticalAnchor = verticalAnchor,
        horizontalPadding = horizontalPadding, verticalPadding = verticalPadding)
    fun normalized(): SharedPlacement {
        val element = applyTo(SceneElement("layout", slot.kind)).normalized()
        return from(slot, element).copy(template = template?.takeIf { it.kind == slot.kind }?.copy(sharedSlot = null)?.normalized())
    }
    companion object {
        fun from(slot: LayoutSlot, element: SceneElement) = SharedPlacement(slot, element.x, element.y, element.width, element.height,
            element.positionLocked, element.copy(sharedSlot = null).normalized(), element.contentSize,
            element.horizontalAnchor, element.verticalAnchor, element.horizontalPadding, element.verticalPadding)
    }
}

data class SharedLayout(val placements: List<SharedPlacement> = emptyList()) {
    fun normalized() = copy(placements = placements.filter { it.slot.kind in sharedLayoutKinds && it.slot.occurrence in 0 until StandbyScene.MAX_STORED_ELEMENTS }
        .distinctBy { it.slot }.take(StandbyScene.MAX_ELEMENTS).map { it.normalized() })

    fun placementFor(scene: StandbyScene): Map<String, SharedPlacement> {
        val bySlot = normalized().placements.associateBy { it.slot }
        return scene.layoutSlots().mapNotNull { (slot, element) -> bySlot[slot]?.let { element.id to it } }.toMap()
    }

    /** 이전 버전의 위치 정보에 기준 요소를 보충한다. 이미 저장된 기준 설정은 바꾸지 않는다. */
    fun withTemplatesFrom(references: List<StandbyScene>): SharedLayout = copy(placements = normalized().placements.map { placement ->
        if (placement.template != null) placement else placement.copy(template = references.firstNotNullOfOrNull { scene ->
            scene.layoutSlots().firstOrNull { it.first == placement.slot }?.second?.copy(sharedSlot = null)
        } ?: newElement("template_${placement.slot.kind}_${placement.slot.occurrence}", placement.slot.kind))
    }).normalized()

    fun mediaNames(): Set<String> = placements.mapNotNull { it.template?.media }.toSet()

    /** 없는 슬롯만 복사한다. 앞 번호의 불필요한 요소를 만들지 않고 저장 후에도 같은 슬롯을 유지한다. */
    fun addMissingTo(scene: StandbyScene): StandbyScene {
        val existing = scene.layoutSlots().map { it.first }.toSet()
        val ids = scene.elements.map { it.id }.toMutableSet()
        val missing = normalized().placements.filter { it.slot !in existing }.map { placement ->
            val stem = previewId(placement.slot)
            var id = stem
            var suffix = 1
            while (!ids.add(id)) id = "${stem}_${suffix++}"
            placement.applyTo(placement.template ?: newElement(id, placement.slot.kind))
                .copy(id = id, sharedSlot = placement.slot).normalized()
        }
        require(scene.elements.size + missing.size <= StandbyScene.MAX_STORED_ELEMENTS) {
            "공통 요소를 추가하면 화면 요소가 ${StandbyScene.MAX_STORED_ELEMENTS}개를 넘습니다. 해당 프리셋에서 사용하지 않는 요소를 정리해 주세요."
        }
        return scene.copy(elements = scene.elements + missing)
    }

    /** 없는 요소는 먼저 보충하고 기존 요소의 원래 배치는 표시할 때만 합성한다. */
    fun applyTo(scene: StandbyScene): StandbyScene {
        val filled = addMissingTo(scene)
        val matched = placementFor(filled)
        return filled.copy(elements = filled.elements.map { matched[it.id]?.applyTo(it) ?: it })
    }

    fun previewScene(reference: StandbyScene): StandbyScene = applyTo(reference)

    fun previewTargets(reference: StandbyScene): Map<String, SharedPlacement> {
        return placementFor(previewScene(reference))
    }

    private fun previewId(slot: LayoutSlot) = "shared_${slot.kind}_${slot.occurrence}"

    fun updateElement(reference: StandbyScene, edited: SceneElement): SharedLayout {
        val preview = previewScene(reference)
        val current = preview.elements.firstOrNull { it.id == edited.id } ?: return this
        val old = previewTargets(reference)[edited.id] ?: return this
        val next = SharedPlacement.from(old.slot, current.applyEdit(edited)).copy(template = old.template)
        return copy(placements = normalized().placements.map { if (it.slot == next.slot) next else it })
    }

    fun withSelection(reference: StandbyScene, slot: LayoutSlot, included: Boolean): SharedLayout {
        if (!included) return copy(placements = placements.filterNot { it.slot == slot }).normalized()
        if (placements.any { it.slot == slot }) return this
        val element = reference.layoutSlots().firstOrNull { it.first == slot }?.second ?: return this
        return copy(placements = placements + SharedPlacement.from(slot, element)).normalized()
    }

    companion object {
        fun fromScene(scene: StandbyScene, selectedIds: Set<String> = scene.elements.map { it.id }.toSet()) =
            SharedLayout(scene.layoutSlots().filter { it.second.id in selectedIds }
                .map { (slot, element) -> SharedPlacement.from(slot, element) }).normalized()
    }
}

fun StandbyScene.layoutSlots(): List<Pair<LayoutSlot, SceneElement>> {
    val counts = mutableMapOf<ElementKind, Int>()
    val reserved = elements.mapNotNull { it.sharedSlot }.toSet()
    return elements.filter { it.kind in sharedLayoutKinds }.map { element ->
        val slot = element.sharedSlot ?: run {
            var index = counts.getOrDefault(element.kind, 0)
            while (LayoutSlot(element.kind, index) in reserved) index++
            counts[element.kind] = index + 1
            LayoutSlot(element.kind, index)
        }
        slot to element
    }
}

/** 연결 중의 색상 편집·덮어쓰기가 프리셋의 원래 배치를 덮지 않게 한다. */
fun preserveSharedPlacement(original: StandbyScene, edited: StandbyScene, shared: SharedLayout?, matchByIdOnly: Boolean = false): StandbyScene {
    if (shared == null) return edited
    val sharedSlots = shared.normalized().placements.map { it.slot }.toSet()
    val originalSlots = original.layoutSlots().toMap()
    val originalIds = original.elements.associateBy { it.id }
    val placements = edited.layoutSlots().mapNotNull { (slot, element) ->
        val originalElement = originalIds[element.id]?.takeIf { it.kind == element.kind }
            ?: if (matchByIdOnly) null else originalSlots[slot]
        originalElement?.takeIf { slot in sharedSlots }?.let { element.id to SharedPlacement.from(slot, it) }
    }.toMap()
    return edited.copy(elements = edited.elements.map { placements[it.id]?.applyTo(it) ?: it })
}
