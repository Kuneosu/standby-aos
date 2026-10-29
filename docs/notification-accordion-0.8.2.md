# 알림을 아래로 펼치는 아코디언 — 0.8.2

2026-10-26 / versionCode 13.

## 변경 이유와 동작

0.8.1은 접힌 최신 미리보기를 줄이면서 별도의 행을 페이드인했다. 이 때문에 사용자는 같은 묶음이 펼쳐지기보다 새 목록이 나타나는 것으로 느꼈다.

0.8.2는 최신 알림을 같은 위치에 유지하고, 그 아래의 이전 기록만 위쪽에 고정한 채 표시 경계를 늘린다. 닫을 때는 같은 경계가 위로 줄어든다. 기록 자체의 투명도를 바꾸거나 최신 알림을 다른 행으로 교체하지 않는다. 다음 앱 묶음은 실제로 열린 높이만큼 밀리며, 별도의 위치 보간을 중첩하지 않는다.

알림이 많을 때 전체 기록 높이를 한 번에 애니메이션하면 첫 프레임에 화면이 가득 찰 수 있다. 이를 막기 위해 전환 중에는 보이는 한 화면 높이까지만 연다. 전환이 끝나면 같은 위치의 전체 기록을 스크롤할 수 있다. 펼침 360ms·접힘 320ms이며 시스템 애니메이션 배율을 따른다. 화살표도 같은 시간과 곡선으로 회전한다.

앱 이름 또는 최신 알림을 눌러 접고 펼친다. 개별 스와이프 제거·앱별 삭제·전체 삭제는 유지한다. 하나의 알림만 있는 묶음은 추가 기록 영역이 없다.

Compose의 레이아웃 높이와 클리핑을 이용한다. [공식 애니메이션 설명](https://developer.android.com/develop/ui/compose/animation/composables-modifiers).

## 검증

| 구분 | 결과 |
| --- | --- |
| Debug·Validation·계측 APK 빌드 | 통과 |
| 단위 검사 | 76개 통과 |
| Debug·Validation Lint | 각각 오류 0개, 기존 경고 14개 |
| Fold6 알림 패널 계측 검사 | 고유 7개 항목 최종 통과 |
| 설치 | 0.8.2 / versionCode 13 확인 |
| 자동 감시·알림 수신 | 켜짐·연결 복구 확인 |
| 검사 앱·계측 앱 | 제거 완료 |

중간 프레임에서 최신 알림의 위치 유지, 이전 기록 영역의 높이 증가·감소, 다음 앱의 이동, 전환 도중 다시 누를 때의 연속성을 확인했다. 500개를 펼칠 때 첫 중간 프레임이 한 화면보다 작고, 가장 오래된 기록까지 스크롤한 뒤 다시 접을 수 있는 것도 확인했다. 기존 스와이프·일괄 삭제·빈 상태·권한 안내·세로 스크롤·블러 검사도 통과했다.

최초 계측 실행은 7개 중 6개 통과, 1개 실패였다. 실패 항목은 방향 전환 요청 직후 첫 프레임부터 이동 방향이 바뀐다고 가정했다. 이를 중간 높이의 연속성과 최종 도착 높이를 확인하도록 보정한 뒤 해당 1개만 다시 실행해 통과했다. 이 보정에서 제품 코드는 변경하지 않았다. 첫 실행 16.068초, 해당 항목 재실행 3.627초다.

기존 설정·사진·GIF 6개 파일은 검사 전·설치 전·설치 후 해시가 동일했다. 암호화된 알림 기록은 각 시점의 원본을 별도로 백업했다. 검사 중과 설치 직후에도 알림 수신 서비스가 재연결되어 파일 크기·수정 시각·해시가 바뀌었으므로, 이 기록에 바이트 동일성을 주장하지 않는다. 원문 내용은 열거나 수집하지 않았다.

화면 검사는 예시 알림을 이용했다. 0.8.2의 움직임에 대한 사용자 체감 확인은 대기 중이다.

## 산출물

- 검사 집계 (`output/notification-accordion-0.8.2/verification.json`, 로컬 전용)
- 최종 빌드·검사 로그 (`output/notification-accordion-0.8.2/build-verified.txt`, 로컬 전용)
- 최초 실기기 검사 (`output/notification-accordion-0.8.2/instrumentation.txt`, 로컬 전용)
- 전환 검사 재실행 (`output/notification-accordion-0.8.2/instrumentation-motion-rerun.txt`, 로컬 전용)
- 데이터 보존·감시 복구 (`output/notification-accordion-0.8.2/update-preservation.json`, 로컬 전용)
- 소스 변경 (`output/notification-accordion-0.8.2/source.diff`, 로컬 전용)
- 0.8.2 APK (`output/fold-standby-0.8.2-debug.apk`, 로컬 전용)

접힘 → 펼치는 중 → 펼침 → 접는 중의 실제 검사 프레임:

접힌 상태 (`output/notification-accordion-0.8.2/previews/00-collapsed.png`, 로컬 전용)
펼치는 중 (`output/notification-accordion-0.8.2/previews/01-opening.png`, 로컬 전용)
펼친 상태 (`output/notification-accordion-0.8.2/previews/02-expanded.png`, 로컬 전용)
접는 중 (`output/notification-accordion-0.8.2/previews/03-closing.png`, 로컬 전용)
