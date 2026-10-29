package dev.kwonsu.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ShiftTimeRequest(val initialTime: Long, val expectedStart: Long? = null)

@Composable
internal fun WorkShiftHost(night: Boolean, store: WorkShiftStore? = null, nowOverride: Long? = null,
    content: @Composable (WorkShiftDisplay?, Boolean, () -> Unit, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val repository = store ?: remember(context) { WorkShiftStore(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val now = nowOverride ?: clock
    var shift by remember(repository) { mutableStateOf<WorkShift?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var fireworks by remember { mutableStateOf<Long?>(null) }
    var timeRequest by remember { mutableStateOf<ShiftTimeRequest?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) {
        try { repository.state.collect { shift = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "출퇴근 상태를 불러오지 못했습니다." }
    }
    LaunchedEffect(lifecycle, nowOverride != null) {
        if (nowOverride == null) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { clock = System.currentTimeMillis(); delay(1000 - clock % 1000) }
        }
    }
    val display = shift?.display(now)
    LaunchedEffect(shift?.startedAt, shift?.checkedOutAt, display?.phase) {
        val current = shift
        if (current != null && current.autoComplete(now) != current) {
            try { repository.autoComplete(now, current.startedAt) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "자동 퇴근 상태를 저장하지 못했습니다." }
        }
    }
    val canCelebrate = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && windowFocused && timeRequest == null
    LaunchedEffect(shift?.startedAt, shift?.celebrated, display?.phase, canCelebrate) {
        val current = shift
        if (current != null && !current.celebrated && display?.phase == WorkShiftPhase.COMPLETE && canCelebrate) {
            fireworks = current.startedAt
            Diagnostics.record("work_shift_fireworks_started", "완료 폭죽 재생 시작")
        } else fireworks = null
    }
    fun persist(action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try { error = if (action()) null else "출퇴근 상태가 바뀌었습니다. 다시 시도해 주세요." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "출퇴근 상태를 저장하지 못했습니다. 다시 눌러 주세요." }
            finally { busy = false }
        }
    }
    Box(Modifier.fillMaxSize().testTag("work_shift_host")) {
        Box(Modifier.fillMaxSize().then(if (timeRequest != null) Modifier.clearAndSetSemantics {} else Modifier)) {
            content(display, busy, {
                if (!busy && display?.phase == WorkShiftPhase.READY) timeRequest = ShiftTimeRequest(nowOverride ?: System.currentTimeMillis())
            }, {
                val current = shift
                if (!busy && current != null && display?.phase in setOf(WorkShiftPhase.WORKING, WorkShiftPhase.COMPLETE)) {
                    timeRequest = ShiftTimeRequest(current.startedAt, current.startedAt)
                }
            })
        }
        timeRequest?.let { request -> WorkShiftTimePicker(request.initialTime, busy, { timeRequest = null }, editing = request.expectedStart != null) { selected ->
            persist {
                val savedAt = nowOverride ?: System.currentTimeMillis()
                val accepted = if (request.expectedStart == null) repository.clockIn(savedAt, selected)
                    else repository.changeStart(savedAt, selected, request.expectedStart)
                timeRequest = null
                accepted
            }
        } }
        fireworks?.let { token -> WorkShiftFireworks(token, night) {
            // 화면 회전·프리셋 전환·잠금 화면 가림으로 중단되면 다음 표시 때 다시 재생한다.
            scope.launch {
                try {
                    if (repository.claimCelebration(nowOverride ?: System.currentTimeMillis(), expectedStart = token)) {
                        Diagnostics.record("work_shift_fireworks_finished", "완료 폭죽을 끝까지 표시함")
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "폭죽 재생 상태를 저장하지 못했습니다." }
                finally { if (fireworks == token) fireworks = null }
            }
        } }
        error?.let { Text(it, Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.8f)).padding(8.dp), color = Color(0xFFFFC0AB)) }
    }
}
