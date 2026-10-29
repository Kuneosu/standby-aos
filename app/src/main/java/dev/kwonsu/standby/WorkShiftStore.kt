package dev.kwonsu.standby

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.ZoneId

private val Context.workShiftDataStore by preferencesDataStore("work_shift")

class WorkShiftStore(context: Context, private val preferences: DataStore<Preferences> = context.applicationContext.workShiftDataStore) {
    private val start = longPreferencesKey("started_at")
    private val end = longPreferencesKey("deadline")
    private val out = longPreferencesKey("checked_out_at")
    private val celebrated = booleanPreferencesKey("celebrated")
    val state = preferences.data.onStart { update { it.migrateLegacyCheckout() } }.map(::decode).distinctUntilChanged()

    private fun decode(p: Preferences) = WorkShift(p[start] ?: 0L, p[end] ?: 0L, p[out] ?: 0L, p[celebrated] ?: false)
    private suspend fun update(transform: (WorkShift) -> WorkShift): Boolean {
        var changed = false
        preferences.edit { data ->
            val before = decode(data)
            val next = transform(before)
            changed = next != before
            if (changed) { data[start] = next.startedAt; data[end] = next.deadline; data[out] = next.checkedOutAt; data[celebrated] = next.celebrated }
        }
        return changed
    }
    suspend fun clockIn(now: Long, selectedStart: Long, zone: ZoneId = ZoneId.systemDefault()) = update { it.clockIn(now, selectedStart, zone) }

    /** 같은 시각을 확정해도 성공이다. 다른 근무로 바뀐 오래된 수정 요청은 저장하지 않는다. */
    suspend fun changeStart(now: Long, selectedStart: Long, expectedStart: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        var accepted = false
        update { current ->
            if (current.startedAt != expectedStart) current
            else current.changeStart(now, selectedStart, zone)?.also { accepted = true } ?: current
        }
        return accepted
    }
    suspend fun autoComplete(now: Long, expectedStart: Long) = update {
        if (it.startedAt == expectedStart) it.autoComplete(now) else it
    }
    suspend fun claimCelebration(now: Long, expectedStart: Long? = null) = update {
        if (expectedStart == null || it.startedAt == expectedStart) it.claimCelebration(now) else it
    }
}
