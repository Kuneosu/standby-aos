# 출퇴근 게이지 슬림 디자인 — 0.9.1

2026-10-27 / versionCode 16.

게이지가 두껍고 장식이 과하다는 피드백을 반영해 가로·세로 모두 얇은 둥근 라인으로 바꿨다.

- 실제 막대는 **최대 6dp**이며, 저장된 요소 크기가 커도 막대가 두꺼워지지 않는다. 기존 위치·크기·방향 설정은 그대로 사용한다.
- 금속 외곽선, 20칸 분할, 반사 하이라이트를 없앴다. 어두운 트랙과 차분한 민트색으로 진행률을 구분한다.
- 퍼센트를 막대 밖으로 옮겼다. 가로는 퇴근 시각과 퍼센트를 위에, 세로는 퍼센트를 위·퇴근 시각을 아래에 배치한다.
- 출근·퇴근 버튼은 높이 최대 34dp의 캡슐 형태로 표시하고, 일반 크기에서는 44dp 높이의 터치 영역을 유지한다. 누를 때 살짝 축소했다가 복원한다.
- 선택한 출근 시각·17시 최소 종료·9시간 계산·폭죽·퇴근 후 12시간 대기·저장 복원 로직은 변경하지 않았다.

## 검증

- Debug·Validation·계측 APK 빌드 통과.
- 기존 단위 검사 **87개 통과**. Debug·Validation Lint 각각 오류 0개, 기존 경고 14개.
- Galaxy Z Fold6(Android 16)의 별도 검사 앱에서 관련 **5개 실기기 검사 통과**(9.308초).
- 가로·세로 막대의 실제 두께가 6dp 이하인지 확인하고, 가로 왼쪽·세로 아래부터 채워지는 실제 픽셀을 검사했다.
- 출근 다이얼·취소·과거 시각 선택·회전 화면 터치, 100% 폭죽·퇴근 버튼, 퇴근 후 숨김과 12시간 경계, 편집기 방향·고정·저장을 확인했다.
- 진행 중·완료·출근 버튼의 실제 캡처를 확인했다. 시간 경계는 검사 시각을 주입했으며 실제 장시간 대기를 다시 수행하지 않았다.

최초 빌드는 이 프로젝트 버전에 없는 테스트 크기 단언 API로 실패했다. 실제 노드 경계와 기기 밀도를 비교하도록 검사 코드를 수정한 뒤 빌드·검사가 모두 통과했다.

## 설치와 보존

**2026-10-27 사용 앱 0.9.1 / versionCode 16 설치 완료.** 설치 후 자동 감시를 다시 켜고 foreground 서비스 실행과 알림 수신 연결을 확인했다.

기존 설정·근무 기록·사진·GIF **7개 파일**의 검사 전후 기기 내부 SHA-256이 동일하다. 사용자 파일 원문을 읽거나 복사하지 않았다. 검사 앱·계측 앱 2개는 제거했다. 설치 직전·직후와 자동 감시 복구 후에도 설정·근무 기록·미디어 7개 파일의 해시가 동일함을 확인했다.

## 산출물

- 가로 게이지 상세 (`output/work-shift-design-0.9.1/previews/gauge-horizontal-detail.png`, 로컬 전용)
- 세로 게이지 (`output/work-shift-design-0.9.1/previews/working-vertical.png`, 로컬 전용)
- 완료·폭죽·퇴근 버튼 (`output/work-shift-design-0.9.1/previews/complete-fireworks.png`, 로컬 전용)
- 출근 버튼 (`output/work-shift-design-0.9.1/previews/ready.png`, 로컬 전용)
- 0.9.1 APK (`output/fold-standby-0.9.1-debug.apk`, 로컬 전용)
- 최종 빌드·검사 (`output/work-shift-design-0.9.1/build-final.txt`, 로컬 전용)
- 기기 검사 (`output/work-shift-design-0.9.1/instrumentation.txt`, 로컬 전용)
- 검증 집계 (`output/work-shift-design-0.9.1/verification.json`, 로컬 전용)
- 데이터 보존·설치 상태 (`output/work-shift-design-0.9.1/preservation.json`, 로컬 전용)
- 소스 변경 (`output/work-shift-design-0.9.1/source.diff`, 로컬 전용)
