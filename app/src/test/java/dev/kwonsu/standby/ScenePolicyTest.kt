package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class ScenePolicyTest {
    @Test fun tightResizeKeepsOppositeAnchoredEdgesAndFontProportion() {
        val e = SceneElement("clock", ElementKind.CLOCK, x = 0.48f, y = 0.3f, width = 0.5f, height = 0.3f,
            contentSize = 0.4f, horizontalAnchor = CanvasAnchor.END, horizontalPadding = 0.02f)
        val larger = e.resizedFrom(ResizeCorner.TOP_LEFT, -0.1f, -0.05f)
        assertEquals(0.98f, larger.x + larger.width, 0.0001f)
        assertEquals(e.width / e.height, larger.width / larger.height, 0.0001f)
        assertTrue(larger.contentSize!! > e.contentSize!!)
        assertEquals(CanvasAnchor.END, larger.horizontalAnchor)
        assertEquals(e.copy(positionLocked = true), e.copy(positionLocked = true).applyEdit(larger.copy(positionLocked = true)))
    }
    @Test fun internalPaddingNeverMovesOuterAnchoredEdges() {
        val e = SceneElement("clock", ElementKind.CLOCK, width = 0.4f, height = 0.2f, contentSize = 0.3f,
            horizontalAnchor = CanvasAnchor.END, verticalAnchor = CanvasAnchor.START, horizontalPadding = 0.0125f, verticalPadding = 0.03f)
        assertEquals(0f, 1f - e.anchored().x - e.width, 0.0001f)
        assertEquals(0f, 1f - e.copy(width = 0.6f).anchored().x - 0.6f, 0.0001f)
        assertEquals(0f, e.anchored().y, 0.0001f)
        assertEquals(0.1f, e.copy(x = 0.1f, horizontalAnchor = CanvasAnchor.FREE).anchored().x)
    }
    @Test fun commonContentSizeAndMarginsKeepPresetStyleAndRestoreOriginalPlacementWhenUnlinked() {
        val original = SceneElement("clock", ElementKind.CLOCK, textSize = 0.12f, textAlignment = ElementTextAlignment.LEFT)
        val source = original.copy(contentSize = 0.5f, horizontalAnchor = CanvasAnchor.END, horizontalPadding = 0.02f)
        val layout = SharedLayout.fromScene(StandbyScene(elements = listOf(source)))
        val target = StandbyScene(elements = listOf(original))
        val display = layout.applyTo(target).elements.single()
        assertEquals(0.5f, display.contentSize!!)
        assertEquals(0.02f, display.horizontalPadding)
        assertEquals(original.textAlignment, display.textAlignment)
        assertEquals(original.textSize, display.textSize)
        assertEquals(target, preserveSharedPlacement(target, target.copy(elements = listOf(display)), layout))
    }
    @Test fun resizingFromInsidePreservesRightAndBottomEdgesAndMinimumSize() {
        val e = SceneElement("clock", ElementKind.CLOCK, x = 0.5f, y = 0.5f, width = 0.5f, height = 0.5f)
        val larger = e.resizedFrom(ResizeCorner.TOP_LEFT, -0.2f, -0.1f)
        assertEquals(0.7f, larger.width, 0.0001f)
        assertEquals(1f, larger.x + larger.width, 0.0001f)
        assertEquals(1f, larger.y + larger.height, 0.0001f)
        val minimum = e.resizedFrom(ResizeCorner.TOP_LEFT, 10f, 10f)
        assertEquals(0.06f, minimum.width, 0.0001f)
        assertEquals(1f, minimum.x + minimum.width, 0.0001f)
        assertEquals(minimum, minimum.resizedFrom(ResizeCorner.BOTTOM_RIGHT, 0f, 0f))
        assertEquals(e.copy(positionLocked = true), e.copy(positionLocked = true).resizedFrom(ResizeCorner.TOP_LEFT, -1f, -1f))
    }
    @Test fun independentTextSizeSurvivesBoxChangesAndInvalidValuesAreBounded() {
        val e = SceneElement("clock", ElementKind.CLOCK, textSize = 0.2f, textAlignment = ElementTextAlignment.RIGHT)
        assertEquals(0.2f, e.copy(width = 0.7f, height = 0.8f).normalized().textSize!!)
        assertEquals(ElementTextAlignment.RIGHT, e.resizedFrom(ResizeCorner.BOTTOM_RIGHT, 0.1f, 0.1f).textAlignment)
        assertNull(e.copy(textSize = null).normalized().textSize)
        assertEquals(0.16f, e.copy(textSize = Float.NaN).normalized().textSize!!)
    }
    @Test fun draggedAndResizedElementStaysInsideCanvas() {
        val e = SceneElement("test", ElementKind.CLOCK, x = 0.9f, y = -1f, width = 0.7f, height = 2f).normalized()
        assertEquals(0.3f, e.x, 0.0001f)
        assertEquals(0f, e.y)
        assertEquals(1f, e.height)
        assertTrue(e.x + e.width <= 1f)
    }
    @Test fun invalidNumbersCannotBreakLayoutOrPlayback() {
        val e = SceneElement("test", ElementKind.GIF, x = Float.NaN, height = Float.POSITIVE_INFINITY, speed = Float.NaN).normalized()
        assertEquals(0f, e.x)
        assertEquals(0.25f, e.height)
        assertEquals(1f, e.speed)
    }
    @Test fun mediaNamesCannotEscapeOwnedDirectory() {
        assertNull(validMediaName("../../outside.gif"))
        assertNull(validMediaName("/test.gif"))
        val name = "d6b71c24-e10f-465a-aa61-408d270787b3.gif"
        assertEquals(name, validMediaName(name))
    }
    @Test fun hexAcceptsExactlySixDigits() {
        assertEquals(0xFFAABBCC, parseHexColor("#aabbcc"))
        assertEquals(0xFF000000, parseHexColor("000000"))
        assertNull(parseHexColor("00ff"))
        assertNull(parseHexColor("ffzz00"))
    }
    @Test fun gifSpeedChangesTimeline() {
        assertEquals(125, gifFrame(500, 1000, 0.25f, true).timeMillis)
        assertEquals(500, gifFrame(500, 1000, 3f, true).timeMillis)
        assertEquals(750, gifFrame(500, 2000, 1.5f, true).timeMillis)
    }
    @Test fun oneShotStopsOnLastFrame() {
        val end = gifFrame(700, 900, 2f, false)
        assertEquals(899, end.timeMillis)
        assertTrue(end.finished)
        assertEquals(end, gifFrame(8000, 900, 2f, false))
    }
    @Test fun loopContinuesBeyondDuration() {
        val frame = gifFrame(1000, 900, 1f, true)
        assertEquals(100, frame.timeMillis)
        assertFalse(frame.finished)
    }
    @Test fun emptySceneStaysEmptyAndDuplicatesAreRemoved() {
        assertTrue(StandbyScene(elements = emptyList()).normalized().elements.isEmpty())
        val element = newElement("same", ElementKind.CLOCK)
        assertEquals(1, StandbyScene(elements = listOf(element, element)).normalized().elements.size)
    }
    @Test fun customColorsDoNotBecomeTransparent() {
        assertEquals(0xFF123456, StandbyScene(textColor = 0x00123456).normalized().textColor)
    }
    @Test fun backgroundTransformsStayWithinControls() {
        val b = BackgroundSpec(zoom = 10f, panX = -10f, widthScale = Float.NaN, heightScale = 0f).normalized()
        assertEquals(3f, b.zoom)
        assertEquals(-1f, b.panX)
        assertEquals(1f, b.widthScale)
        assertEquals(0.5f, b.heightScale)
    }
}
