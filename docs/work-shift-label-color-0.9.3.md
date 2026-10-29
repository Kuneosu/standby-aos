# 출퇴근 게이지 문구 배치·색상 — 0.9.3

2026-10-27 / versionCode 18.

퇴근 예정 시각을 게이지 오른쪽 위에 배치했다. 게이지 프레임과 어울리는 작은 각진 배지에 고정폭 시각과 `퇴근` 문구를 표시한다. 가로·세로 방향 모두 막대보다 위에 놓는다.

화면 꾸미기에서 출퇴근 게이지를 선택하면 **게이지 색상** 팔레트와 6자리 HEX 입력이 나온다. 기본값은 기존 청록색이며 **기본 청록색 사용**을 켜면 복원한다. 선택한 색은 막대·프레임·퍼센트·시각 배지·출근 및 퇴근 버튼에 함께 적용한다. 밝은 칸·빈 칸·배경색도 선택한 색을 기준으로 만든다. 야간 모드는 기존 붉은 계열을 유지한다.

색상은 기존 요소별 색상 필드에 저장하여 기본 디자인·임시 저장·프리셋에 보관한다. 저장 형식과 근무 시간 계산은 변경하지 않았다. 출근·퇴근 버튼은 모서리를 잘라 낸 작은 사각형이며 얇은 테두리와 은은한 배경을 사용한다.

## 검증

- Debug·Validation·계측 APK 빌드 통과.
- 단위 검사 **87개 통과**. Debug·Validation Lint 각각 오류 0개·기존 경고 14개.
- Galaxy Z Fold6(Android 16)의 별도 검사 앱에서 관련 고유 **9개 검사 최종 통과**.
- 첫 실행은 9개 중 8개 통과·1개 실패였다. 새 색상 검사에서 게이지 요소를 먼저 선택하는 단계가 빠져 있었다. 해당 검사 순서를 고친 뒤 1개 통과, 시각 배지의 줄 높이를 조정한 최종 소스로 화면 관련 4개를 다시 실행해 모두 통과했다. 최초 실패 기록도 보관한다.
- 퇴근 시각 배지가 가로·세로 게이지보다 위에 있고 오른쪽에 정렬되는지 화면 좌표로 확인했다. 주황색 게이지는 실제 픽셀 색상으로 확인했다.
- 색상 선택·저장·기본 청록색 복귀와 다른 요소의 색상 동작을 확인했다. 가로 주황색·세로 보라색 프리셋을 저장하고 저장소를 다시 열어 방향·색상 보존을 확인했다.
- 출근 시각 다이얼·취소·회전 화면 터치, 완료 폭죽·퇴근·12시간 숨김, 편집기 방향·고정·저장을 관련 검사로 재확인했다. 긴 시간 경계는 검사 시각을 주입했으며 실제 장시간 대기 결과는 아니다.
- 58% 기본 청록색·선택 색상·세로·완료 게이지와 출근·퇴근 버튼을 실제 기기에서 캡처해 확인했다.

## 설치와 보존

**2026-10-27 사용 앱 0.9.3 / versionCode 18 설치 완료.** 설치 후 자동 감시를 다시 켜고 foreground 서비스 실행과 알림 수신 연결을 확인했다.

별도 검사 전후 기존 설정·근무 기록·사진·GIF **7개 파일**의 기기 내부 SHA-256이 동일하다. 사용 앱 업데이트 직전·직후와 자동 감시 복구 후에도 7개 파일의 해시가 동일하다. 개인 파일 내용을 내보내지 않았다. 검사 앱 2개는 제거했다. 0.9.3 설치 후 잠금·충전·자세 변경에 따른 자동 진입·종료와 장시간 동작은 이번에 다시 검사하지 않았다.

## 화면과 산출물

기본 청록색 58% 게이지 (`output/work-shift-label-color-0.9.3/previews/reference-58-horizontal.png`, 로컬 전용)

| 출근 | 퇴근 |
| --- | --- |
| 출근 버튼 (`output/work-shift-label-color-0.9.3/previews/clock-in-button.png`, 로컬 전용) | 퇴근 버튼 (`output/work-shift-label-color-0.9.3/previews/clock-out-button.png`, 로컬 전용) |

- 선택 색상 예시 (`output/work-shift-label-color-0.9.3/previews/custom-color-gauge.png`, 로컬 전용)
- 완료 게이지와 퇴근 버튼 (`output/work-shift-label-color-0.9.3/previews/complete-gauge.png`, 로컬 전용)
- 색상 편집 화면 (`output/work-shift-label-color-0.9.3/previews/color-editor-preview.png`, 로컬 전용)
- 0.9.3 APK (`output/fold-standby-0.9.3-debug.apk`, 로컬 전용)
- 최종 빌드·단위 검사·Lint (`output/work-shift-label-color-0.9.3/build-final.txt`, 로컬 전용)
- 최초 실기기 검사 (`output/work-shift-label-color-0.9.3/instrumentation.txt`, 로컬 전용)
- 색상 편집 재검사 (`output/work-shift-label-color-0.9.3/instrumentation-editor-final.txt`, 로컬 전용)
- 최종 화면 재검사 (`output/work-shift-label-color-0.9.3/instrumentation-ui-final.txt`, 로컬 전용)
- 검증 집계 (`output/work-shift-label-color-0.9.3/verification.json`, 로컬 전용)
- 설치·보존 상태 (`output/work-shift-label-color-0.9.3/preservation.json`, 로컬 전용)
- 소스 변경 (`output/work-shift-label-color-0.9.3/source.diff`, 로컬 전용)
