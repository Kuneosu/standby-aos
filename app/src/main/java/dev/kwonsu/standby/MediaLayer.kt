@file:Suppress("DEPRECATION")

package dev.kwonsu.standby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LoadedMedia(val bitmap: Bitmap? = null, val movie: Movie? = null)

@Composable
fun MediaLayer(name: String?, animated: Boolean, modifier: Modifier = Modifier, fit: PhotoFit = PhotoFit.CONTAIN,
    zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f, speed: Float = 1f, loop: Boolean = true,
    widthScale: Float = 1f, heightScale: Float = 1f,
) {
    val context = LocalContext.current
    val media by produceState<LoadedMedia?>(null, name, animated) {
        value = null
        value = withContext(Dispatchers.IO) {
            val file = MediaRepository(context).file(name)
            if (file?.isFile != true) return@withContext null
            runCatching {
                if (animated) LoadedMedia(movie = Movie.decodeFile(file.path))
                else LoadedMedia(bitmap = MediaRepository.decodePhoto(file))
            }.onFailure { Diagnostics.record("media_load_failed", "이미지 표시 실패: ${it.javaClass.simpleName}") }.getOrNull()
        }
    }
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { SceneMediaView(it) }, modifier = modifier, update = { view ->
        view.bind(media?.bitmap, media?.movie, fit, zoom, panX, panY, speed, loop, active, widthScale, heightScale)
    }, onRelease = { it.release() })
}

/** Movie의 시간 축을 직접 제어해서 GIF 속도와 1회 재생을 일관되게 적용한다. */
internal class SceneMediaView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var movie: Movie? = null
    private var fit = PhotoFit.CONTAIN
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var speed = 1f
    private var loop = true
    private var widthScale = 1f
    private var heightScale = 1f
    private var active = true
    private var started = SystemClock.uptimeMillis()
    private var pausedAt: Long? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun bind(bitmap: Bitmap?, movie: Movie?, fit: PhotoFit, zoom: Float, panX: Float, panY: Float, speed: Float, loop: Boolean, active: Boolean,
        widthScale: Float = 1f, heightScale: Float = 1f) {
        val reset = this.movie !== movie || this.speed != speed || this.loop != loop
        if (reset) { started = SystemClock.uptimeMillis(); pausedAt = if (active) null else started }
        else if (this.active && !active) pausedAt = SystemClock.uptimeMillis()
        else if (!this.active && active) { pausedAt?.let { started += SystemClock.uptimeMillis() - it }; pausedAt = null }
        this.bitmap = bitmap; this.movie = movie; this.fit = fit; this.zoom = zoom
        this.panX = panX; this.panY = panY; this.speed = speed; this.loop = loop; this.active = active
        this.widthScale = widthScale; this.heightScale = heightScale
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val mediaWidth = (movie?.width() ?: bitmap?.width ?: return).toFloat()
        val mediaHeight = (movie?.height() ?: bitmap?.height ?: return).toFloat()
        if (mediaWidth <= 0 || mediaHeight <= 0 || width == 0 || height == 0) return
        var sx = width / mediaWidth
        var sy = height / mediaHeight
        if (fit != PhotoFit.STRETCH) { val scale = if (fit == PhotoFit.COVER) maxOf(sx, sy) else minOf(sx, sy); sx = scale; sy = scale }
        sx *= zoom * widthScale; sy *= zoom * heightScale
        val dx = (width - mediaWidth * sx) / 2 + panX * width / 2
        val dy = (height - mediaHeight * sy) / 2 + panY * height / 2
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(dx, dy)
        canvas.scale(sx, sy)
        val animation = movie
        if (animation != null) {
            val elapsed = (pausedAt ?: SystemClock.uptimeMillis()) - started
            val frame = gifFrame(elapsed, animation.duration().coerceAtLeast(100), speed, loop)
            animation.setTime(frame.timeMillis)
            animation.draw(canvas, 0f, 0f, paint)
            if (active && !frame.finished && animation.duration() > 0) postInvalidateDelayed(33)
        } else bitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        canvas.restore()
    }

    fun release() { active = false; bitmap = null; movie = null }
}
