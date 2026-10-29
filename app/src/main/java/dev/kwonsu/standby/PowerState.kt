package dev.kwonsu.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class PowerState(val plugged: Int = 0, val percent: Int = -1, val charging: Boolean = false, val temperature: Float = 0f) {
    val connected: Boolean get() = plugged != 0
    val source: String get() = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "유선 전원"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "무선 충전"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "도크"
        0 -> "전원 연결 없음"
        else -> "외부 전원"
    }
    val label: String get() = if (!connected) "배터리 사용 중" else if (charging) "$source · 충전 중" else "$source · 충전 대기/완료"
}

private fun Intent?.toPower(): PowerState {
    if (this == null) return PowerState()
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    return PowerState(
        plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        percent = (getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale).coerceIn(-1, 100),
        charging = getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING,
        temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
    )
}

fun readPower(context: Context): PowerState = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).toPower()

fun powerStates(context: Context) = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { trySend(intent.toPower()) }
    }
    val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(sticky.toPower())
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()
