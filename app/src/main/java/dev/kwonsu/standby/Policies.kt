package dev.kwonsu.standby

enum class DevicePosture { UNKNOWN, PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE;
    val isLandscape: Boolean get() = this == LANDSCAPE || this == REVERSE_LANDSCAPE
}

/** 잠금 화면의 회전 고정과 무관하게 물리 각도를 판정한다. 경계에는 여유각을 둔다. */
fun classifyPosture(angle: Int, previous: DevicePosture): DevicePosture = when {
    angle !in 0..359 -> DevicePosture.UNKNOWN
    angle in 60..120 || (previous == DevicePosture.REVERSE_LANDSCAPE && angle in 45..135) -> DevicePosture.REVERSE_LANDSCAPE
    angle in 240..300 || (previous == DevicePosture.LANDSCAPE && angle in 225..315) -> DevicePosture.LANDSCAPE
    else -> DevicePosture.PORTRAIT
}

data class StandbyConditions(
    val locked: Boolean = false,
    val charging: Boolean = false,
    val posture: DevicePosture = DevicePosture.UNKNOWN,
) {
    val canShow: Boolean get() = locked && charging && posture.isLandscape
    val description: String get() = "잠금=$locked; 전원연결=$charging; 자세=$posture"
    val waitingReason: String get() = when {
        !charging -> "충전 연결 대기"
        !locked -> "화면 잠금 대기"
        !posture.isLandscape -> "가로 자세 대기"
        else -> "잠금 · 충전 · 가로 확인"
    }
}

enum class GateAction { NONE, ENTER, EXIT }

/** 조건이 안정된 뒤 한 번만 진입한다. 하나라도 해제되면 즉시 종료한다. */
class StandbyGate(private val settleMillis: Long = 800) {
    private var readySince: Long? = null
    private var entered = false
    private var dismissed = false

    fun dismiss() { dismissed = true }

    fun update(conditions: StandbyConditions, nowMillis: Long): GateAction {
        if (!conditions.canShow) {
            val exit = entered
            readySince = null
            entered = false
            dismissed = false
            return if (exit) GateAction.EXIT else GateAction.NONE
        }
        if (readySince == null) readySince = nowMillis
        if (!entered && !dismissed && nowMillis - readySince!! >= settleMillis) {
            entered = true
            return GateAction.ENTER
        }
        return GateAction.NONE
    }
}

enum class DisplayLayout { COMPACT, WIDE, TABLETOP, BOOK }

data class FoldInfo(
    val halfOpened: Boolean = false,
    val horizontal: Boolean = false,
    val separating: Boolean = false,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val description: String get() = when {
        halfOpened && horizontal -> "탁상 자세 · 수평 힌지"
        halfOpened -> "책 자세 · 수직 힌지"
        separating -> "분리 영역 감지"
        else -> "접힘 정보 없음 또는 펼침"
    }
}

fun chooseLayout(widthDp: Float, heightDp: Float, fold: FoldInfo): DisplayLayout = when {
    fold.halfOpened && fold.horizontal -> DisplayLayout.TABLETOP
    !fold.horizontal && (fold.halfOpened || fold.separating) -> DisplayLayout.BOOK
    widthDp >= 600 || widthDp > heightDp -> DisplayLayout.WIDE
    else -> DisplayLayout.COMPACT
}

data class PaneBounds(val x: Int, val y: Int, val width: Int, val height: Int)
data class PaneRegions(val clock: PaneBounds, val info: PaneBounds)

/** 큰 화면에서 시스템이 회전 요청을 무시할 때 가로 캔버스의 힌지 좌표로 변환한다. */
fun rotateFoldForLandscape(fold: FoldInfo, width: Int, height: Int, clockwise: Boolean): FoldInfo {
    if (fold.left == 0 && fold.top == 0 && fold.right == 0 && fold.bottom == 0) return fold
    return if (clockwise) fold.copy(horizontal = !fold.horizontal,
        left = fold.top, top = width - fold.right, right = fold.bottom, bottom = width - fold.left)
    else fold.copy(horizontal = !fold.horizontal,
        left = height - fold.bottom, top = fold.left, right = height - fold.top, bottom = fold.right)
}

/** 힌지의 실제 경계를 피한다. 경계가 일시적으로 없으면 창의 중앙을 기준으로 분할한다. */
fun splitRegions(width: Int, height: Int, mode: DisplayLayout, fold: FoldInfo, gap: Int): PaneRegions {
    require(width > 0 && height > 0)
    val verticalStack = mode == DisplayLayout.TABLETOP || mode == DisplayLayout.COMPACT
    val extent = if (verticalStack) height else width
    val requestedStart = if (verticalStack) fold.top else fold.left
    val requestedEnd = if (verticalStack) fold.bottom else fold.right
    val hasHinge = (mode == DisplayLayout.TABLETOP || mode == DisplayLayout.BOOK) && requestedStart in 1 until extent && requestedEnd in requestedStart until extent
    val center = if (mode == DisplayLayout.COMPACT) extent * 3 / 5 else extent / 2
    val before = (if (hasHinge) requestedStart - gap / 2 else center - gap / 2).coerceIn(0, extent)
    val after = (if (hasHinge) requestedEnd + gap / 2 else center + gap / 2).coerceIn(before, extent)
    return if (verticalStack) PaneRegions(PaneBounds(0, 0, width, before), PaneBounds(0, after, width, height - after))
    else PaneRegions(PaneBounds(0, 0, before, height), PaneBounds(after, 0, width - after, height))
}
