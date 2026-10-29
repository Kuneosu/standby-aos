package dev.kwonsu.standby

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class GlassRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun backgroundIsBlurredOnlyInsideGlassAndSolidFillHasNoVerticalGradient() {
        val name = "${UUID.randomUUID()}.img"
        val media = File(compose.activity.filesDir, "scene_media/$name").apply { parentFile!!.mkdirs() }
        val pattern = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        for (y in 0 until pattern.height) for (x in 0 until pattern.width) {
            val shade = if (x / 8 % 2 == 0) 40 else 200
            pattern.setPixel(x, y, PixelColor.rgb(shade, shade, shade))
        }
        media.outputStream().use { pattern.compress(Bitmap.CompressFormat.PNG, 100, it) }
        pattern.recycle()
        val slider = SceneElement("glass", ElementKind.BRIGHTNESS, 0.1f, 0.3f, 0.8f, 0.4f)
        val scene = mutableStateOf(StandbyScene(
            background = BackgroundSpec(kind = BackgroundKind.PHOTO, media = name, fit = PhotoFit.STRETCH, dim = 0f),
            elements = listOf(slider)))
        try {
            compose.setContent { FoldTheme { SceneCanvas(scene.value, PowerState(), LocalDateTime.of(2026, 10, 26, 10, 30),
                Modifier.fillMaxWidth().height(240.dp), brightness = 0.5f) } }
            compose.waitUntil(5000) { range(capture(), 0.15f) > 120 }
            compose.onNodeWithTag("brightness_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(350)
            val glass = capture()
            // 사진 원본은 선명하게 남고, 유리 안의 줄무늬만 실제로 흐려져야 한다.
            assertTrue("유리 바깥 사진의 대비 유지", range(glass, 0.15f) > 120)
            assertTrue("유리 내부의 사진 블러", range(glass, 0.40f) < 35)
            assertTrue("블러 안에서도 원본 사진의 밝기를 유지", PixelColor.red(glass.getPixel(glass.width / 2, (glass.height * 0.4f).toInt())) in 100..160)
            save(glass, "photo-backdrop-blur.png")

            compose.runOnIdle { scene.value = scene.value.copy(background = BackgroundSpec(color = 0xFF202020)) }
            compose.mainClock.advanceTimeBy(32)
            val flat = capture()
            val top = flat.getPixel(flat.width / 2, (flat.height * 0.4f).toInt())
            val bottom = flat.getPixel(flat.width / 2, (flat.height * 0.6f).toInt())
            assertEquals("단색 배경에서는 슬라이더 위아래가 같은 색", top, bottom)
            assertEquals("무채색 유리 표면", PixelColor.red(top), PixelColor.blue(top))
            save(flat, "solid-no-gradient.png")
        } finally {
            compose.mainClock.autoAdvance = true
            compose.activityRule.scenario.close()
            media.delete()
        }
    }

    private fun capture(): Bitmap = compose.onNodeWithTag("scene_canvas").captureToImage().asAndroidBitmap()
    private fun range(bitmap: Bitmap, y: Float): Int {
        val values = (bitmap.width * 0.3f).toInt()..(bitmap.width * 0.7f).toInt()
        val shades = values.map { PixelColor.red(bitmap.getPixel(it, (bitmap.height * y).toInt())) }
        return shades.max() - shades.min()
    }
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File(compose.activity.filesDir, "validation-0.4.1").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
