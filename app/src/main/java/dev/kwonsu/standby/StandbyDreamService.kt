package dev.kwonsu.standby

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.service.dreams.DreamService
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class StandbyDreamService : DreamService() {
    private var scope: CoroutineScope? = null
    private lateinit var clock: DreamClock

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = false
        clock = DreamClock(this).apply { setOnClickListener { finish() } }
        setContentView(clock)
        Diagnostics.record("dream_attached", "화면보호기 창 연결")
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Diagnostics.record("dream_started", "시스템 화면보호기 시작 · 충전 연결=${readPower(this).connected}")
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { active ->
            active.launch {
                var wasConnected = readPower(this@StandbyDreamService).connected
                powerStates(this@StandbyDreamService).collect {
                    clock.power = it
                    clock.invalidate()
                    if (wasConnected && !it.connected) { Diagnostics.record("dream_unplug_exit", "충전 해제로 화면보호기 종료"); finish() }
                    wasConnected = it.connected
                }
            }
            active.launch { while (true) { clock.invalidate(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
        }
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        Diagnostics.record("dream_stopped", "화면보호기 종료")
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        super.onDetachedFromWindow()
    }
}

private class DreamClock(context: Context) : View(context) {
    var power = PowerState()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
    init { contentDescription = "Standby Screen 화면보호기. 탭하여 종료"; isClickable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Diagnostics.record("dream_window_size", "${w}x${h}px · 크기에 맞춘 시계, 힌지 전용 분할 없음")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val now = LocalDateTime.now()
        val x = width / 2f + (now.minute % 5 - 2) * 2
        paint.color = Color.rgb(210, 75, 60)
        paint.textSize = minOf(width * 0.23f, height * 0.35f)
        canvas.drawText(now.format(DateTimeFormatter.ofPattern("HH:mm")), x, height * 0.49f, paint)
        paint.textSize = 16 * resources.displayMetrics.scaledDensity
        canvas.drawText("${power.label} · ${power.percent.coerceAtLeast(0)}%", x, height * 0.63f, paint)
        paint.textSize = 12 * resources.displayMetrics.scaledDensity
        canvas.drawText("화면보호기 · 탭하여 종료", x, height * 0.75f, paint)
    }
}
