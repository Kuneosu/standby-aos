package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class ContentBoundsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val power = PowerState(percent = 64, plugged = 1, charging = true)
    @Test fun selectionHandlesNeverEraseClockPixelsAtZeroPadding() {
        val editing = mutableStateOf(false)
        val scene = StandbyScene(font = ClockFont.KKUKKUKK, textColor = 0xFF000000,
            background = BackgroundSpec(color = 0xFFA6E8CB), elements = listOf(SceneElement("clock", ElementKind.CLOCK,
                contentSize = 0.32f, horizontalAnchor = CanvasAnchor.END)))
        compose.setContent { FoldTheme { SceneCanvas(scene, power, LocalDateTime.of(2026, 10, 28, 14, 20),
            Modifier.fillMaxWidth().aspectRatio(2.45f), editing = editing.value, selectedId = "clock") } }
        val clean = capture("clock-clean.png")
        compose.runOnIdle { editing.value = true }
        val selected = capture("clock-selected.png")
        var inkPixels = 0; var missing = 0
        for (y in 0 until clean.height) for (x in 0 until clean.width) {
            if (android.graphics.Color.green(clean.getPixel(x, y)) < 40) {
                inkPixels++
                if (android.graphics.Color.green(selected.getPixel(x, y)) > 60) missing++
            }
        }
        assertTrue("선택 손잡이가 시계 픽셀 $missing / $inkPixels 개를 가렸습니다", missing == 0)
    }
    private fun capture(name: String): Bitmap {
        val image = compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "validation-0.12.2").apply { mkdirs() }
        File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }
    private fun ink(image: Bitmap): Rect {
        var left = image.width; var right = -1; var top = image.height; var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (android.graphics.Color.red(image.getPixel(x, y)) > 140) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        check(right >= left)
        return Rect(left, top, right + 1, bottom + 1)
    }

    @Test fun largeClockKeepsItsSizeAndPaddingIsInsideOuterBoxAcrossFontsAndTimes() {
        val old = SceneElement("clock", ElementKind.CLOCK, x = 0.18f, y = 0.06f, width = 0.82f, height = 0.68f)
        val scene = mutableStateOf(StandbyScene(textColor = 0xFFFFFFFF, elements = listOf(old)))
        val now = mutableStateOf(LocalDateTime.of(2026, 10, 28, 13, 9))
        var fit: () -> Unit = {}
        compose.setContent { FoldTheme { BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(2.45f)) {
            val sizer = rememberContentSizer(scene.value.font, 2.45f, now.value, power, maxHeight.value)
            fit = { scene.value = scene.value.copy(elements = listOf(sizer.fit(scene.value.elements.single())
                .copy(horizontalAnchor = CanvasAnchor.END, horizontalPadding = 0.0125f).anchored())) }
            SceneCanvas(scene.value, power, now.value, Modifier.fillMaxSize())
        } } }
        ClockFont.entries.forEach { font ->
            compose.runOnIdle { scene.value = scene.value.copy(font = font, elements = listOf(old)); now.value = LocalDateTime.of(2026, 10, 28, 13, 9) }
            val before = ink(capture("${font.name}-before.png"))
            compose.runOnIdle { fit() }
            val afterImage = capture("${font.name}-tight.png")
            val after = ink(afterImage)
            assertEquals("맞춤 후에도 선택한 시계 크기를 유지해야 합니다: $font", before.height().toFloat(), after.height().toFloat(), 3f)
            val canvas = compose.onNodeWithTag("scene_canvas").fetchSemanticsNode().boundsInRoot
            val box = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
            assertEquals(after.width().toFloat(), box.width * 0.975f, 3f)
            assertEquals(after.height().toFloat(), box.height, 3f)
            assertEquals(0f, canvas.right - box.right, 1f)
            assertEquals(box.width * 0.0125f, (afterImage.width - after.right).toFloat(), 3f)
            val size = scene.value.elements.single().contentSize
            listOf(11 to 11, 23 to 58).forEach { (hour, minute) ->
                compose.runOnIdle { now.value = now.value.withHour(hour).withMinute(minute) }
                val frame = capture("${font.name}-$hour-$minute.png")
                val updatedBox = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
                assertEquals(updatedBox.width * 0.0125f, (frame.width - ink(frame).right).toFloat(), 3f)
                assertEquals(size, scene.value.elements.single().contentSize)
            }
        }
    }

    @Test fun dateAndBatteryBoundsIncludeAllVisibleLinesAndZeroMarginTouchesEdge() {
        val scene = mutableStateOf(StandbyScene(textColor = 0xFFFFFFFF, elements = emptyList()))
        val now = LocalDateTime.of(2026, 10, 28, 13, 9)
        compose.setContent { FoldTheme { SceneCanvas(scene.value, power, now, Modifier.fillMaxWidth().aspectRatio(2.45f)) } }
        listOf(ElementKind.DATE, ElementKind.BATTERY).forEach { kind ->
            compose.runOnIdle { scene.value = scene.value.copy(elements = listOf(SceneElement("text", kind,
                contentSize = 0.2f, horizontalAnchor = CanvasAnchor.END, verticalAnchor = CanvasAnchor.START, verticalPadding = 0.02f))) }
            val frame = capture("tight-${kind.name}.png");val drawn = ink(frame)
            val box = compose.onNodeWithTag("element_text").fetchSemanticsNode().boundsInRoot
            assertEquals(drawn.width().toFloat(), box.width, 3f)
            assertEquals(drawn.height().toFloat(), box.height * 0.96f, 3f)
            assertTrue(frame.width - drawn.right <= 2)
            assertEquals(box.height * 0.02f, drawn.top.toFloat(), 3f)
        }
    }

    @Test fun sceneAndSharedContentGeometryRoundTripAndLegacyRemainsBoxBased() {
        val scene = StandbyScene(elements = listOf(SceneElement("clock", ElementKind.CLOCK, contentSize = 0.5f,
            horizontalAnchor = CanvasAnchor.END, verticalAnchor = CanvasAnchor.START, horizontalPadding = 0.0125f, verticalPadding = 0.02f)))
        assertEquals(scene, SceneCodec.decode(SceneCodec.encode(scene)))
        val layout = SharedLayout.fromScene(scene)
        assertEquals(layout, SharedLayoutCodec.decode(SharedLayoutCodec.encode(layout)))
        val old = SceneCodec.decode("""{"elements":[{"id":"clock","kind":"CLOCK"}]}""").elements.single()
        assertNull(old.contentSize); assertEquals(CanvasAnchor.FREE, old.horizontalAnchor)
        assertEquals(0f, old.horizontalPadding)
        val previous = SceneCodec.decode("""{"elements":[{"id":"clock","kind":"CLOCK","horizontalMargin":0.05,"verticalMargin":0.1}]}""").elements.single()
        assertEquals(0.05f, previous.horizontalPadding)
        assertEquals(0.1f, previous.verticalPadding)
        val previousShared = SharedLayoutCodec.decode("""{"placements":[{"kind":"CLOCK","horizontalMargin":0.05,"verticalMargin":0.1}]}""")!!.placements.single()
        assertEquals(0.05f, previousShared.horizontalPadding)
        assertEquals(0.1f, previousShared.verticalPadding)
    }

    @Test fun paddingGrowsTightBoxSymmetricallyWithoutShrinkingInkOrMovingFreeOrigin() {
        val scene = mutableStateOf(StandbyScene(textColor = 0xFFFFFFFF, elements = listOf(SceneElement("clock", ElementKind.CLOCK,
            x = 0.1f, y = 0.1f, contentSize = 0.3f))))
        compose.setContent { FoldTheme { SceneCanvas(scene.value, power, LocalDateTime.of(2026, 10, 28, 23, 58),
            Modifier.fillMaxWidth().aspectRatio(2.45f)) } }
        val before = ink(capture("padding-zero.png"))
        val origin = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot.topLeft
        listOf(0.05f, 0.15f).forEach { padding ->
            compose.runOnIdle { scene.value = scene.value.copy(elements = scene.value.elements.map {
                it.copy(horizontalPadding = padding, verticalPadding = padding)
            }) }
            val drawn = ink(capture("padding-${padding * 100}.png"))
            val box = compose.onNodeWithTag("element_clock").fetchSemanticsNode().boundsInRoot
            val canvas = compose.onNodeWithTag("scene_canvas").fetchSemanticsNode().boundsInRoot
            assertEquals(origin, box.topLeft)
            assertEquals(before.width().toFloat(), drawn.width().toFloat(), 2f)
            assertEquals(before.height().toFloat(), drawn.height().toFloat(), 2f)
            assertEquals(box.width * padding, drawn.left - (box.left - canvas.left), 2f)
            assertEquals(box.width * padding, box.right - canvas.left - drawn.right, 2f)
            assertEquals(box.height * padding, drawn.top - (box.top - canvas.top), 2f)
            assertEquals(box.height * padding, box.bottom - canvas.top - drawn.bottom, 2f)
        }
    }
}
