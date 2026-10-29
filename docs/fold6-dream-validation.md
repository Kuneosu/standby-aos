# Fold6 시스템 화면보호기 검증

이 문서는 `0.1.0`의 당시 검증 기록이다. `0.2.0`에서는 모든 시계 진입에 잠금·충전·가로 조건을 적용하기 위해 별도 시스템 화면보호기 component를 비활성화했다. 현재 사용 방법은 [README](../README.md)를 기준으로 한다.

2026-10-23, Galaxy Z Fold6 `SM-F956N` / Android 16 / One UI `80500`에서 기존 `0.1.0` debug APK의 시스템 `DreamService` 경로를 시험했다. 아래 6개 항목을 모두 통과했고 변경한 설정을 복원했다. 앱 코드·APK는 변경하지 않아 빌드·단위 테스트를 다시 실행하지 않았다. 이 문서는 [시계 Activity 검증](fold6-validation.md)과 별도의 결과다.

## 시험 조건

- 앱의 충전 감시는 15:36:24에 중지했다. 별도의 foreground service 자동 실행이 시험에 섞이지 않도록 했다.
- 원래 화면보호기는 ‘설정 안 함’, 화면 꺼짐 시간은 10분, 개발자 옵션의 충전 중 화면 유지는 켜져 있었다.
- 시스템 UI에서 **Fold 스탠바이 시계**를 선택했다. 서비스 선택 값은 `dev.kwonsu.standby/dev.kwonsu.standby.StandbyDreamService`다.
- 유휴 시험 동안 화면 꺼짐을 30초로 변경하고 충전 중 화면 유지를 껐다. USB 충전 상태에서 홈 화면으로 이동한 뒤 40초간 터치·키 입력 없이 기다렸다.
- 강제 Dream 시작 명령이나 배터리 상태 주입을 사용하지 않았다. 미리보기는 시스템 설정의 실제 **미리보기** 버튼으로 시작했다.

## 관찰 결과

아래 시간은 한국 시간이며 앱 JSONL에는 UTC가 저장된다.

| 항목 | 결과 | 근거 |
|---|---|---|
| 시스템 목록 등록·선택 | Pass | ‘Fold 스탠바이 시계’ 노출, 선택 라디오 버튼과 저장된 component 확인 |
| 미리보기 표시 | Pass | 15:36:58 `dream_attached`·`dream_started`, `968×2376px`, 실제 붉은 시계 캡처 |
| 탭해서 종료 | Pass | 15:37:20 `dream_stopped`, `mCurrentDream=null` |
| USB 충전 중 유휴 자동 진입 | Pass | 입력 없이 대기한 뒤 `mWakefulness=Dreaming`, `mBound=true`, 실제 서비스 component와 화면 표시 확인 |
| 분 단위 시각 갱신 | Pass | 자동 진입 기록 뒤 15:39 캡처에서 시계 갱신 확인 |
| 실제 USB 해제 시 종료 | Pass | 사용자 종료 확인, 15:41:07 `dream_unplug_exit` → `dream_stopped`, 재연결 후 `mCurrentDream=null` |

유휴 시험 준비 과정에서 15:37:48에 짧은 시작·종료 기록이 있었고, 15:37:53에 다시 시작된 서비스가 대기 후에도 실행 중이었다. 정확히 30초 뒤 시작됐다고 판정하지 않고, 유휴 조건에서 시스템이 서비스를 자동 실행하고 유지한 사실을 확인했다.

One UI의 **미리보기** 버튼으로 시작해도 `dumpsys dreams`에는 `isPreview=false`로 기록됐다. 따라서 그 필드만으로 수동 미리보기와 유휴 자동 진입을 구분하지 않았다. 실제 실행 트리거, 앱 lifecycle 기록, 시스템 `Dreaming` 상태를 함께 사용했다. 또한 `canDoze=false`, `isDozing=false`로 저전력 AOD 실행은 아니다.

## 설정 복원

시험 전 값은 원본 설정 (`output/device-dream/settings-before.json`, 로컬 전용)에 보존했다. 케이블 시험이 끝난 뒤 설정을 복원하고 직접 읽어 대조했다. 복원 결과 (`output/device-dream/settings-after.json`, 로컬 전용)의 `systemSettingsMatchBefore=true`를 확인했다.

| 항목 | 복원 후 값 |
|---|---|
| 시스템 화면보호기 | 설정 안 함 / `screensaver_enabled=0` |
| 선택 서비스 | 원래처럼 미지정 / `screensaver_components=null` |
| 화면 꺼짐 시간 | 10분 / `600000` |
| 충전 중 화면 유지 | 켜짐 / `stay_on_while_plugged_in=7` |
| 유휴·도크 활성화 옵션 | 원래 값 `1` / `0` 유지 |
| 앱 충전 감시 | 켜짐. 15:43:11 `monitor_started`와 ‘충전 감시 끄기’ 버튼 확인 |

현재는 시스템 화면보호기를 계속 켜 둔 상태가 아니다. 이 경로를 다시 사용하려면 시스템 화면보호기를 선택하고 충전 중 화면 유지 옵션을 꺼야 한다. 두 경로를 구분해 시험하려면 앱 충전 감시도 끈다.

## 증거

- 미리보기 캡처 (`output/device-dream/preview.png`, 로컬 전용)
- 유휴 자동 진입 캡처 (`output/device-dream/idle.png`, 로컬 전용)
- 미리보기 시스템 상태 (`output/device-dream/preview-dreams.txt`, 로컬 전용)
- 자동 진입 시스템 상태 (`output/device-dream/idle-dreams.txt`, 로컬 전용)
- 자동 진입 전원 상태 (`output/device-dream/idle-power.txt`, 로컬 전용)
- 충전 해제 후 시스템 상태 (`output/device-dream/after-unplug-dreams.txt`, 로컬 전용)
- 앱 진단 기록 (`output/device-dream/diagnostics.jsonl`, 로컬 전용)
- 기기 정보 (`output/device-dream/device.json`, 로컬 전용)

커버 화면·USB 충전 조건에서 수행했다. 화면보호기 자체의 접기·펼치기 전환, 무선 충전, 장시간 발열·전력 소모, 다른 절전 설정은 이번 범위에서 확인하지 않았다. 이 경로는 힌지 전용 분할을 구현하지 않은 단일 시계 화면이다.
