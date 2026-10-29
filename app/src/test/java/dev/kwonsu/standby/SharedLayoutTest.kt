package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class SharedLayoutTest {
    private val reference = StandbyScene(elements = listOf(
        SceneElement("clock-a", ElementKind.CLOCK, 0.12f, 0.18f, 0.4f, 0.2f, positionLocked = true),
        SceneElement("date-a", ElementKind.DATE, 0.15f, 0.5f, 0.35f, 0.1f),
        SceneElement("battery-a", ElementKind.BATTERY, 0.7f, 0.2f, 0.2f, 0.15f),
        SceneElement("brightness-a", ElementKind.BRIGHTNESS, 0.2f, 0.75f, 0.6f, 0.15f)))
    private val target = reference.copy(font = ClockFont.MAPLESTORY, textColor = 0xFFFFAA55,
        background = BackgroundSpec(kind = BackgroundKind.PRESET, preset = BackdropPreset.DUSK),
        elements = reference.elements.map { it.copy(id = it.id.replace("-a", "-b"), x = 0.3f, y = 0.1f, width = 0.3f,
            height = 0.12f, positionLocked = false, color = 0xFF55AAFF, opacity = 0.7f) } +
            SceneElement("gif", ElementKind.GIF, 0.35f, 0.3f, 0.2f, 0.4f, media = "00000000-0000-0000-0000-000000000001.gif", speed = 2f, loop = false))

    @Test fun onlyGeometryAndLockAreSharedWhileStyleMediaAndOrderStayLocal() {
        val displayed = SharedLayout.fromScene(reference).applyTo(target)
        assertEquals(target.font, displayed.font); assertEquals(target.textColor, displayed.textColor)
        assertEquals(target.background, displayed.background)
        assertEquals(target.elements.map { it.id }, displayed.elements.map { it.id })
        reference.elements.forEachIndexed { index, expected ->
            val original = target.elements[index]
            assertEquals(expected.x, displayed.elements[index].x)
            assertEquals(expected.y, displayed.elements[index].y)
            assertEquals(expected.width, displayed.elements[index].width)
            assertEquals(expected.height, displayed.elements[index].height)
            assertEquals(expected.positionLocked, displayed.elements[index].positionLocked)
            assertEquals(original.color, displayed.elements[index].color)
            assertEquals(original.opacity, displayed.elements[index].opacity)
        }
        assertEquals(target.elements.last(), displayed.elements.last())
    }

    @Test fun unlinkRestoresExactOriginalAndCommonUpdatesReachOnlyLinkedPresets() {
        val a = ScenePreset("a", "연결", target, true)
        val b = ScenePreset("b", "개별", target, false)
        val shared = SharedLayout.fromScene(reference)
        val library = PresetLibrary(listOf(a, b), sharedLayout = shared)
        assertEquals(target, library.displayScene(b))
        assertNotEquals(target, library.displayScene(a))
        assertEquals(target, library.displayScene(a.copy(useSharedLayout = false)))
        val changed = library.copy(sharedLayout = shared.copy(placements = shared.placements.map { it.copy(x = 0.05f) }))
        assertEquals(0.05f, changed.displayScene(a).elements.first().x)
        assertEquals(target, changed.displayScene(b))
        assertEquals(target, changed.presets.first().scene)
    }

    @Test fun absentElementsAreAddedAndMultipleKindsMatchByOccurrence() {
        val first = reference.elements.first()
        val shared = SharedLayout.fromScene(reference.copy(elements = listOf(first, first.copy(id = "second", x = 0.5f))))
        val input = target.copy(elements = listOf(first.copy(id = "local-1"), first.copy(id = "local-2"), target.elements.last()))
        val displayed = shared.applyTo(input)
        assertEquals(3, displayed.elements.size)
        assertEquals(0.12f, displayed.elements[0].x); assertEquals(0.5f, displayed.elements[1].x)
        assertEquals(input.elements.last(), displayed.elements.last())
        val filled = shared.applyTo(input.copy(elements = emptyList()))
        assertEquals(2, filled.elements.size)
        assertEquals(listOf(0.12f, 0.5f), filled.elements.map { it.x })
        assertEquals(filled, shared.applyTo(filled))
    }

    @Test fun selectedGifButtonsAndWorkGaugeShareOnlyTheirPlacement() {
        val scene = target.copy(elements = target.elements + newElement("close", ElementKind.CLOSE_BUTTON) + newElement("shift", ElementKind.WORK_SHIFT))
        val shared = SharedLayout.fromScene(scene, setOf("gif", "close", "shift"))
        assertEquals(setOf(ElementKind.GIF, ElementKind.CLOSE_BUTTON, ElementKind.WORK_SHIFT), shared.placements.map { it.slot.kind }.toSet())
        val local = scene.copy(elements = scene.elements.map { it.copy(x = 0f, y = 0f, color = 0xFFABCDEF, speed = 0.5f) })
        val displayed = shared.applyTo(local)
        assertEquals(local.elements.take(4), displayed.elements.take(4))
        displayed.elements.takeLast(3).forEachIndexed { index, element ->
            assertEquals(scene.elements.takeLast(3)[index].x, element.x)
            assertEquals(0xFFABCDEF, element.color); assertEquals(0.5f, element.speed)
            assertEquals(local.elements.takeLast(3)[index].media, element.media)
        }
    }

    @Test fun selectingOnlySecondOccurrenceNeverReassignsItToFirst() {
        val first = newElement("first", ElementKind.GIF).copy(x = 0.1f)
        val second = first.copy(id = "second", x = 0.6f)
        val scene = StandbyScene(elements = listOf(first, second))
        val selected = SharedLayout.fromScene(scene, setOf("second"))
        assertEquals(LayoutSlot(ElementKind.GIF, 1), selected.placements.single().slot)
        val target = scene.copy(elements = listOf(first.copy(id = "other-first", x = 0.2f), second.copy(id = "other-second", x = 0.3f)))
        assertEquals(0.2f, selected.applyTo(target).elements[0].x)
        assertEquals(0.6f, selected.applyTo(target).elements[1].x)
        assertEquals(selected, SharedLayout().withSelection(scene, LayoutSlot(ElementKind.GIF, 1), true))
        assertEquals(SharedLayout(), selected.withSelection(scene, LayoutSlot(ElementKind.GIF, 1), false))
        assertEquals(selected, selected.withSelection(scene, LayoutSlot(ElementKind.GIF, 1), true))
        assertEquals(selected, selected.withSelection(scene, LayoutSlot(ElementKind.GIF, 2), true))
        val added = selected.addMissingTo(scene.copy(elements = emptyList()))
        assertEquals(1, added.elements.size)
        assertEquals(LayoutSlot(ElementKind.GIF, 1), added.elements.single().sharedSlot)
        assertEquals(LayoutSlot(ElementKind.GIF, 1), added.layoutSlots().single().first)
        assertEquals(added, selected.applyTo(added))
        val withNewFirst = added.copy(elements = added.elements + first.copy(id = "local-first"))
        assertEquals(LayoutSlot(ElementKind.GIF, 0), withNewFirst.layoutSlots().last().first)
    }

    @Test fun overwritingALinkedPresetKeepsOriginalPlacementButAcceptsNewStyle() {
        val shared = SharedLayout.fromScene(reference)
        val edited = shared.applyTo(target).copy(font = ClockFont.KKUKKUKK, background = BackgroundSpec(color = 0xFF112233),
            elements = shared.applyTo(target).elements.map { it.copy(color = 0xFFABCDEF) })
        val kept = preserveSharedPlacement(target, edited, shared)
        assertEquals(target.elements.first().x, kept.elements.first().x)
        assertEquals(target.elements.first().positionLocked, kept.elements.first().positionLocked)
        assertEquals(ClockFont.KKUKKUKK, kept.font)
        assertEquals(0xFF112233, kept.background.color)
        assertEquals(0xFFABCDEF, kept.elements.first().color)
    }

    @Test fun localRemovalAndNewElementsDoNotStealAnotherElementsBackupPlacement() {
        val clock = target.elements.first()
        val original = target.copy(elements = listOf(clock, clock.copy(id = "second", x = 0.6f, width = 0.2f)))
        val edited = original.copy(elements = listOf(original.elements[1].copy(x = 0.01f), clock.copy(id = "new", x = 0.4f)))
        val kept = preserveSharedPlacement(original, edited, SharedLayout.fromScene(original), matchByIdOnly = true)
        assertEquals(0.6f, kept.elements.first().x)
        assertEquals(0.4f, kept.elements.last().x)
    }

    @Test fun missingElementCanBeEditedInCommonPreviewWithoutChangingReference() {
        val shared = SharedLayout.fromScene(reference)
        val empty = target.copy(elements = listOf(target.elements.last()))
        val preview = shared.previewScene(empty)
        assertEquals(5, preview.elements.size)
        val date = preview.elements.first { it.kind == ElementKind.DATE }
        val edited = shared.updateElement(empty, date.copy(y = 0.6f))
        assertEquals(0.6f, edited.placements.first { it.slot.kind == ElementKind.DATE }.y)
        assertEquals(5, edited.applyTo(empty).elements.size)
        assertEquals(empty.elements.single(), edited.applyTo(empty).elements.first())
        assertEquals(0.6f, edited.applyTo(empty).elements.first { it.kind == ElementKind.DATE }.y)
    }

    @Test fun malformedGeometryAndUnsupportedSlotsAreBounded() {
        val layout = SharedLayout(listOf(SharedPlacement(LayoutSlot(ElementKind.GIF, StandbyScene.MAX_STORED_ELEMENTS), 0f, 0f, 1f, 1f),
            SharedPlacement(LayoutSlot(ElementKind.CLOCK), Float.NaN, -1f, 2f, 0f),
            SharedPlacement(LayoutSlot(ElementKind.DATE, -1), 0f, 0f, 0.1f, 0.1f))).normalized()
        assertEquals(1, layout.placements.size)
        assertEquals(0f, layout.placements.single().x); assertEquals(0f, layout.placements.single().y)
        assertEquals(1f, layout.placements.single().width); assertEquals(0.06f, layout.placements.single().height)
    }

    @Test fun copiesMissingTemplateSettingsWithoutOverwritingExistingOrUnlinkedPresets() {
        val source = StandbyScene(elements = listOf(newElement("source-gif", ElementKind.GIF, "00000000-0000-0000-0000-000000000001.gif")
            .copy(speed = 1.75f, loop = false, opacity = 0.6f), newElement("source-shift", ElementKind.WORK_SHIFT)
            .copy(gaugeOrientation = GaugeOrientation.VERTICAL, color = 0xFFFF8844)))
        val shared = SharedLayout.fromScene(source)
        val localShift = source.elements.last().copy(id = "local", x = 0.01f, color = 0xFF112233, gaugeOrientation = GaugeOrientation.HORIZONTAL)
        val target = StandbyScene(elements = listOf(localShift))
        val library = PresetLibrary(listOf(ScenePreset("a", "연결", target, true), ScenePreset("b", "개별", target)), sharedLayout = shared).withSharedElements()
        val saved = library.presets.first().scene
        assertEquals(localShift, saved.elements.first())
        val added = saved.elements.last()
        assertEquals(source.elements.first().media, added.media); assertEquals(1.75f, added.speed); assertFalse(added.loop)
        assertEquals(target, library.presets.last().scene)
        assertEquals(library, library.withSharedElements())
        assertTrue(source.elements.first().media in library.mediaNames())
        val displayed = library.displayScene(library.presets.first())
        assertEquals(0xFF112233, displayed.elements.first().color)
        assertEquals(GaugeOrientation.HORIZONTAL, displayed.elements.first().gaugeOrientation)
    }

    @Test fun fillingTwentyLocalElementsPreservesAllAndStableIdsAvoidCollisions() {
        val source = StandbyScene(elements = listOf(newElement("source", ElementKind.WORK_SHIFT)))
        val shared = SharedLayout.fromScene(source)
        val full = StandbyScene(elements = (0 until 20).map { newElement(if (it == 0) "shared_WORK_SHIFT_0" else "clock-$it", ElementKind.CLOCK) })
        val added = shared.addMissingTo(full)
        assertEquals(21, added.normalized().elements.size)
        assertEquals(full.elements, added.elements.take(20))
        assertEquals("shared_WORK_SHIFT_0_1", added.elements.last().id)
        assertEquals(added, shared.addMissingTo(added))
        val forty = StandbyScene(elements = full.elements + (0 until 20).map { newElement("generated-$it", ElementKind.DATE).copy(sharedSlot = LayoutSlot(ElementKind.DATE, it)) })
        assertEquals(40, forty.normalized().elements.size)
        assertTrue(runCatching { shared.addMissingTo(forty) }.isFailure)
        assertEquals(40, forty.elements.size)
    }
}
