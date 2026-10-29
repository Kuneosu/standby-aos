package dev.kwonsu.standby

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID

class ChargeMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var conditionsObserver: StandbyConditionsObserver? = null
    private val gate = StandbyGate()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "자동 스탠바이", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("잠금 · 충전 · 가로 자세를 기다리는 중"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (!observing) {
            observing = true
            MonitorRuntime.running.value = true
            MonitorRuntime.dismissed = false
            conditionsObserver = StandbyConditionsObserver(this, scope).also { it.start() }
            Diagnostics.record("monitor_started", "잠금 · 충전 · 가로를 지속 확인 · 800ms 안정 후 진입")
            scope.launch {
                var previous: StandbyConditions? = null
                while (isActive) {
                    val conditions = MonitorRuntime.conditions.value
                    if (!conditions.canShow) MonitorRuntime.dismissed = false
                    if (MonitorRuntime.dismissed) gate.dismiss()
                    if (previous != conditions) {
                        previous = conditions
                        setStatus("감시 중 · ${conditions.waitingReason}")
                    }
                    when (gate.update(conditions, SystemClock.elapsedRealtime())) {
                        GateAction.ENTER -> attemptLaunch()
                        GateAction.EXIT -> Diagnostics.record("gate_exit", conditions.description)
                        GateAction.NONE -> Unit
                    }
                    delay(100)
                }
            }
        }
        // 강제 종료·재부팅 뒤 몰래 감시를 재개하지 않는다. 사용자가 앱에서 다시 켠다.
        return START_NOT_STICKY
    }

    private fun attemptLaunch() {
        if (!MonitorRuntime.conditions.value.canShow || !isStandbyLocked(this) || !readPower(this).connected) return
        if (!Settings.canDrawOverlays(this)) {
            Diagnostics.record("launch_needs_permission", "자동 실행 권한 없음 · 설정 안내")
            setStatus("자동 실행 권한 필요 · 알림에서 설정 열기")
            return
        }
        val attempt = UUID.randomUUID().toString()
        Diagnostics.record("launch_attempt", "${MonitorRuntime.conditions.value.description} · attempt=$attempt")
        try {
            startActivity(StandbyActivity.intent(this, "conditions", attempt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            scope.launch {
                delay(2500)
                if (!MonitorRuntime.conditions.value.canShow) return@launch
                if (MonitorRuntime.acknowledgedAttempt == attempt) {
                    Diagnostics.record("launch_acknowledged", "Activity 활성화 확인 · attempt=$attempt")
                    setStatus("시계 표시 중 · 잠금 해제 또는 조건 해제 시 종료")
                } else {
                    Diagnostics.record("launch_unconfirmed", "Activity 활성화 미확인 · attempt=$attempt")
                    setStatus("자동 표시 미확인 · 알림에서 설정 확인")
                }
            }
        } catch (error: RuntimeException) {
            Diagnostics.record("launch_failed", "${error.javaClass.simpleName}: ${error.message}")
            setStatus("자동 실행 차단 · 알림에서 설정 확인")
        }
    }

    private fun setStatus(value: String) {
        MonitorRuntime.status.value = value
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(value))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_standby)
        .setContentTitle("Standby Screen · 자동 감시")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "감시 종료", PendingIntent.getService(this, 2, Intent(this, ChargeMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() {
        conditionsObserver?.stop()
        scope.cancel()
        MonitorRuntime.running.value = false
        MonitorRuntime.status.value = "자동 스탠바이 꺼짐"
        Diagnostics.record("monitor_stopped", "자동 스탠바이 감시 종료")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "charge_monitor"
        const val NOTIFICATION_ID = 10
        const val ACTION_STOP = "dev.kwonsu.standby.STOP_MONITOR"
    }
}
