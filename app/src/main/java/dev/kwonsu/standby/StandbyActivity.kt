package dev.kwonsu.standby

import android.app.KeyguardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import java.util.UUID

class StandbyActivity : ComponentActivity() {
    private var fold by mutableStateOf(FoldInfo())
    private var power by mutableStateOf(PowerState())
    private var entrySource by mutableStateOf("conditions")
    private var attempt: String? = null
    private var visit by mutableStateOf(PlaybackVisit(""))
    private var wakeReceiverRegistered = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> visit = visit.screenOff()
                Intent.ACTION_SCREEN_ON -> if (mayDisplay()) visit = visit.screenOn(UUID.randomUUID().toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readEntry(intent)
        savedInstanceState?.getString("playback_visit")?.let { visit = PlaybackVisit(it, savedInstanceState.getBoolean("playback_screen_off")) }
        if (!mayDisplay()) {
            Diagnostics.record("standby_rejected", "진입 시 조건 재확인 실패 · ${MonitorRuntime.conditions.value.description}")
            finishAndRemoveTask()
            return
        }
        if (getSystemService(PowerManager::class.java).isInteractive) visit = visit.screenOn(UUID.randomUUID().toString())
        ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        wakeReceiverRegistered = true
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setWindowAnimations(R.style.StandbyWindowAnimation)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        power = readPower(this)
        val preferences = SettingsStore(applicationContext)
        setContent {
            val night by preferences.nightMode.collectAsState(initial = false)
            val events by Diagnostics.events.collectAsState(initial = emptyList())
            val conditions by MonitorRuntime.conditions.collectAsState()
            val scope = rememberCoroutineScope()
            FoldTheme(night) {
                LandscapeFrame(fold, conditions.posture, display?.rotation ?: 0) { landscapeFold ->
                    StandbyPresentation(
                        settings = preferences, entryId = visit.id, fold = landscapeFold, power = power, night = night, source = entrySource,
                        locked = conditions.locked, events = events,
                        systemBrightness = (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0.02f, 1f),
                        applyBrightness = { brightness -> window.attributes = window.attributes.apply { screenBrightness = brightness } },
                        onNight = { scope.launch { preferences.setNightMode(!night) } },
                        onExit = {
                            MonitorRuntime.dismissed = true
                            Diagnostics.record("standby_closed", "사용자가 닫음 · 조건이 해제될 때까지 재진입하지 않음")
                            finishAndRemoveTask()
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            MonitorRuntime.conditions.collect { conditions ->
                if (!conditions.canShow) {
                    Diagnostics.record("standby_conditions_exit", conditions.description)
                    finishAndRemoveTask()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    powerStates(this@StandbyActivity).collect { current ->
                        power = current
                    }
                }
                launch {
                    WindowInfoTracker.getOrCreate(this@StandbyActivity).windowLayoutInfo(this@StandbyActivity).collect { layout ->
                        val feature = layout.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        val next = feature?.let {
                            FoldInfo(it.state == FoldingFeature.State.HALF_OPENED, it.orientation == FoldingFeature.Orientation.HORIZONTAL,
                                it.isSeparating, it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)
                        } ?: FoldInfo()
                        if (fold != next) {
                            fold = next
                            Diagnostics.record("fold_changed", "$next")
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readEntry(intent)
        if (!mayDisplay()) finishAndRemoveTask()
        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) acknowledgeResume()
    }

    private fun readEntry(intent: Intent) {
        entrySource = intent.getStringExtra("source") ?: "conditions"
        attempt = intent.getStringExtra("attempt")
        visit = PlaybackVisit(attempt ?: UUID.randomUUID().toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("playback_visit", visit.id)
        outState.putBoolean("playback_screen_off", visit.screenWasOff)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (mayDisplay()) acknowledgeResume() else finishAndRemoveTask()
    }

    private fun mayDisplay() = MonitorRuntime.running.value && MonitorRuntime.conditions.value.canShow &&
        isStandbyLocked(this) && readPower(this).connected

    private fun acknowledgeResume() {
        attempt?.let { MonitorRuntime.acknowledgedAttempt = it }
        Diagnostics.record("standby_resumed", "경로=$entrySource; 잠금=${getSystemService(KeyguardManager::class.java).isKeyguardLocked}; 화면켜짐=${getSystemService(PowerManager::class.java).isInteractive}; attempt=$attempt")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Diagnostics.record("standby_window_focused", "시계 창 포커스 획득 · 경로=$entrySource")
    }

    override fun onDestroy() {
        if (wakeReceiverRegistered) unregisterReceiver(wakeReceiver)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Diagnostics.record("standby_destroyed", "화면 재생성=$isChangingConfigurations")
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context, source: String, attempt: String? = null) = Intent(context, StandbyActivity::class.java)
            .putExtra("source", source).putExtra("attempt", attempt)
    }
}
