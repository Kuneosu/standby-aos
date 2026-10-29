# 접힌 알림의 내용 숨김 — 0.8.3

2026-10-26 / versionCode 14.

접힌 앱 묶음에서는 앱 이름·알림 개수·지우기·펼침 버튼만 표시한다. 제목·본문·시간은 모두 숨긴다. 앱 이름을 누르면 최신 알림부터 전체 기록이 아래로 펼쳐지고, 다시 누르면 모든 내용이 위로 접힌다. 알림이 한 개뿐이어도 같은 방식이다.

0.8.2의 아코디언 경계 애니메이션을 유지하며 최신 알림도 같은 펼침 영역에 넣었다. 앱별·전체 삭제는 접힌 상태에서도 사용할 수 있고, 펼친 알림의 개별 스와이프 제거는 유지한다.

## 검증 및 설치

- Debug·Validation·계측 APK 빌드 통과.
- 단위 검사 76개 통과. Debug·Validation Lint 각각 오류 0개, 기존 경고 14개.
- Galaxy Z Fold6 알림 패널 검사 7개 모두 통과(17.217초). 접힌 제목·본문 숨김, 한 개 알림의 펼침, 다시 접은 뒤 전체 내용 제거, 중간 프레임·방향 전환, 500개 스크롤, 개별·일괄 삭제, 권한·빈 상태, 블러를 확인했다.
- 사용 앱 0.8.3 / versionCode 14 설치 확인. 자동 감시·알림 수신 연결 복구. 검사 앱·계측 앱 정리 완료.
- 설정·사진·GIF 6개 파일의 검사 전·설치 전·설치 후 해시 동일. 수신 중 갱신되는 암호화 알림 기록은 세 시점 원본을 별도 백업했으며 바이트 동일성을 주장하지 않는다. 개인 알림 내용은 열거나 수집하지 않았다.

## 산출물

- 검사 집계 (`output/notification-collapsed-0.8.3/verification.json`, 로컬 전용)
- 빌드 로그 (`output/notification-collapsed-0.8.3/build-validation.txt`, 로컬 전용)
- 실기기 검사 로그 (`output/notification-collapsed-0.8.3/instrumentation.txt`, 로컬 전용)
- 데이터 보존·감시 복구 (`output/notification-collapsed-0.8.3/update-preservation.json`, 로컬 전용)
- 소스 변경 (`output/notification-collapsed-0.8.3/source.diff`, 로컬 전용)
- 접힌 예시 화면 (`output/notification-collapsed-0.8.3/previews/00-collapsed.png`, 로컬 전용)
- 펼친 예시 화면 (`output/notification-collapsed-0.8.3/previews/02-expanded.png`, 로컬 전용)
- 0.8.3 APK (`output/fold-standby-0.8.3-debug.apk`, 로컬 전용)
