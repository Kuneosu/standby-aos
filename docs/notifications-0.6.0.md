# 알림 패널과 세로 프리셋 전환 — 0.6.0

2026-10-26 / versionCode 8.

## 동작

| 입력 | 동작 |
| --- | --- |
| 패널이 닫힌 배경에서 위·아래 스와이프 | 등록된 순환 목록의 다음 프리셋 |
| 배경에서 오른쪽 → 왼쪽 드래그 | 손가락을 따라 알림 패널 열기 |
| 패널 안의 위·아래 스와이프 | 알림 목록 스크롤, 프리셋 유지 |
| 패널을 오른쪽으로 밀기 | 패널 닫기 |
| 패널 바깥·닫기 버튼·뒤로 가기 | 패널 닫기 |
| 밝기 슬라이더 조작 | 밝기 변경, 프리셋·패널 유지 |

패널은 오른쪽 절반을 기준으로 오른쪽·위·아래에 12dp 여백을 둔다. 모서리 24dp, 배경 블러 20dp, 균일한 무채색 반투명 표면, 얇은 테두리와 그림자를 적용한다. 패널 안의 실제 배경·요소만 흐리게 비추고 글자는 또렷하게 그린다. 열기·닫기는 250ms 애니메이션이며 드래그 중에는 이동량을 바로 반영한다.

프리셋 저장·공통 배치·원래 디자인·미디어 저장 형식은 바꾸지 않는다. 위·아래 모두 기존 다음 순환 동작을 사용하며, 이전 프리셋으로 이동하는 동작을 추가한 것은 아니다.

## 알림 연결과 내용

`NotificationListenerService`로 Android가 전달한 활성 알림을 읽는다. 앱의 **알림 접근 허용** 버튼은 해당 서비스의 시스템 설정을 열며, 접근 허용은 사용자가 직접 수행한다. 자동 감시 알림을 표시하는 `POST_NOTIFICATIONS` 권한과 별개다.

- 도착·삭제·순위 변경 때 목록을 갱신한다. 연결 해제·권한 회수 시 메모리 목록을 비운다.
- 최신순 최대 100개, 제목 160자·본문 600자 제한을 적용한다. 목록 본문은 최대 4줄로 표시한다.
- 공개 가능한 자식이 있는 그룹의 요약은 중복 표시하지 않는다. 이 앱의 자동 감시 알림을 제외한다.
- 알림 자체와 시스템 순위 정보의 잠금 화면 공개 범위 중 더 엄격한 값을 적용한다. 공개 알림은 공개 내용, 비공개 알림은 `publicVersion` 또는 가림 안내를 사용한다. 숨김 알림은 제외한다.
- 비공개 원문은 표시 모델로 가져오지 않는다. 내용·알림 키를 파일이나 진단 로그에 기록하지 않는다. 연결·해제 이벤트만 진단에 기록한다.
- 읽음·삭제·답장·앱 실행 동작은 포함하지 않는다.

공식 근거: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [잠금 화면 공개 범위](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking#getLockscreenVisibilityOverride()), [공개 버전](https://developer.android.com/reference/android/app/Notification#publicVersion), [서비스별 접근 설정](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).

## 검증

빌드·단위 검사·Lint를 실행했다. 상세 집계는 검사 결과 (`output/notifications-0.6.0/verification.json`, 로컬 전용), 실행 원본은 빌드 로그 (`output/notifications-0.6.0/build-tests.txt`, 로컬 전용)에 보관한다.

새 단위 검사는 스와이프 방향·모호한 대각선·순환 비활성화, 비공개 내용 가림·공개 버전·숨김, 그룹 중복 제거, 최신순·개수·문자 수 제한을 다룬다.

Galaxy Z Fold6 `SM-F956N` / Android 16에서 별도 검사 패키지로 **31개 전체 시나리오를 통과**했다. 검사 알림을 주입한 UI 흐름은 열기 중간 위치·오른쪽 절반 배치·목록 스크롤·닫기·밝기와 전환 간섭·접근 상태별 표시·내용 변경을 검증했다. 사진 픽셀 비교로 패널 영역의 실제 블러를 확인했고, Android 알림 객체의 공개 버전·시스템 공개 범위 추출도 검사했다. 기존 편집·저장·프리셋·공통 배치 시나리오를 함께 통과했다.

| 항목 | 결과 |
| --- | --- |
| Debug·Validation·계측 APK 빌드 | 성공 |
| 단위 검사 | 53개 통과, 실패·오류·생략 0 |
| 실제 기기 계측 검사 | 전체 31개 통과, 44.839초 |
| Debug·Validation Lint | 각각 오류 0개, 기존 경고 14개 |
| 사용 앱 알림 접근 | 사용자가 시스템 설정에서 직접 허용 |
| 실제 시스템 리스너 연결 | 서비스 바인딩과 앱 연결 이벤트 확인 |
| 실제 개인 알림 표시·스크롤 | 사용자가 “알림 표시와 스크롤이 잘돼요”라고 확인 |

실기기 실행 원본 (`output/notifications-0.6.0/instrumentation-first.txt`, 로컬 전용), 패널 검사 화면 (`output/notifications-0.6.0/previews/notification-panel.png`, 로컬 전용), 실제 블러 검사 화면 (`output/notifications-0.6.0/previews/notification-panel-blur.png`, 로컬 전용), 시스템 연결 증거 (`output/notifications-0.6.0/notification-access.json`, 로컬 전용).

충전·잠금·가로 자세의 실제 스탠바이에서 패널을 열어 확인하도록 안내했고, 사용자가 실제 알림 표시와 스크롤의 정상 동작을 확인했다. 주입한 알림을 사용하는 자동 UI 검사와 이 사용자 확인은 별도 증거로 기록한다. 개인 알림 내용이나 화면은 수집하지 않았다. 장시간 전력·발열과 다른 제조사 기기는 이번 범위에 포함하지 않는다.

## 설치와 보존

사용 앱 `dev.kwonsu.standby`를 삭제하지 않고 `0.5.0`에서 `0.6.0`으로 업데이트했다. 기존 설정·미디어 6개 파일의 SHA-256이 검사 전·설치 직전·설치 직후 모두 같았다. 검사 앱 두 개를 제거하고 기존에 켜져 있던 자동 스탠바이 감시를 다시 켰으며 `isForeground=true`를 확인했다.

설치·데이터 보존 (`output/notifications-0.6.0/update-preservation.json`, 로컬 전용), 감시 복구 (`output/notifications-0.6.0/monitor-restored.txt`, 로컬 전용).

APK SHA-256: `776f4e3bd782e4aef375d9db6652183b71af833e4bc6dc1479cd54bdef452da5`.

## 산출물

- 0.6.0 APK (`output/fold-standby-0.6.0-debug.apk`, 로컬 전용)
- 주요 소스 변경 (`output/notifications-0.6.0/source.diff`, 로컬 전용)
- 단위 검사·Lint 보고서 (`output/reports-0.6.0/`, 로컬 전용)
