# Fold6 0.2.0 조건 검증

2026-10-23, Galaxy Z Fold6 `SM-F956N` / Android 16 / One UI `80500`에서 확인했다. **잠금 + 충전 연결 + 물리 가로 자세**가 모두 성립할 때만 시계를 표시하는 버전이다. 기존 `0.1.0`의 충전 이벤트만으로 여는 동작을 교체했다. 커버 화면에서 세 조건에 따른 진입과 각각의 조건 해제에 따른 종료를 사용자 확인·실제 앱 기록으로 검증했다.

## 구현

| 항목 | 동작 |
|---|---|
| 진입 | 조건이 모두 참인 상태가 800ms 유지되면 한 번 실행 |
| 조건 순서 | 충전 연결, 잠금, 자세 변화 중 어느 것이 마지막이어도 재평가 |
| 종료 | 잠금 해제, 전원 해제, 세로·판정 불가 자세 중 하나라도 감지되면 닫음 |
| 중복 방지 | 조건 유지 중 반복 실행하지 않음. 사용자가 닫으면 조건 해제 전까지 재진입 억제 |
| 잠금 확인 | Keyguard 표시 및 보안 잠금 인증 상태를 함께 확인. 이벤트와 250ms 확인 병행 |
| 물리 자세 | `OrientationEventListener`로 양방향 가로 감지. 화면 꺼짐·잠금 화면의 세로 고정과 구별 |
| 가로 표시 | 가로 방향 요청과 별도의 가로 캔버스 적용. 힌지 좌표도 변환 |
| 이전 진입점 | 수동 시계 버튼 제거, 알림은 설정으로 이동, 별도 `DreamService` component 비활성화 |

화면이 꺼진 상태에서 자세를 바꿔도 감지하도록, 충전·잠금 중에만 CPU 유지 잠금을 사용한다. 잠금 해제·충전 해제·서비스 종료 시 해제하며 자세 센서는 충전 중에만 켠다. 평평하게 눕혀 각도를 판정할 수 없는 경우에는 시계를 표시하지 않는다.

## 자동 검사

- `assembleDebug`: 성공.
- `testDebugUnitTest`: **19개 통과**, 실패·오류·건너뜀 0.
- `lintDebug`: 오류 0, 경고 10. 라이브러리 업데이트 7, AGP 업데이트 1, target API 업데이트 1, 명시적 데이터 이전 규칙 권고 1.
- 조건 테스트: 잠금·충전·자세의 전체 조합, 마지막 조건의 종류, 800ms 대기 취소, 중복 실행 방지, 각 조건 해제 시 즉시 종료, 재잠금 재진입, 수동 닫기, 각도 경계, 힌지 좌표 변환.
- 조건 검사 결과 (`output/reports-0.2.0/TEST-dev.kwonsu.standby.StandbyGateTest.xml`, 로컬 전용), 배치 검사 결과 (`output/reports-0.2.0/TEST-dev.kwonsu.standby.PoliciesTest.xml`, 로컬 전용), Lint XML (`output/reports-0.2.0/lint-results-debug.xml`, 로컬 전용).

## 실기기 결과

아래 시간은 한국 시간이며 JSONL의 시간은 UTC다. 실제 케이블·자세·잠금 조작을 사용했고 센서나 배터리 상태를 주입하지 않았다.

| 시나리오 | 판정 | 근거 |
|---|---|---|
| 설치·감시 시작 | Pass | `0.2.0` 설치 성공, `monitor_started`, 실제 foreground service 실행 확인 |
| 충전 + 잠금 해제 | Pass | 15:58:59 `잠금=false; 전원연결=true`, 시계 미실행 |
| 잠금 + 충전 + 세로 | Pass | 16:01:50 `잠금=true; 전원연결=true; 자세=PORTRAIT`, 5초 동안 미실행, 사용자 확인 |
| 화면이 꺼진 채 가로로 전환 | Pass | 16:01:55.741 가로 감지 → 16:01:56.636 진입 시도. `화면켜짐=false`에서 시작 후 화면 켜짐·활성화·포커스 확인 |
| 가로 시계 표시 | Pass | 실제 창이 처음 세로일 때도 `2376×968px` 가로 캔버스, 이어 실제 창도 가로로 재생성. 사용자 화면 확인 |
| 양방향 가로 | Pass | `REVERSE_LANDSCAPE`와 `LANDSCAPE` 각각 실제 진입 확인 |
| 세로 전환 자동 종료 | Pass | 16:01:59.951 및 후속 전환에서 `standby_conditions_exit`, Activity 종료, 조건 유지 중 재진입 없음 |
| 가로·충전 유지 중 잠금 해제 | Pass | 16:04:52.887 `잠금=false; 전원연결=true; 자세=REVERSE_LANDSCAPE`로 종료. 사용자 확인. 후속 LANDSCAPE 방향에서도 반복 확인 |
| 충전·가로 유지 중 다시 잠금 | Pass | 16:05:05.956 잠금만 true로 전환 → 16:05:06.840 진입. 충전 재연결 없이 실행 |
| 0.2.0 충전 해제·재연결 | Pass | 16:05:37.919 전원 해제·종료 → 16:05:43.728 재연결 → 16:05:44.806 진입. 사용자 확인 |

가로·충전 상태를 유지한 채 잠금 해제한 경우와, 잠금·가로를 유지한 채 충전을 해제한 경우를 별도로 확인했다. 가로 전환 시험에는 화면이 꺼진 상태에서의 전환도 포함된다. 조건 변경·실제 Activity 활성화·가로 캔버스 기록을 분석한 관찰 요약 JSON (`output/device-conditions/evidence-summary.json`, 로컬 전용)을 함께 남겼다.

## 결과 파일

- APK (`output/fold-standby-0.2.0-debug.apk`, 로컬 전용)
- SHA-256: `e1742b7883566f3df02b4864fcc8e0f25eed924eed6f12cb472f80c51da6d229`
- 기기 정보 (`output/device-conditions/device.json`, 로컬 전용)
- 앱 진단 원본 (`output/device-conditions/diagnostics.jsonl`, 로컬 전용). 기존 버전의 기록도 포함되므로 `process_started`의 `앱 0.2.0` 이후를 기준으로 한다.

현재 `0.2.0`이 기기에 설치되어 있고 자동 스탠바이 감시는 켜져 있다. 기존 자동 실행·알림 권한은 유지하며 시스템 화면보호기는 사용하지 않는다.

## 미검증 범위와 참고

큰 내부 화면·가로 탁상 자세에서 새 조건 흐름 재검증, 다른 기기, 무선 충전·완충·배터리 보호, 장시간 발열·소모 전력은 아직 확인하지 않았다. `0.1.0` 검증 결과를 현재 버전의 통과 근거로 대체하지 않는다.

- [Android 센서 사용 조건](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview): foreground service에서 센서를 사용하고 필요 없을 때 해제하는 기준.
- [OrientationEventListener](https://developer.android.com/reference/android/view/OrientationEventListener): 기기 기본 자세를 기준으로 한 각도와 평평한 자세의 판정 불가 상태.
- [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager): 잠금 화면 표시 여부와 인증이 필요한 기기 잠금 상태의 차이.
