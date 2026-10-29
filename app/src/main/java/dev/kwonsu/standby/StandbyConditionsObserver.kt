package dev.kwonsu.standby

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.OrientationEventListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun isStandbyLocked(context: Context): Boolean {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    return keyguard.isKeyguardLocked && (!keyguard.isDeviceSecure || keyguard.isDeviceLocked)
}

/** foreground service가 소유한다. 충전하지 않을 때는 자세 센서도 중지한다. */
class StandbyConditionsObserver(private val context: Context, private val scope: CoroutineScope) {
    private var connected = false
    private var posture = DevicePosture.UNKNOWN
    private var sensing = false
    private var powerJob: Job? = null
    private var lockJob: Job? = null
    private var renewedAt = 0L
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FoldStandby:locked-charge-posture")
        .apply { setReferenceCounted(false) }

    private val orientation = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
        override fun onOrientationChanged(angle: Int) {
            val next = classifyPosture(angle, posture)
            if (posture != next) {
                posture = next
                Diagnostics.record("posture_changed", "$next · 물리 각도=$angle")
                refresh()
            }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh() }
    }

    fun start() {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        powerJob = scope.launch {
            powerStates(context).collect { power ->
                connected = power.connected
                if (connected && !sensing) {
                    sensing = orientation.canDetectOrientation()
                    if (sensing) orientation.enable()
                    else Diagnostics.record("posture_unavailable", "자세 센서 없음 · 자동 진입하지 않음")
                } else if (!connected && sensing) {
                    orientation.disable()
                    sensing = false
                    posture = DevicePosture.UNKNOWN
                }
                refresh()
            }
        }
        // 생체 인증으로 가림 상태에서 잠금이 풀리는 경우도 확인한다.
        lockJob = scope.launch { while (isActive) { refresh(); delay(250) } }
    }

    private fun refresh() {
        val current = StandbyConditions(isStandbyLocked(context), connected, posture)
        if (MonitorRuntime.conditions.value != current) {
            MonitorRuntime.conditions.value = current
            Diagnostics.record("conditions_changed", current.description)
        }
        // Fold6 가속도계는 non-wakeup 센서다. 충전·잠금 중에만 CPU를 유지해 화면이 꺼져도 자세 변화를 받는다.
        val needsCpu = current.charging && current.locked
        val now = SystemClock.elapsedRealtime()
        if (needsCpu && (!wakeLock.isHeld || now - renewedAt >= 5 * 60_000)) {
            wakeLock.acquire(10 * 60_000L)
            renewedAt = now
        } else if (!needsCpu && wakeLock.isHeld) wakeLock.release()
    }

    fun stop() {
        powerJob?.cancel()
        lockJob?.cancel()
        orientation.disable()
        context.unregisterReceiver(receiver)
        if (wakeLock.isHeld) wakeLock.release()
        MonitorRuntime.conditions.value = StandbyConditions()
    }
}
