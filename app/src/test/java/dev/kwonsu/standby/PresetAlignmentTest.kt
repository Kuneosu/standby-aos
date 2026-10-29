package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class PresetAlignmentTest {
    private val presets = listOf("a", "b", "c").map { ScenePreset(it, it, StandbyScene()) }

    @Test fun initialScreenOnDoesNotDoubleAdvanceButOffThenOnDoes() {
        val initial = PlaybackVisit("entry")
        assertEquals(initial, initial.screenOn("initial-turn-screen-on"))
        val waking = initial.screenOff().screenOn("wake")
        assertEquals("wake", waking.id)
        assertEquals(waking, waking.screenOn("duplicate-event"))
        assertEquals(waking, waking.copy())
    }

    @Test fun onlySelectedPresetsAppearInRequestedOrder() {
        val library = PresetLibrary(presets, listOf("c", "a"), true)
        assertEquals("c", nextPresetId(library, null))
        assertEquals("a", nextPresetId(library, "c"))
        assertEquals("c", nextPresetId(library, "a"))
        assertEquals("c", nextPresetId(library, "b"))
    }

    @Test fun emptyDisabledAndDeletedCyclesHaveDefinedFallbacks() {
        assertNull(nextPresetId(PresetLibrary(presets, listOf("a"), false), null))
        assertNull(nextPresetId(PresetLibrary(presets, emptyList(), true), null))
        val library = PresetLibrary(presets.take(2), listOf("c", "a", "a", "b"), true).normalized()
        assertEquals(listOf("a", "b"), library.cycleIds)
        assertEquals("a", nextPresetId(library, "c"))
    }

    @Test fun singlePresetCyclesToItself() {
        assertEquals("b", nextPresetId(PresetLibrary(presets, listOf("b"), true), "b"))
    }

    @Test fun centersSnapToOtherElementsAndCanvasIndependently() {
        val other = SceneElement("target", ElementKind.DATE, x = 0.6f, y = 0.4f, width = 0.2f, height = 0.2f)
        val candidate = other.copy(id = "moving", x = 0.59f, y = 0.395f)
        val result = alignCenters(candidate, listOf(other), 0.02f, 0.02f)
        assertEquals(0.7f, result.vertical!!, 0.00001f)
        assertEquals(0.5f, result.horizontal!!, 0.00001f)
        assertEquals(0.6f, result.element.x, 0.00001f)
        assertEquals(0.4f, result.element.y, 0.00001f)
    }

    @Test fun snapDoesNotAttractDistantElementsOrUseItsOwnCenter() {
        val old = SceneElement("same", ElementKind.CLOCK, x = 0.2f, y = 0.2f, width = 0.2f, height = 0.2f)
        val result = alignCenters(old.copy(x = 0.205f), listOf(old), 0.01f, 0.01f)
        assertNull(result.vertical); assertNull(result.horizontal)
        assertEquals(0.205f, result.element.x)
    }

    @Test fun lockedElementsKeepGeometryButAllowStyleAndUnlock() {
        val locked = SceneElement("locked", ElementKind.CLOCK, positionLocked = true)
        val changed = locked.applyEdit(locked.copy(x = 0.4f, y = 0.5f, width = 0.6f, color = 0xFFABCDEF))
        assertEquals(locked.x, changed.x); assertEquals(locked.y, changed.y); assertEquals(locked.width, changed.width)
        assertEquals(0xFFABCDEF, changed.color)
        assertEquals(0.4f, locked.applyEdit(locked.copy(x = 0.4f, positionLocked = false)).x)
        assertEquals(AlignmentResult(locked), alignCenters(locked, emptyList(), 1f, 1f))
    }

    @Test fun brightnessCannotTurnTheScreenCompletelyBlack() {
        assertEquals(0.02f, clampBrightness(-1f)); assertEquals(1f, clampBrightness(4f))
        assertEquals(0.5f, clampBrightness(Float.NaN))
    }
}
