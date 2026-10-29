# 알림 일괄 삭제와 화면 개선 — 0.8.1

2026-10-26 / versionCode 12.

## 변경

- 앱 이름 옆 **지우기**로 해당 앱의 기록을 한 번에 제거한다. 접힌 상태와 펼친 상태에서 모두 사용할 수 있다.
- 상단 **모두 지우기**로 현재 표시된 알림을 한 번에 제거한다. 필터로 숨겨진 기록은 유지한다.
- 버튼을 누른 순간의 식별자 묶음을 하나의 이벤트로 처리하고 한 번 저장한다. 처리 중 새로 도착한 알림은 제거 대상에 포함하지 않는다.
- 삭제 후에도 중복 방지 식별자는 유지해 현재 활성 알림을 다시 받아도 되살아나지 않는다. 시스템 알림 취소·읽음 처리는 실행하지 않는다.
- 앱 아이콘·대체 문자 아이콘·원형 개수 배지를 제거했다. 앱 이름과 개수를 중심으로 표시하며, 닫기·펼침은 일정한 두께의 선으로 그린다.
- 패널은 손가락을 따라 움직인 뒤 완만하게 멈춘다. 묶음 미리보기 높이·화살표 회전·새 항목 등장·제거 후 목록 재배치를 애니메이션으로 연결한다. 짧게 밀었다 놓은 항목은 튀지 않고 제자리로 돌아온다.
- 마지막 알림을 제거하면 빈 목록 안내가 부드럽게 나타나며 패널은 열린 채 유지된다.

목록은 안정적인 항목 키와 Compose의 `animateItem`을 사용해 보이는 항목을 중심으로 처리한다. [Android 목록 애니메이션 공식 문서](https://developer.android.com/develop/ui/compose/lists#item-animations).

## 검증

Debug·Validation·계측 APK 빌드 통과. 단위 검사 76개 통과. Lint는 Debug·Validation 각각 오류 0개, 기존 경고 14개다.

Galaxy Z Fold6의 별도 검사 패키지에서 알림 관련 계측 검사 18개가 모두 통과했다(19.735초). 앱별 삭제·전체 삭제·다른 앱 보존·삭제 후 신규 수신·암호화 파일 재오픈 후 일괄 삭제 유지·펼침과 접힘의 중간 프레임을 확인했다. 기존 단일 스와이프 제거·세로 스크롤·패널 닫기·프리셋 구분·유리 블러·공개 규칙·선택 앱 필터 검사도 포함했다.

사용 앱에 0.8.1 / versionCode 12를 설치했다. 설정·사진·GIF·암호화된 알림 기록 7개 파일의 검사 전·설치 전·설치 후 SHA-256이 모두 같았다. 자동 감시의 foreground 동작과 알림 수신 연결을 복구했으며, 검사 앱과 계측 앱은 제거했다.

검사는 예시 알림으로 수행했다. 실제 개인 알림을 지우거나 내용을 수집하지 않았다. 예시 화면의 배치와 실제 기기에서의 애니메이션 중간 상태는 확인했으며, 애니메이션의 체감에 대한 사용자 후기는 아직 받지 않았다.

## 산출물

- 검사 집계 (`output/notification-polish-0.8.1/verification.json`, 로컬 전용)
- 빌드·검사 로그 (`output/notification-polish-0.8.1/build-validation.txt`, 로컬 전용)
- 실기기 검사 로그 (`output/notification-polish-0.8.1/instrumentation.txt`, 로컬 전용)
- 기존 데이터 보존·자동 감시 복구 (`output/notification-polish-0.8.1/update-preservation.json`, 로컬 전용)
- 접힌 예시 화면 (`output/notification-polish-0.8.1/previews/clean-notification-panel.png`, 로컬 전용)
- 펼친 예시 화면 (`output/notification-polish-0.8.1/previews/expanded-notifications.png`, 로컬 전용)
- 소스 변경 (`output/notification-polish-0.8.1/source.diff`, 로컬 전용)
- 단위 검사·Lint 보고서 (`output/reports-0.8.1/`, 로컬 전용)
- 0.8.1 APK (`output/fold-standby-0.8.1-debug.apk`, 로컬 전용)
