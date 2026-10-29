package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class StandbyGateTest {
    private val ready = StandbyConditions(true, true, DevicePosture.LANDSCAPE)

    @Test fun `세 조건의 모든 조합 중 모두 참일 때만 허용한다`() {
        listOf(false, true).forEach { locked ->
            listOf(false, true).forEach { charging ->
                DevicePosture.entries.forEach { posture ->
                    val conditions = StandbyConditions(locked, charging, posture)
                    assertEquals(conditions.description, locked && charging && posture.isLandscape, conditions.canShow)
                }
            }
        }
    }

    @Test fun `이미 충전 중이어도 모든 조건이 안정되면 진입한다`() {
        val gate = StandbyGate()
        assertEquals(GateAction.NONE, gate.update(ready, 0))
        assertEquals(GateAction.NONE, gate.update(ready, 799))
        assertEquals(GateAction.ENTER, gate.update(ready, 800))
    }

    @Test fun `잠금 충전 가로 중 어떤 조건이 마지막으로 충족되어도 진입한다`() {
        listOf(ready.copy(locked = false), ready.copy(charging = false), ready.copy(posture = DevicePosture.PORTRAIT)).forEach { waiting ->
            val gate = StandbyGate()
            assertEquals(GateAction.NONE, gate.update(waiting, 0))
            assertEquals(GateAction.NONE, gate.update(waiting, 1000))
            assertEquals(GateAction.NONE, gate.update(ready, 1200))
            assertEquals(GateAction.ENTER, gate.update(ready, 2000))
        }
    }

    @Test fun `조건이 유지되면 시계를 반복 실행하지 않는다`() {
        val gate = StandbyGate(0)
        assertEquals(GateAction.ENTER, gate.update(ready, 0))
        repeat(20) { assertEquals(GateAction.NONE, gate.update(ready, it * 1000L)) }
    }

    @Test fun `잠금 해제 충전 해제 세로 전환은 대기 없이 종료한다`() {
        listOf(ready.copy(locked = false), ready.copy(charging = false), ready.copy(posture = DevicePosture.PORTRAIT), ready.copy(posture = DevicePosture.UNKNOWN)).forEach { stopped ->
            val gate = StandbyGate(0)
            gate.update(ready, 0)
            assertEquals(GateAction.EXIT, gate.update(stopped, 1))
            assertEquals(GateAction.NONE, gate.update(stopped, 2))
        }
    }

    @Test fun `짧게 가로를 지나가거나 잠금이 풀리면 진입 대기를 취소한다`() {
        val gate = StandbyGate()
        gate.update(ready, 0)
        assertEquals(GateAction.NONE, gate.update(ready.copy(locked = false), 700))
        assertEquals(GateAction.NONE, gate.update(ready, 900))
        assertEquals(GateAction.NONE, gate.update(ready, 1600))
        assertEquals(GateAction.ENTER, gate.update(ready, 1700))
    }

    @Test fun `잠금을 다시 걸면 충전기를 다시 꽂지 않아도 재진입한다`() {
        val gate = StandbyGate(0)
        gate.update(ready, 0)
        gate.update(ready.copy(locked = false), 1)
        assertEquals(GateAction.ENTER, gate.update(ready, 2))
    }

    @Test fun `사용자가 닫으면 조건 해제 전까지 다시 나타나지 않는다`() {
        val gate = StandbyGate(0)
        gate.update(ready, 0)
        gate.dismiss()
        assertEquals(GateAction.NONE, gate.update(ready, 9000))
        gate.update(ready.copy(posture = DevicePosture.PORTRAIT), 10000)
        assertEquals(GateAction.ENTER, gate.update(ready, 10001))
    }

    @Test fun `양쪽 가로 각도만 허용하고 세로와 판정 불가는 거부한다`() {
        assertEquals(DevicePosture.REVERSE_LANDSCAPE, classifyPosture(90, DevicePosture.UNKNOWN))
        assertEquals(DevicePosture.LANDSCAPE, classifyPosture(270, DevicePosture.UNKNOWN))
        listOf(0, 180, 359).forEach { assertEquals(DevicePosture.PORTRAIT, classifyPosture(it, DevicePosture.UNKNOWN)) }
        assertEquals(DevicePosture.UNKNOWN, classifyPosture(-1, DevicePosture.LANDSCAPE))
    }

    @Test fun `가로 판정 경계의 작은 떨림은 유지하되 세로로 돌아가면 해제한다`() {
        assertEquals(DevicePosture.PORTRAIT, classifyPosture(55, DevicePosture.UNKNOWN))
        assertEquals(DevicePosture.REVERSE_LANDSCAPE, classifyPosture(55, DevicePosture.REVERSE_LANDSCAPE))
        assertEquals(DevicePosture.PORTRAIT, classifyPosture(44, DevicePosture.REVERSE_LANDSCAPE))
    }

    @Test fun `회전 요청이 무시된 창의 힌지를 가로 캔버스에 맞춘다`() {
        val original = FoldInfo(halfOpened = true, separating = true, left = 300, right = 320, bottom = 1000)
        val clockwise = rotateFoldForLandscape(original, 800, 1000, true)
        assertTrue(clockwise.horizontal)
        assertEquals(480, clockwise.top)
        assertEquals(500, clockwise.bottom)
        assertEquals(1000, clockwise.right)
        val reverse = rotateFoldForLandscape(original, 800, 1000, false)
        assertEquals(300, reverse.top)
        assertEquals(320, reverse.bottom)
    }
}
